package com.bufferblock.service;

import com.bufferblock.dto.BorrowCancelDTO;
import com.bufferblock.dto.BorrowHandleDTO;
import com.bufferblock.dto.BorrowOverviewVO;
import com.bufferblock.dto.BorrowReservationCreateDTO;
import com.bufferblock.dto.BorrowReservationQueryDTO;
import com.bufferblock.entity.BlockBorrowFlowRecord;
import com.bufferblock.entity.BlockBorrowReservation;
import com.bufferblock.entity.BlockLineBinding;
import com.bufferblock.entity.BufferBlock;
import com.bufferblock.entity.ProductionLine;
import com.bufferblock.repository.BlockBorrowFlowRecordRepository;
import com.bufferblock.repository.BlockBorrowReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 缓冲挡块借用预约业务：
 * <ul>
 *     <li>班组只能预约空闲挡块（在用、未挂起、无占用中预约/待确认移交）；</li>
 *     <li>预约登记即占用，档案列表通过 {@link #activeReservationMap()} 显示“已约出”；</li>
 *     <li>取走登记后状态为“已取走”，实际取走人与实际取走时刻随单落库；归还时可修改实际归还点，
 *     默认沿用约定归还点，改动写入流转记录；</li>
 *     <li>已取走但超过计划还期仍未归还的，列表实时派生“超期未还”标记与超期时长，整行标红；</li>
 *     <li>取消必须填写原因，原因随状态一并落库；</li>
 *     <li>到约定取用时间未取走（含宽限期）由定时任务置为“逾时未取”并提醒，提醒落库；</li>
 *     <li>班组交班未完成（接班人未逐条确认完）期间禁止新开预约；</li>
 *     <li>全部状态均落库持久化，刷新/重开页面后档案标记与预约状态保持一致。</li>
 * </ul>
 */
@Service
public class BorrowReservationService {

    private static final Logger log = LoggerFactory.getLogger(BorrowReservationService.class);

    /** 到点未取的宽限期（分钟）：约定时间过 5 分钟仍未取走才提醒，避免分秒级抖动 */
    public static final long OVERDUE_GRACE_MINUTES = 5;

    /** 占用中状态：这些状态的挡块在档案上显示“已约出”，且不能再次预约 */
    public static final List<String> ACTIVE_STATUSES =
            List.of(BlockBorrowReservation.STATUS_RESERVED,
                    BlockBorrowReservation.STATUS_PICKED_UP,
                    BlockBorrowReservation.STATUS_OVERDUE);

    /** 可取消/可取走的状态（逾时未取后仍允许班组补取或取消） */
    private static final Set<String> OPEN_STATUSES = Set.of(
            BlockBorrowReservation.STATUS_RESERVED,
            BlockBorrowReservation.STATUS_OVERDUE);

    private final BlockBorrowReservationRepository reservationRepository;
    private final BlockBorrowFlowRecordRepository flowRecordRepository;
    private final BufferBlockService bufferBlockService;
    private final ProductionLineService productionLineService;
    private final CalibrationService calibrationService;
    private final BorrowNumberService borrowNumberService;
    private final ShiftHandoverService shiftHandoverService;

    public BorrowReservationService(BlockBorrowReservationRepository reservationRepository,
                                    BlockBorrowFlowRecordRepository flowRecordRepository,
                                    BufferBlockService bufferBlockService,
                                    ProductionLineService productionLineService,
                                    CalibrationService calibrationService,
                                    BorrowNumberService borrowNumberService,
                                    ShiftHandoverService shiftHandoverService) {
        this.reservationRepository = reservationRepository;
        this.flowRecordRepository = flowRecordRepository;
        this.bufferBlockService = bufferBlockService;
        this.productionLineService = productionLineService;
        this.calibrationService = calibrationService;
        this.borrowNumberService = borrowNumberService;
        this.shiftHandoverService = shiftHandoverService;
    }

    // ---------------------------------------------------------------- 查询

    public Page<BlockBorrowReservation> query(BorrowReservationQueryDTO query) {
        int page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int size = query.getSize() == null || query.getSize() < 1 ? 10 : query.getSize();
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<BlockBorrowReservation> result = reservationRepository.queryReservations(
                query.getStatus(),
                query.getTeamName() == null ? null : query.getTeamName().trim(),
                query.getBlockCode() == null ? null : query.getBlockCode().trim(),
                pageable);
        result.getContent().forEach(this::enrich);
        return result;
    }

    public BlockBorrowReservation getById(Long id) {
        BlockBorrowReservation reservation = reservationRepository.findById(id).orElse(null);
        if (reservation != null) {
            enrich(reservation);
        }
        return reservation;
    }

    public List<BlockBorrowReservation> getByBlockId(Long blockId) {
        List<BlockBorrowReservation> list =
                reservationRepository.findByBlockIdOrderByCreateTimeDesc(blockId);
        list.forEach(this::enrich);
        return list;
    }

    public List<BlockBorrowFlowRecord> getFlowRecords(Long reservationId) {
        return flowRecordRepository.findByReservationIdOrderByCreateTimeAscIdAsc(reservationId);
    }

    /**
     * 概览计数：已预约/已取走/逾时未取/占用合计，供首页与列表顶部提醒。
     */
    public BorrowOverviewVO overview() {
        BorrowOverviewVO vo = new BorrowOverviewVO();
        long reserved = reservationRepository.countByStatus(BlockBorrowReservation.STATUS_RESERVED);
        long pickedUp = reservationRepository.countByStatus(BlockBorrowReservation.STATUS_PICKED_UP);
        long overdue = reservationRepository.countByStatus(BlockBorrowReservation.STATUS_OVERDUE);
        vo.setReservedCount(reserved);
        vo.setPickedUpCount(pickedUp);
        vo.setOverdueCount(overdue);
        vo.setOverdueReturnCount(reservationRepository.countOverdueReturn(LocalDateTime.now()));
        vo.setActiveCount(reserved + pickedUp + overdue);
        return vo;
    }

    /**
     * 占用中预约按挡块归集（同一挡块同时只允许一张占用单），供挡块档案标记“已约出”。
     * 每次读档案实时派生：关掉页面再打开，标记仍与预约状态对得上。
     */
    public java.util.Map<Long, BlockBorrowReservation> activeReservationMap() {
        java.util.Map<Long, BlockBorrowReservation> map = new java.util.HashMap<>();
        for (BlockBorrowReservation r : reservationRepository.findByStatusIn(ACTIVE_STATUSES)) {
            // 派生“超期未还”，保证档案标记与预约台列表同一口径
            deriveOverdueReturn(r);
            map.putIfAbsent(r.getBlockId(), r);
        }
        return map;
    }

    // ---------------------------------------------------------------- 预约登记

    @Transactional
    public BlockBorrowReservation create(BorrowReservationCreateDTO dto) {
        validateCreate(dto);

        // 交班未完成（接班人未逐条确认完）时禁止新开预约，交班完成后自动恢复
        shiftHandoverService.assertNoInProgressHandover();

        // 锁定挡块主数据，串行化同一挡块的并发预约，避免重复占用
        BufferBlock block = bufferBlockService.lockEntityById(dto.getBlockId());
        if (block == null) {
            throw new RuntimeException("挡块不存在");
        }
        if (BufferBlock.STATUS_SUSPENDED.equals(block.getServiceStatus())) {
            throw new RuntimeException("该挡块挂起待修中，暂不可预约");
        }
        // 校准逾期的挡块同样不允许借出（与移交口径一致）
        calibrationService.assertTransferable(dto.getBlockId());

        List<BlockBorrowReservation> active =
                reservationRepository.findByBlockIdAndStatusIn(dto.getBlockId(), ACTIVE_STATUSES);
        if (!active.isEmpty()) {
            throw new RuntimeException("该挡块已存在占用中的借用预约（" + active.get(0).getReservationNo() + "），暂不可约");
        }
        if (bufferBlockService.hasPendingTransfer(dto.getBlockId())) {
            throw new RuntimeException("该挡块存在待确认的移交单，暂不可预约");
        }

        LocalDateTime pickupTime = dto.getPickupTime();
        LocalDateTime plannedReturn = dto.getPlannedReturnTime();
        if (plannedReturn != null && plannedReturn.isBefore(pickupTime)) {
            throw new RuntimeException("计划归还时间不能早于约定取用时间");
        }

        BlockBorrowReservation reservation = new BlockBorrowReservation();
        reservation.setReservationNo(borrowNumberService.nextReservationNo(pickupTime.toLocalDate()));
        reservation.setBlockId(dto.getBlockId());
        reservation.setTeamName(dto.getTeamName().trim());
        reservation.setPickupTime(pickupTime);
        reservation.setPlannedReturnTime(plannedReturn);
        reservation.setReturnPoint(dto.getReturnPoint().trim());
        reservation.setContactPerson(trimToNull(dto.getContactPerson()));
        reservation.setContactPhone(trimToNull(dto.getContactPhone()));
        reservation.setPurpose(trimToNull(dto.getPurpose()));
        reservation.setRemark(trimToNull(dto.getRemark()));
        reservation.setStatus(BlockBorrowReservation.STATUS_RESERVED);
        reservation.setRemindCount(0);
        reservation = reservationRepository.saveAndFlush(reservation);

        recordFlow(reservation.getId(), BlockBorrowFlowRecord.ACTION_BOOK, null,
                BlockBorrowReservation.STATUS_RESERVED, trimToNull(dto.getOperator()),
                "借用预约登记，约定 " + formatDateTime(pickupTime) + " 取用，归还点：" + reservation.getReturnPoint());

        enrich(reservation);
        return reservation;
    }

    private void validateCreate(BorrowReservationCreateDTO dto) {
        if (dto == null) {
            throw new RuntimeException("预约参数不能为空");
        }
        if (dto.getBlockId() == null) {
            throw new RuntimeException("请选择要预约的挡块");
        }
        if (dto.getTeamName() == null || dto.getTeamName().isBlank()) {
            throw new RuntimeException("请填写借用班组");
        }
        if (dto.getPickupTime() == null) {
            throw new RuntimeException("请选择约定取用时间");
        }
        if (dto.getReturnPoint() == null || dto.getReturnPoint().isBlank()) {
            throw new RuntimeException("请填写归还点");
        }
        if (dto.getOperator() == null || dto.getOperator().isBlank()) {
            throw new RuntimeException("请填写预约登记人");
        }
    }

    // ---------------------------------------------------------------- 取走

    @Transactional
    public BlockBorrowReservation pickup(Long id, BorrowHandleDTO dto) {
        BlockBorrowReservation reservation = getOpenReservation(id);
        String operator = requireOperator(dto == null ? null : dto.getOperator(), "取走登记人");

        String fromStatus = reservation.getStatus();
        reservation.setStatus(BlockBorrowReservation.STATUS_PICKED_UP);
        reservation.setPickupOperator(operator);
        reservation.setActualPickupTime(LocalDateTime.now());
        reservation = reservationRepository.save(reservation);

        String note;
        if (BlockBorrowReservation.STATUS_OVERDUE.equals(fromStatus)) {
            note = "逾时后补登记取走" + appendNote(dto);
        } else {
            note = "班组按约取走" + appendNote(dto);
        }
        recordFlow(reservation.getId(), BlockBorrowFlowRecord.ACTION_PICKUP, fromStatus,
                BlockBorrowReservation.STATUS_PICKED_UP, operator, note);

        enrich(reservation);
        return reservation;
    }

    // ---------------------------------------------------------------- 归还

    @Transactional
    public BlockBorrowReservation giveBack(Long id, BorrowHandleDTO dto) {
        BlockBorrowReservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("预约记录不存在"));
        if (!BlockBorrowReservation.STATUS_PICKED_UP.equals(reservation.getStatus())) {
            throw new RuntimeException("仅已取走的预约单可登记归还，当前状态：" + statusText(reservation.getStatus()));
        }
        String operator = requireOperator(dto == null ? null : dto.getOperator(), "归还登记人");
        // 归还时允许改归还点：留空沿用预约约定，填写了以实际归还点为准并在流转中留痕
        String plannedPoint = reservation.getReturnPoint();
        String actualPoint = trimToNull(dto == null ? null : dto.getActualReturnPoint());
        if (actualPoint != null && actualPoint.length() > 200) {
            throw new RuntimeException("实际归还点长度不能超过200字");
        }
        String effectivePoint = actualPoint != null ? actualPoint : plannedPoint;

        reservation.setStatus(BlockBorrowReservation.STATUS_RETURNED);
        reservation.setReturnOperator(operator);
        reservation.setActualReturnTime(LocalDateTime.now());
        reservation.setActualReturnPoint(effectivePoint);
        reservation = reservationRepository.save(reservation);

        StringBuilder note = new StringBuilder("实物已归还至");
        if (actualPoint != null && !actualPoint.equals(plannedPoint)) {
            note.append("实际归还点：").append(actualPoint)
                    .append("（原约定归还点：").append(plannedPoint).append("）");
        } else {
            note.append("约定归还点：").append(effectivePoint);
        }
        note.append(appendNote(dto));
        recordFlow(reservation.getId(), BlockBorrowFlowRecord.ACTION_RETURN,
                BlockBorrowReservation.STATUS_PICKED_UP, BlockBorrowReservation.STATUS_RETURNED,
                operator, note.toString());

        enrich(reservation);
        return reservation;
    }

    // ---------------------------------------------------------------- 取消（必须写原因）

    @Transactional
    public BlockBorrowReservation cancel(Long id, BorrowCancelDTO dto) {
        BlockBorrowReservation reservation = getOpenReservation(id);
        if (dto == null || dto.getCancelReason() == null || dto.getCancelReason().isBlank()) {
            throw new RuntimeException("取消预约必须填写取消原因");
        }
        String reason = dto.getCancelReason().trim();
        String operator = requireOperator(dto.getOperator(), "取消操作人");

        String fromStatus = reservation.getStatus();
        reservation.setStatus(BlockBorrowReservation.STATUS_CANCELLED);
        reservation.setCancelReason(reason);
        reservation.setCancelOperator(operator);
        reservation.setCancelTime(LocalDateTime.now());
        reservation = reservationRepository.save(reservation);

        // 取消原因写入流转记录，档案占用标记随之解除
        recordFlow(reservation.getId(), BlockBorrowFlowRecord.ACTION_CANCEL, fromStatus,
                BlockBorrowReservation.STATUS_CANCELLED, operator, "取消原因：" + reason);

        enrich(reservation);
        return reservation;
    }

    // ---------------------------------------------------------------- 到点未取提醒

    /**
     * 扫描到点未取的已预约单：超过约定取用时间 + 宽限期仍未取走/取消的，
     * 置为“逾时未取”并登记一次提醒。返回本次新提醒的单数。
     *
     * <p>由定时任务周期性调用；提醒次数与最近提醒时间随状态落库，
     * 关闭页面再打开仍能看到“逾时未取”标记与提醒记录。</p>
     */
    @Transactional
    public int scanOverduePickups() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(OVERDUE_GRACE_MINUTES);
        // 悲观锁串行化：避免扫描与人工取走/取消并发导致状态误覆盖
        List<BlockBorrowReservation> dueList =
                reservationRepository.findDueReservationsForUpdate(deadline);
        int reminded = 0;
        for (BlockBorrowReservation reservation : dueList) {
            // 锁内二次确认状态，过滤并发窗口内已被处理的单据
            if (!BlockBorrowReservation.STATUS_RESERVED.equals(reservation.getStatus())) {
                continue;
            }
            reservation.setStatus(BlockBorrowReservation.STATUS_OVERDUE);
            reservation.setRemindCount((reservation.getRemindCount() == null ? 0 : reservation.getRemindCount()) + 1);
            reservation.setLastRemindTime(LocalDateTime.now());
            reservationRepository.save(reservation);

            recordFlow(reservation.getId(), BlockBorrowFlowRecord.ACTION_OVERDUE_REMIND,
                    BlockBorrowReservation.STATUS_RESERVED, BlockBorrowReservation.STATUS_OVERDUE,
                    "系统", "已过约定取用时间（" + formatDateTime(reservation.getPickupTime())
                            + "）" + OVERDUE_GRACE_MINUTES + " 分钟仍未取走，请提醒班组 "
                            + reservation.getTeamName());
            reminded++;
            log.warn("[借用提醒] 预约单 {}（挡块 {}，班组 {}）逾时未取，已登记提醒",
                    reservation.getReservationNo(), reservation.getBlockId(), reservation.getTeamName());
        }
        return reminded;
    }

    // ---------------------------------------------------------------- 内部方法

    private BlockBorrowReservation getOpenReservation(Long id) {
        BlockBorrowReservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("预约记录不存在"));
        if (!OPEN_STATUSES.contains(reservation.getStatus())) {
            throw new RuntimeException("当前状态（" + statusText(reservation.getStatus()) + "）不允许该操作");
        }
        return reservation;
    }

    private String requireOperator(String operator, String fieldLabel) {
        if (operator == null || operator.isBlank()) {
            throw new RuntimeException("请填写" + fieldLabel);
        }
        return operator.trim();
    }

    private String appendNote(BorrowHandleDTO dto) {
        if (dto != null && dto.getNote() != null && !dto.getNote().isBlank()) {
            return "（" + dto.getNote().trim() + "）";
        }
        return "";
    }

    private void recordFlow(Long reservationId, String action, String fromStatus, String toStatus,
                            String operator, String note) {
        flowRecordRepository.saveAndFlush(
                new BlockBorrowFlowRecord(reservationId, action, fromStatus, toStatus, operator, note));
    }

    private void enrich(BlockBorrowReservation r) {
        BufferBlock block = bufferBlockService.getEntityById(r.getBlockId());
        if (block != null) {
            r.setBlockCode(block.getBlockCode());
            r.setAdapterModel(block.getAdapterModel());
            r.setThickness(block.getThickness());
            r.setSpecTemplate(block.getSpecTemplate());
            r.setServiceStatus(block.getServiceStatus());
            BlockLineBinding binding = bufferBlockService.getCurrentBinding(r.getBlockId());
            if (binding != null) {
                ProductionLine line = productionLineService.getById(binding.getLineId());
                if (line != null) {
                    r.setCurrentLineName(line.getLineName());
                }
            }
        }
        if (BlockBorrowReservation.STATUS_OVERDUE.equals(r.getStatus()) && r.getPickupTime() != null) {
            r.setOverdueDuration(formatDuration(Duration.between(r.getPickupTime(), LocalDateTime.now())));
        }
        // 超期未还派生不依赖挡块主数据，档案页经 activeReservationMap 取占用单时也会调用
        deriveOverdueReturn(r);
    }

    /**
     * 超期未还：已取走占用中、登记了计划还期且已过期。纯读时派生，不新增持久化状态，
     * 因此再进预约台/档案页始终与实际占用和流转记录对得上。
     */
    private void deriveOverdueReturn(BlockBorrowReservation r) {
        boolean overdueReturn = BlockBorrowReservation.STATUS_PICKED_UP.equals(r.getStatus())
                && r.getPlannedReturnTime() != null
                && r.getPlannedReturnTime().isBefore(LocalDateTime.now());
        r.setOverdueReturn(overdueReturn);
        r.setOverdueReturnDuration(overdueReturn
                ? formatDuration(Duration.between(r.getPlannedReturnTime(), LocalDateTime.now()))
                : null);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String formatDateTime(LocalDateTime time) {
        return time == null ? "" : time.toString().replace('T', ' ');
    }

    private String formatDuration(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return "0分钟";
        }
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("天");
        }
        if (hours > 0) {
            sb.append(hours).append("小时");
        }
        if (minutes > 0 || sb.length() == 0) {
            sb.append(minutes).append("分钟");
        }
        return sb.toString();
    }

    public static String statusText(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case BlockBorrowReservation.STATUS_RESERVED -> "已预约";
            case BlockBorrowReservation.STATUS_PICKED_UP -> "已取走";
            case BlockBorrowReservation.STATUS_RETURNED -> "已归还";
            case BlockBorrowReservation.STATUS_CANCELLED -> "已取消";
            case BlockBorrowReservation.STATUS_OVERDUE -> "逾时未取";
            default -> status;
        };
    }
}
