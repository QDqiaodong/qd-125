package com.bufferblock.service;

import com.bufferblock.dto.HandoverCreateDTO;
import com.bufferblock.dto.HandoverItemConfirmDTO;
import com.bufferblock.dto.HandoverOverviewVO;
import com.bufferblock.dto.HandoverPreviewVO;
import com.bufferblock.dto.HandoverQueryDTO;
import com.bufferblock.entity.BlockBorrowReservation;
import com.bufferblock.entity.BlockTransfer;
import com.bufferblock.entity.BufferBlock;
import com.bufferblock.entity.ProductionLine;
import com.bufferblock.entity.ShiftHandover;
import com.bufferblock.entity.ShiftHandoverItem;
import com.bufferblock.entity.StocktakeBatch;
import com.bufferblock.entity.StocktakeItem;
import com.bufferblock.repository.BlockBorrowReservationRepository;
import com.bufferblock.repository.BlockTransferRepository;
import com.bufferblock.repository.BufferBlockRepository;
import com.bufferblock.repository.ShiftHandoverItemRepository;
import com.bufferblock.repository.ShiftHandoverRepository;
import com.bufferblock.repository.StocktakeBatchRepository;
import com.bufferblock.repository.StocktakeItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 班组交班业务：
 * <ul>
 *     <li>交班登记时一次性快照当班全部未还预约（占用中：已预约/已取走/逾时未取）、
 *     待确认移交与待处理盘点差异，清单内容落库后保持不变；</li>
 *     <li>同一时刻只允许一个交班中的交班单；接班人须逐条确认，
 *     全部确认后交班自动完成；</li>
 *     <li>未确认条数、清单内容、确认人/确认时间全部落库，
 *     关闭页面再打开与档案占用标记保持一致；</li>
 *     <li>交班未完成期间，借用预约模块通过 {@link #assertNoInProgressHandover()} 禁止新开预约。</li>
 * </ul>
 */
@Service
public class ShiftHandoverService {

    /** 事项类型展示顺序：未还预约 → 待确认移交 → 待处理盘点差异 */
    private static final Map<String, Integer> TYPE_ORDER = Map.of(
            ShiftHandoverItem.TYPE_BORROW_UNRETURNED, 1,
            ShiftHandoverItem.TYPE_TRANSFER_PENDING, 2,
            ShiftHandoverItem.TYPE_STOCKTAKE_PENDING, 3);

    private final ShiftHandoverRepository handoverRepository;
    private final ShiftHandoverItemRepository itemRepository;
    private final HandoverNumberService handoverNumberService;
    private final BlockBorrowReservationRepository reservationRepository;
    private final BlockTransferRepository transferRepository;
    private final StocktakeItemRepository stocktakeItemRepository;
    private final StocktakeBatchRepository stocktakeBatchRepository;
    private final BufferBlockRepository blockRepository;
    private final ProductionLineService productionLineService;

    public ShiftHandoverService(ShiftHandoverRepository handoverRepository,
                                ShiftHandoverItemRepository itemRepository,
                                HandoverNumberService handoverNumberService,
                                BlockBorrowReservationRepository reservationRepository,
                                BlockTransferRepository transferRepository,
                                StocktakeItemRepository stocktakeItemRepository,
                                StocktakeBatchRepository stocktakeBatchRepository,
                                BufferBlockRepository blockRepository,
                                ProductionLineService productionLineService) {
        this.handoverRepository = handoverRepository;
        this.itemRepository = itemRepository;
        this.handoverNumberService = handoverNumberService;
        this.reservationRepository = reservationRepository;
        this.transferRepository = transferRepository;
        this.stocktakeItemRepository = stocktakeItemRepository;
        this.stocktakeBatchRepository = stocktakeBatchRepository;
        this.blockRepository = blockRepository;
        this.productionLineService = productionLineService;
    }

    // ---------------------------------------------------------------- 查询

    public Page<ShiftHandover> query(HandoverQueryDTO query) {
        int page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int size = query.getSize() == null || query.getSize() < 1 ? 10 : query.getSize();
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<ShiftHandover> result = handoverRepository.search(
                query.getStatus(),
                query.getHandoverNo() == null ? null : query.getHandoverNo().trim(),
                pageable);
        result.getContent().forEach(this::enrichHandover);
        return result;
    }

    public ShiftHandover getById(Long id) {
        ShiftHandover handover = handoverRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("交班记录不存在"));
        enrichHandover(handover);
        return handover;
    }

    /**
     * 交班事项清单：按 未还预约 → 待确认移交 → 待处理盘点差异 排序；
     * 清单内容为登记时快照，同时实时派生源单据当前状态供接班人参考。
     */
    public List<ShiftHandoverItem> getItems(Long handoverId) {
        getById(handoverId);
        List<ShiftHandoverItem> items = itemRepository.findByHandoverIdOrderByIdAsc(handoverId);
        items.sort(Comparator
                .comparingInt((ShiftHandoverItem i) -> TYPE_ORDER.getOrDefault(i.getItemType(), 99))
                .thenComparing(ShiftHandoverItem::getId));
        enrichSourceStatus(items);
        return items;
    }

    /**
     * 交班概览：当前进行中的交班单与未确认条数；无进行中交班时 inProgress=false。
     * 供交班页横幅、借用预约页闸门与首页提醒共用同一口径。
     */
    public HandoverOverviewVO overview() {
        HandoverOverviewVO vo = new HandoverOverviewVO();
        handoverRepository.findFirstByStatusOrderByIdDesc(ShiftHandover.STATUS_IN_PROGRESS)
                .ifPresent(h -> {
                    vo.setInProgress(true);
                    vo.setHandoverId(h.getId());
                    vo.setHandoverNo(h.getHandoverNo());
                    vo.setFromTeam(h.getFromTeam());
                    vo.setToTeam(h.getToTeam());
                    vo.setTotalCount(h.getTotalCount());
                    vo.setConfirmedCount(h.getConfirmedCount());
                    vo.setUnconfirmedCount(unconfirmedOf(h));
                    vo.setCreateTime(h.getCreateTime());
                });
        return vo;
    }

    /**
     * 交班登记预览：当前将被一次性登记的三类未结事项（瞬态快照，未落库）。
     */
    public HandoverPreviewVO preview() {
        List<ShiftHandoverItem> items = collectPendingItems();
        HandoverPreviewVO vo = new HandoverPreviewVO();
        vo.setItems(items);
        vo.setBorrowCount(countOfType(items, ShiftHandoverItem.TYPE_BORROW_UNRETURNED));
        vo.setTransferCount(countOfType(items, ShiftHandoverItem.TYPE_TRANSFER_PENDING));
        vo.setStocktakeCount(countOfType(items, ShiftHandoverItem.TYPE_STOCKTAKE_PENDING));
        return vo;
    }

    // ---------------------------------------------------------------- 交班登记

    /**
     * 登记交班：一次性快照当前全部未还预约、待确认移交、待处理盘点差异。
     * 同一时刻只允许一个交班中的交班单；没有任何未结事项时无需交班。
     */
    @Transactional
    public ShiftHandover create(HandoverCreateDTO dto) {
        validateCreate(dto);

        // 悲观锁串行化并发登记，保证“同一时刻只有一个交班中”的约束
        List<ShiftHandover> inProgress = handoverRepository.findInProgressForUpdate();
        if (!inProgress.isEmpty()) {
            ShiftHandover existing = inProgress.get(0);
            throw new RuntimeException("上一次交班（" + existing.getHandoverNo() + "）尚未完成，"
                    + "还剩 " + unconfirmedOf(existing) + " 条事项待接班人确认，请先完成交班");
        }

        List<ShiftHandoverItem> items = collectPendingItems();
        if (items.isEmpty()) {
            throw new RuntimeException("当前无未还预约、待确认移交或待处理盘点差异，无需登记交班");
        }

        ShiftHandover handover = new ShiftHandover();
        handover.setHandoverNo(handoverNumberService.nextHandoverNo(LocalDate.now()));
        handover.setFromTeam(dto.getFromTeam().trim());
        handover.setToTeam(dto.getToTeam().trim());
        handover.setHandoverOperator(dto.getHandoverOperator().trim());
        handover.setReceiveOperator(dto.getReceiveOperator().trim());
        handover.setRemark(trimToNull(dto.getRemark()));
        handover.setStatus(ShiftHandover.STATUS_IN_PROGRESS);
        handover.setTotalCount(items.size());
        handover.setConfirmedCount(0);
        handover = handoverRepository.saveAndFlush(handover);

        for (ShiftHandoverItem item : items) {
            item.setHandoverId(handover.getId());
            item.setStatus(ShiftHandoverItem.STATUS_PENDING);
            itemRepository.save(item);
        }
        itemRepository.flush();

        enrichHandover(handover);
        return handover;
    }

    private void validateCreate(HandoverCreateDTO dto) {
        if (dto == null) {
            throw new RuntimeException("交班参数不能为空");
        }
        if (dto.getFromTeam() == null || dto.getFromTeam().isBlank()) {
            throw new RuntimeException("请填写交班班组");
        }
        if (dto.getToTeam() == null || dto.getToTeam().isBlank()) {
            throw new RuntimeException("请填写接班班组");
        }
        if (dto.getHandoverOperator() == null || dto.getHandoverOperator().isBlank()) {
            throw new RuntimeException("请填写交班登记人");
        }
        if (dto.getReceiveOperator() == null || dto.getReceiveOperator().isBlank()) {
            throw new RuntimeException("请填写接班人");
        }
    }

    // ---------------------------------------------------------------- 接班人逐条确认

    /**
     * 接班人确认单条事项。确认人/时间随状态落库；全部事项确认后交班自动完成。
     */
    @Transactional
    public ShiftHandover confirmItem(Long handoverId, Long itemId, HandoverItemConfirmDTO dto) {
        if (dto == null || dto.getOperator() == null || dto.getOperator().isBlank()) {
            throw new RuntimeException("请填写确认人（接班人）");
        }
        String operator = dto.getOperator().trim();

        // 悲观锁串行化逐条确认，避免并发确认导致计数漂移或重复确认
        List<ShiftHandover> inProgress = handoverRepository.findInProgressForUpdate();
        ShiftHandover handover = inProgress.stream()
                .filter(h -> h.getId().equals(handoverId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("交班记录不存在或已完成，请刷新后查看最新状态"));

        ShiftHandoverItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("交班事项不存在"));
        if (!item.getHandoverId().equals(handover.getId())) {
            throw new RuntimeException("事项不属于当前交班单");
        }
        if (!ShiftHandoverItem.STATUS_PENDING.equals(item.getStatus())) {
            throw new RuntimeException("该事项已确认，无需重复操作");
        }

        item.setStatus(ShiftHandoverItem.STATUS_CONFIRMED);
        item.setConfirmOperator(operator);
        item.setConfirmTime(LocalDateTime.now());
        item.setConfirmNote(trimToNull(dto.getNote()));
        itemRepository.saveAndFlush(item);

        long confirmed = itemRepository.countByHandoverIdAndStatus(
                handover.getId(), ShiftHandoverItem.STATUS_CONFIRMED);
        handover.setConfirmedCount((int) confirmed);
        if (confirmed >= handover.getTotalCount()) {
            handover.setStatus(ShiftHandover.STATUS_COMPLETED);
            handover.setFinishTime(LocalDateTime.now());
        }
        handover = handoverRepository.save(handover);

        enrichHandover(handover);
        return handover;
    }

    // ---------------------------------------------------------------- 借用预约闸门

    /**
     * 交班未完成时禁止新开借用预约（由 BorrowReservationService 在登记前调用）。
     */
    public void assertNoInProgressHandover() {
        handoverRepository.findFirstByStatusOrderByIdDesc(ShiftHandover.STATUS_IN_PROGRESS)
                .ifPresent(h -> {
                    throw new RuntimeException("当前存在未完成的班组交班（" + h.getHandoverNo()
                            + "，还剩 " + unconfirmedOf(h) + " 条事项待接班人确认），交班完成前禁止新开借用预约");
                });
    }

    // ---------------------------------------------------------------- 事项快照采集

    /**
     * 采集当前三类未结事项并生成快照（未落库）：
     * 未还预约取占用中口径（已预约/已取走/逾时未取），与档案“已约出”标记同一集合；
     * 待确认移交取 PENDING；待处理盘点差异取盘点中批次的 PENDING 差异。
     */
    private List<ShiftHandoverItem> collectPendingItems() {
        List<ShiftHandoverItem> items = new ArrayList<>();

        List<BlockBorrowReservation> reservations =
                reservationRepository.findByStatusIn(BorrowReservationService.ACTIVE_STATUSES);
        List<BlockTransfer> transfers = transferRepository.findByStatus(BlockTransfer.STATUS_PENDING);
        List<StocktakeItem> discrepancies = stocktakeItemRepository.findPendingDiscrepanciesInCountingBatches();

        Map<Long, BufferBlock> blockMap = blocksById(reservations, transfers);

        for (BlockBorrowReservation r : reservations) {
            ShiftHandoverItem item = new ShiftHandoverItem();
            item.setItemType(ShiftHandoverItem.TYPE_BORROW_UNRETURNED);
            item.setRefId(r.getId());
            item.setRefNo(r.getReservationNo());
            item.setBlockId(r.getBlockId());
            item.setBlockCode(blockCodeOf(blockMap, r.getBlockId()));
            item.setSummary(buildBorrowSummary(r));
            items.add(item);
        }

        for (BlockTransfer t : transfers) {
            ShiftHandoverItem item = new ShiftHandoverItem();
            item.setItemType(ShiftHandoverItem.TYPE_TRANSFER_PENDING);
            item.setRefId(t.getId());
            item.setRefNo(t.getTransferNo());
            item.setBlockId(t.getBlockId());
            item.setBlockCode(blockCodeOf(blockMap, t.getBlockId()));
            item.setSummary("移交单等待接收方确认：" + lineName(t.getFromLineId()) + " → "
                    + lineName(t.getToLineId()) + "，移交人：" + nullToDash(t.getTransferOperator())
                    + "，登记于 " + formatDateTime(t.getCreateTime()));
            items.add(item);
        }

        if (!discrepancies.isEmpty()) {
            Map<Long, StocktakeBatch> batchMap = stocktakeBatchRepository
                            .findAllById(discrepancies.stream().map(StocktakeItem::getBatchId).distinct().toList())
                    .stream().collect(Collectors.toMap(StocktakeBatch::getId, Function.identity()));
            for (StocktakeItem i : discrepancies) {
                StocktakeBatch batch = batchMap.get(i.getBatchId());
                ShiftHandoverItem item = new ShiftHandoverItem();
                item.setItemType(ShiftHandoverItem.TYPE_STOCKTAKE_PENDING);
                item.setRefId(i.getId());
                item.setRefNo(batch != null ? batch.getBatchNo() : null);
                item.setBlockId(i.getBlockId());
                item.setBlockCode(i.getBlockCode() != null ? i.getBlockCode() : blockCodeOf(blockMap, i.getBlockId()));
                item.setSummary("盘点批次 " + (batch != null ? batch.getBatchNo() : "-")
                        + "（" + (batch != null ? lineName(batch.getLineId()) : "-") + "）差异："
                        + StocktakeService.discrepancyTypeText(i.getDiscrepancyType()) + "，待处理");
                items.add(item);
            }
        }

        items.sort(Comparator
                .comparingInt((ShiftHandoverItem i) -> TYPE_ORDER.getOrDefault(i.getItemType(), 99))
                .thenComparing(ShiftHandoverItem::getRefNo, Comparator.nullsLast(String::compareTo)));
        return items;
    }

    private String buildBorrowSummary(BlockBorrowReservation r) {
        StringBuilder sb = new StringBuilder();
        sb.append("班组「").append(r.getTeamName()).append("」")
                .append(BorrowReservationService.statusText(r.getStatus()))
                .append("，约定 ").append(formatDateTime(r.getPickupTime())).append(" 取用");
        if (r.getPlannedReturnTime() != null) {
            sb.append("，计划 ").append(formatDateTime(r.getPlannedReturnTime())).append(" 归还");
        }
        sb.append("，归还点：").append(nullToDash(r.getReturnPoint()));
        boolean overdueReturn = BlockBorrowReservation.STATUS_PICKED_UP.equals(r.getStatus())
                && r.getPlannedReturnTime() != null
                && r.getPlannedReturnTime().isBefore(LocalDateTime.now());
        if (overdueReturn) {
            sb.append("（已超期未还）");
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- 源单据当前状态派生

    /**
     * 读取清单时实时派生源单据当前状态：事项清单本身是登记时快照（不变），
     * 源状态仅供接班人判断该事项当前是否仍未结（如预约已归还/移交已确认）。
     */
    private void enrichSourceStatus(List<ShiftHandoverItem> items) {
        Map<Long, BlockBorrowReservation> reservationMap = new HashMap<>();
        Map<Long, BlockTransfer> transferMap = new HashMap<>();
        Map<Long, StocktakeItem> stocktakeMap = new HashMap<>();

        List<Long> reservationIds = refIds(items, ShiftHandoverItem.TYPE_BORROW_UNRETURNED);
        if (!reservationIds.isEmpty()) {
            reservationRepository.findAllById(reservationIds)
                    .forEach(r -> reservationMap.put(r.getId(), r));
        }
        List<Long> transferIds = refIds(items, ShiftHandoverItem.TYPE_TRANSFER_PENDING);
        if (!transferIds.isEmpty()) {
            transferRepository.findAllById(transferIds)
                    .forEach(t -> transferMap.put(t.getId(), t));
        }
        List<Long> stocktakeIds = refIds(items, ShiftHandoverItem.TYPE_STOCKTAKE_PENDING);
        if (!stocktakeIds.isEmpty()) {
            stocktakeItemRepository.findAllById(stocktakeIds)
                    .forEach(i -> stocktakeMap.put(i.getId(), i));
        }

        for (ShiftHandoverItem item : items) {
            switch (item.getItemType()) {
                case ShiftHandoverItem.TYPE_BORROW_UNRETURNED -> {
                    BlockBorrowReservation r = reservationMap.get(item.getRefId());
                    if (r == null) {
                        item.setSourceStatusText("记录已删除");
                        item.setSourceStillOpen(false);
                    } else {
                        boolean overdueReturn = BlockBorrowReservation.STATUS_PICKED_UP.equals(r.getStatus())
                                && r.getPlannedReturnTime() != null
                                && r.getPlannedReturnTime().isBefore(LocalDateTime.now());
                        item.setSourceStatusText(BorrowReservationService.statusText(r.getStatus())
                                + (overdueReturn ? "·超期未还" : ""));
                        item.setSourceStillOpen(BorrowReservationService.ACTIVE_STATUSES.contains(r.getStatus()));
                    }
                }
                case ShiftHandoverItem.TYPE_TRANSFER_PENDING -> {
                    BlockTransfer t = transferMap.get(item.getRefId());
                    if (t == null) {
                        item.setSourceStatusText("记录已删除");
                        item.setSourceStillOpen(false);
                    } else {
                        item.setSourceStatusText(BlockTransferService.statusText(t.getStatus()));
                        item.setSourceStillOpen(BlockTransfer.STATUS_PENDING.equals(t.getStatus()));
                    }
                }
                case ShiftHandoverItem.TYPE_STOCKTAKE_PENDING -> {
                    StocktakeItem i = stocktakeMap.get(item.getRefId());
                    if (i == null) {
                        item.setSourceStatusText("记录已删除");
                        item.setSourceStillOpen(false);
                    } else {
                        item.setSourceStatusText(StocktakeService.discrepancyTypeText(i.getDiscrepancyType())
                                + "·" + StocktakeService.statusText(
                                        i.getDiscrepancyStatus() == null ? "NONE" : i.getDiscrepancyStatus()));
                        item.setSourceStillOpen(StocktakeItem.STATUS_PENDING.equals(i.getDiscrepancyStatus())
                                && !StocktakeItem.DIFF_NONE.equals(i.getDiscrepancyType()));
                    }
                }
                default -> {
                    item.setSourceStatusText("-");
                    item.setSourceStillOpen(false);
                }
            }
        }
    }

    private List<Long> refIds(List<ShiftHandoverItem> items, String type) {
        return items.stream()
                .filter(i -> type.equals(i.getItemType()) && i.getRefId() != null)
                .map(ShiftHandoverItem::getRefId)
                .distinct()
                .toList();
    }

    // ---------------------------------------------------------------- 内部方法

    private Map<Long, BufferBlock> blocksById(List<BlockBorrowReservation> reservations,
                                              List<BlockTransfer> transfers) {
        List<Long> blockIds = new ArrayList<>();
        reservations.forEach(r -> blockIds.add(r.getBlockId()));
        transfers.forEach(t -> blockIds.add(t.getBlockId()));
        if (blockIds.isEmpty()) {
            return Map.of();
        }
        return blockRepository.findAllById(blockIds.stream().distinct().toList())
                .stream().collect(Collectors.toMap(BufferBlock::getId, Function.identity()));
    }

    private String blockCodeOf(Map<Long, BufferBlock> blockMap, Long blockId) {
        BufferBlock block = blockId == null ? null : blockMap.get(blockId);
        return block != null ? block.getBlockCode() : null;
    }

    private String lineName(Long lineId) {
        if (lineId == null) {
            return "-";
        }
        ProductionLine line = productionLineService.getById(lineId);
        return line != null ? line.getLineName() : "-";
    }

    private long countOfType(List<ShiftHandoverItem> items, String type) {
        return items.stream().filter(i -> type.equals(i.getItemType())).count();
    }

    private void enrichHandover(ShiftHandover handover) {
        handover.setUnconfirmedCount(unconfirmedOf(handover));
    }

    private int unconfirmedOf(ShiftHandover handover) {
        int total = handover.getTotalCount() == null ? 0 : handover.getTotalCount();
        int confirmed = handover.getConfirmedCount() == null ? 0 : handover.getConfirmedCount();
        return Math.max(total - confirmed, 0);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String formatDateTime(LocalDateTime time) {
        return time == null ? "-" : time.toString().replace('T', ' ');
    }

    public static String statusText(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case ShiftHandover.STATUS_IN_PROGRESS -> "交班中";
            case ShiftHandover.STATUS_COMPLETED -> "已完成";
            default -> status;
        };
    }

    public static String itemTypeText(String itemType) {
        if (itemType == null) {
            return "未知";
        }
        return switch (itemType) {
            case ShiftHandoverItem.TYPE_BORROW_UNRETURNED -> "未还预约";
            case ShiftHandoverItem.TYPE_TRANSFER_PENDING -> "待确认移交";
            case ShiftHandoverItem.TYPE_STOCKTAKE_PENDING -> "待处理盘点差异";
            default -> itemType;
        };
    }
}
