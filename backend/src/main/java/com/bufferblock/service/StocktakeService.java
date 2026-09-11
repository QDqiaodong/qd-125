package com.bufferblock.service;

import com.bufferblock.dto.StocktakeBatchCreateDTO;
import com.bufferblock.dto.StocktakeBatchQueryDTO;
import com.bufferblock.dto.StocktakeCountDTO;
import com.bufferblock.dto.StocktakeHandleDTO;
import com.bufferblock.dto.StocktakeItemQueryDTO;
import com.bufferblock.dto.StocktakeOverviewVO;
import com.bufferblock.dto.StocktakeTraceVO;
import com.bufferblock.entity.BlockLineBinding;
import com.bufferblock.entity.BlockTransfer;
import com.bufferblock.entity.BufferBlock;
import com.bufferblock.entity.ProductionLine;
import com.bufferblock.entity.StocktakeBatch;
import com.bufferblock.entity.StocktakeItem;
import com.bufferblock.entity.TransferFlowRecord;
import com.bufferblock.repository.BlockLineBindingRepository;
import com.bufferblock.repository.BlockTransferRepository;
import com.bufferblock.repository.BufferBlockRepository;
import com.bufferblock.repository.StocktakeBatchRepository;
import com.bufferblock.repository.StocktakeItemRepository;
import com.bufferblock.repository.TransferFlowRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 挡块盘点差异闭环核心服务：
 * 创建批次（快照应盘清单）→ 逐项录入实物 → 自动比对当前绑定与最近移交 →
 * 差异标记（缺失/错线/重复/盘盈/损坏报废）→ 差异确认/备注 → 闭环结束/导出/追溯。
 */
@Service
public class StocktakeService {

    private final StocktakeBatchRepository batchRepository;
    private final StocktakeItemRepository itemRepository;
    private final BufferBlockRepository blockRepository;
    private final BlockLineBindingRepository bindingRepository;
    private final BlockTransferRepository transferRepository;
    private final TransferFlowRecordRepository flowRecordRepository;
    private final ProductionLineService productionLineService;
    private final StocktakeNumberService numberService;

    public StocktakeService(StocktakeBatchRepository batchRepository,
                            StocktakeItemRepository itemRepository,
                            BufferBlockRepository blockRepository,
                            BlockLineBindingRepository bindingRepository,
                            BlockTransferRepository transferRepository,
                            TransferFlowRecordRepository flowRecordRepository,
                            ProductionLineService productionLineService,
                            StocktakeNumberService numberService) {
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.blockRepository = blockRepository;
        this.bindingRepository = bindingRepository;
        this.transferRepository = transferRepository;
        this.flowRecordRepository = flowRecordRepository;
        this.productionLineService = productionLineService;
        this.numberService = numberService;
    }

    // ------------------------------------------------------------------
    // 批次
    // ------------------------------------------------------------------

    public Page<StocktakeBatch> queryBatches(StocktakeBatchQueryDTO query) {
        Pageable pageable = PageRequest.of(
                Math.max(query.getPage() == null ? 1 : query.getPage(), 1) - 1,
                query.getSize() == null ? 10 : query.getSize());
        Page<StocktakeBatch> page = batchRepository.search(
                query.getLineId(), query.getStatus(), query.getStartDate(), query.getEndDate(),
                query.getBatchNo() == null || query.getBatchNo().isBlank() ? null : query.getBatchNo().trim(),
                pageable);
        page.getContent().forEach(this::enrichBatch);
        return page;
    }

    public StocktakeBatch getBatch(Long batchId) {
        StocktakeBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("盘点批次不存在"));
        enrichBatch(batch);
        return batch;
    }

    public StocktakeOverviewVO getOverview() {
        StocktakeOverviewVO vo = new StocktakeOverviewVO();
        vo.setCountingBatches(batchRepository.countByStatus(StocktakeBatch.STATUS_COUNTING));
        vo.setPendingDiscrepancies(batchRepository.sumPendingCountByStatus(StocktakeBatch.STATUS_COUNTING));
        return vo;
    }

    /**
     * 创建盘点批次：校验同一产线无盘点中批次后，快照当前绑定在该产线的挡块作为应盘清单。
     */
    @Transactional
    public StocktakeBatch createBatch(StocktakeBatchCreateDTO dto) {
        if (dto == null) {
            throw new RuntimeException("盘点参数不能为空");
        }
        if (dto.getLineId() == null) {
            throw new RuntimeException("请选择盘点产线");
        }
        ProductionLine line = productionLineService.getById(dto.getLineId());
        if (line == null) {
            throw new RuntimeException("盘点产线不存在");
        }
        if (line.getParentId() == null) {
            throw new RuntimeException("车间节点不可直接盘点，请选择具体产线");
        }
        if (dto.getOperator() == null || dto.getOperator().isBlank()) {
            throw new RuntimeException("请填写盘点负责人");
        }
        LocalDate stocktakeDate = dto.getStocktakeDate() != null ? dto.getStocktakeDate() : LocalDate.now();

        batchRepository.findFirstByLineIdAndStatusOrderByIdDesc(dto.getLineId(), StocktakeBatch.STATUS_COUNTING)
                .ifPresent(existing -> {
                    throw new RuntimeException("该产线已存在盘点中的批次（" + existing.getBatchNo()
                            + "），请先完成或关闭该批次");
                });

        StocktakeBatch batch = new StocktakeBatch();
        batch.setBatchNo(numberService.nextBatchNo(stocktakeDate));
        batch.setLineId(dto.getLineId());
        batch.setStocktakeDate(stocktakeDate);
        batch.setOperator(dto.getOperator().trim());
        batch.setRemark(dto.getRemark());
        batch.setStatus(StocktakeBatch.STATUS_COUNTING);
        batch = batchRepository.saveAndFlush(batch);

        // 快照当前绑定在盘点产线的挡块作为应盘清单（不随后续绑定变化而改变，保证盘点口径可审计）
        List<BlockLineBinding> currentBindings = bindingRepository.findByLineIdAndIsCurrent(dto.getLineId(), 1);
        int total = 0;
        for (BlockLineBinding binding : currentBindings) {
            BufferBlock block = blockRepository.findById(binding.getBlockId()).orElse(null);
            if (block == null) {
                continue;
            }
            StocktakeItem item = new StocktakeItem();
            item.setBatchId(batch.getId());
            item.setBlockId(block.getId());
            item.setBlockCode(block.getBlockCode());
            item.setExpectedLineId(dto.getLineId());
            item.setBoundLineId(dto.getLineId());
            item.setIsExtra(0);
            item.setIsCounted(0);
            item.setRepeatCount(0);
            item.setDiscrepancyType(StocktakeItem.DIFF_NONE);
            item.setDiscrepancyStatus(null);
            itemRepository.save(item);
            total++;
        }

        batch.setTotalCount(total);
        batch.setCountedCount(0);
        batch.setDiscrepancyCount(0);
        batch.setPendingCount(0);
        batch = batchRepository.save(batch);
        enrichBatch(batch);
        return batch;
    }

    /**
     * 结束盘点：剩余“待盘”行统一标记为缺失差异；存在待处理差异时不允许封账（差异闭环）。
     */
    @Transactional
    public StocktakeBatch finishBatch(Long batchId) {
        getCountingBatch(batchId);
        // markWaitingAsMissing 会清空一级缓存，其后全部实体需要重新查询，避免托管态与批量更新不一致
        int turnedMissing = itemRepository.markWaitingAsMissing(batchId);
        StocktakeBatch batch = batchRepository.findById(batchId).orElseThrow();
        recomputeCounters(batchId);
        batch = batchRepository.findById(batchId).orElseThrow();
        if (batch.getPendingCount() > 0) {
            throw new RuntimeException("仍有 " + batch.getPendingCount() + " 条待处理差异，请全部确认或忽略后再结束盘点"
                    + (turnedMissing > 0 ? "（其中 " + turnedMissing + " 件应盘未盘已自动标记为缺失）" : ""));
        }
        batch.setStatus(StocktakeBatch.STATUS_COMPLETED);
        batch.setFinishTime(LocalDateTime.now());
        batch = batchRepository.save(batch);
        enrichBatch(batch);
        return batch;
    }

    /** 重新打开已完成批次补盘（差异需重新闭环后才能再次结束） */
    @Transactional
    public StocktakeBatch reopenBatch(Long batchId) {
        StocktakeBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("盘点批次不存在"));
        if (!StocktakeBatch.STATUS_COMPLETED.equals(batch.getStatus())) {
            throw new RuntimeException("仅已完成的批次可重新打开");
        }
        batch.setStatus(StocktakeBatch.STATUS_COUNTING);
        batch.setFinishTime(null);
        batch = batchRepository.save(batch);
        recomputeCounters(batchId);
        enrichBatch(batch);
        return batch;
    }

    // ------------------------------------------------------------------
    // 逐项录入与差异处理
    // ------------------------------------------------------------------

    public Page<StocktakeItem> queryItems(Long batchId, StocktakeItemQueryDTO query) {
        getBatch(batchId);
        Pageable pageable = PageRequest.of(
                Math.max(query.getPage() == null ? 1 : query.getPage(), 1) - 1,
                query.getSize() == null ? 10 : query.getSize());
        String blockCode = query.getBlockCode() == null || query.getBlockCode().isBlank()
                ? null : query.getBlockCode().trim();
        Page<StocktakeItem> page = itemRepository.searchItems(
                batchId, query.getDiscrepancyType(), query.getStatus(), blockCode, pageable);
        page.getContent().forEach(this::enrichItem);
        return page;
    }

    /**
     * 逐项录入实物盘点结果，系统自动比对当前绑定/现场产线并标记差异：
     * 未盘到的应盘挡块重复录入 → 重复盘点；账外挡块 → 盘盈；绑定/现场不符 → 错线；
     * 实物损坏/报废标记对应差异；在途待确认移交单给出追溯提示。
     */
    @Transactional
    public StocktakeItem countItem(Long batchId, StocktakeCountDTO dto) {
        StocktakeBatch batch = getCountingBatch(batchId);
        if (dto == null || dto.getBlockCode() == null || dto.getBlockCode().isBlank()) {
            throw new RuntimeException("请录入挡块编号");
        }
        String physicalStatus = normalizePhysicalStatus(dto.getPhysicalStatus());
        Long siteLineId = dto.getSiteLineId() != null ? dto.getSiteLineId() : batch.getLineId();
        if (productionLineService.getById(siteLineId) == null) {
            throw new RuntimeException("现场产线不存在");
        }
        if (dto.getOperator() == null || dto.getOperator().isBlank()) {
            throw new RuntimeException("请填写盘点人");
        }
        String operator = dto.getOperator().trim();

        BufferBlock block = blockRepository.findByBlockCode(dto.getBlockCode().trim()).orElse(null);
        if (block == null) {
            throw new RuntimeException("挡块编号 " + dto.getBlockCode().trim() + " 不在挡块档案中，无法比对绑定关系，请先建档或核对编号");
        }

        StocktakeItem item = itemRepository.findByBatchIdAndBlockId(batchId, block.getId()).orElse(null);
        boolean repeated = item != null && Integer.valueOf(1).equals(item.getIsCounted());

        BlockLineBinding currentBinding = bindingRepository.findByBlockIdAndIsCurrent(block.getId(), 1).orElse(null);
        Long boundLineId = currentBinding != null ? currentBinding.getLineId() : null;

        if (item == null) {
            item = new StocktakeItem();
            item.setBatchId(batchId);
            item.setBlockId(block.getId());
            item.setBlockCode(block.getBlockCode());
            item.setExpectedLineId(batch.getLineId());
            item.setRepeatCount(1);
            // 仅账外/未绑定挡块算盘盈；盘点开始后才绑定到本产线（不在快照内）按正常盘到，
            // 绑定在其他产线则由下方差异判定为错线，避免误报盘盈
            item.setIsExtra(boundLineId == null ? 1 : 0);
        } else {
            item.setRepeatCount((item.getRepeatCount() == null ? 0 : item.getRepeatCount()) + 1);
        }

        item.setBoundLineId(boundLineId);
        item.setSiteLineId(siteLineId);
        item.setIsCounted(1);
        item.setPhysicalStatus(physicalStatus);
        item.setCountOperator(operator);
        item.setCountTime(LocalDateTime.now());
        item.setCountRemark(dto.getRemark());

        item.setDiscrepancyType(resolveDiscrepancyType(item, boundLineId, siteLineId, physicalStatus, repeated));
        item.setDiscrepancyStatus(StocktakeItem.DIFF_NONE.equals(item.getDiscrepancyType()) ? null : StocktakeItem.STATUS_PENDING);
        // 重新盘到/录入时旧的处理结论失效，需要重新闭环
        item.setHandleOperator(null);
        item.setHandleNote(null);
        item.setHandleTime(null);

        item = itemRepository.save(item);
        recomputeCounters(batchId);
        enrichItem(item);
        return item;
    }

    /**
     * 差异确认/忽略。允许把“待盘”的应盘行直接确认为缺失（现场确认找不到）。
     */
    @Transactional
    public StocktakeItem handleItem(Long batchId, Long itemId, StocktakeHandleDTO dto) {
        getCountingBatch(batchId);
        if (dto == null || dto.getOperator() == null || dto.getOperator().isBlank()) {
            throw new RuntimeException("请填写处理人");
        }
        if (dto.getHandleNote() == null || dto.getHandleNote().isBlank()) {
            throw new RuntimeException("请填写处理说明/备注");
        }
        String action = dto.getAction();
        boolean confirm = "CONFIRM".equals(action);
        boolean ignore = "IGNORE".equals(action);
        if (!confirm && !ignore) {
            throw new RuntimeException("不支持的差异处理动作");
        }

        StocktakeItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("盘点明细不存在"));
        if (!item.getBatchId().equals(batchId)) {
            throw new RuntimeException("明细不属于当前盘点批次");
        }

        if (Integer.valueOf(0).equals(item.getIsCounted())
                && StocktakeItem.DIFF_NONE.equals(item.getDiscrepancyType())) {
            // 待盘行：只允许“确认缺失”
            if (!confirm) {
                throw new RuntimeException("待盘挡块只能确认为“缺失”差异，请先逐项录入或确认缺失");
            }
            item.setDiscrepancyType(StocktakeItem.DIFF_MISSING);
        } else if (StocktakeItem.DIFF_NONE.equals(item.getDiscrepancyType())) {
            throw new RuntimeException("该明细无差异，无需处理");
        } else if (StocktakeItem.STATUS_CONFIRMED.equals(item.getDiscrepancyStatus())
                || StocktakeItem.STATUS_IGNORED.equals(item.getDiscrepancyStatus())) {
            throw new RuntimeException("该差异已处理，如需调整请重新录入盘点结果");
        }

        item.setDiscrepancyStatus(confirm ? StocktakeItem.STATUS_CONFIRMED : StocktakeItem.STATUS_IGNORED);
        item.setHandleOperator(dto.getOperator().trim());
        item.setHandleNote(dto.getHandleNote().trim());
        item.setHandleTime(LocalDateTime.now());
        item = itemRepository.save(item);
        recomputeCounters(batchId);
        enrichItem(item);
        return item;
    }

    /** 删除盘盈误录记录（仅盘盈行、盘点中可删；应盘快照行不可删除） */
    @Transactional
    public void deleteExtraItem(Long batchId, Long itemId) {
        getCountingBatch(batchId);
        StocktakeItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("盘点明细不存在"));
        if (!item.getBatchId().equals(batchId) || !Integer.valueOf(1).equals(item.getIsExtra())) {
            throw new RuntimeException("仅盘盈录入记录可删除");
        }
        itemRepository.delete(item);
        recomputeCounters(batchId);
    }

    // ------------------------------------------------------------------
    // 追溯与导出
    // ------------------------------------------------------------------

    public StocktakeTraceVO getTrace(Long batchId, Long itemId) {
        getBatch(batchId);
        StocktakeItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("盘点明细不存在"));
        if (!item.getBatchId().equals(batchId)) {
            throw new RuntimeException("明细不属于当前盘点批次");
        }
        enrichItem(item);

        StocktakeTraceVO vo = new StocktakeTraceVO();
        vo.setItem(item);
        if (item.getBlockId() != null) {
            List<BlockLineBinding> bindings = new ArrayList<>();
            for (BlockLineBinding binding : bindingRepository.findByBlockIdOrderByBindTimeDesc(item.getBlockId())) {
                ProductionLine line = productionLineService.getById(binding.getLineId());
                if (line != null) {
                    binding.setLineCode(line.getLineCode());
                    binding.setLineName(line.getLineName());
                }
                binding.setBlockCode(item.getBlockCode());
                bindings.add(binding);
            }
            vo.setBindings(bindings);

            List<BlockTransfer> transfers = transferRepository
                    .findByBlockIdOrderByTransferDateDesc(item.getBlockId());
            transfers.forEach(this::enrichTransfer);
            vo.setTransfers(transfers);
            if (!transfers.isEmpty()) {
                BlockTransfer latest = transfers.get(0);
                vo.setLatestTransfer(latest);
                vo.setFlowRecords(flowRecordRepository
                        .findByTransferIdOrderByCreateTimeAscIdAsc(latest.getId()));
            } else {
                vo.setFlowRecords(List.of());
            }
        } else {
            vo.setBindings(List.of());
            vo.setTransfers(List.of());
            vo.setFlowRecords(List.of());
        }
        return vo;
    }

    /** 导出批次全部明细（含中文枚举），由控制器按 CSV 落盘 */
    public List<StocktakeItem> listForExport(Long batchId) {
        getBatch(batchId);
        List<StocktakeItem> items = itemRepository.findByBatchIdOrderByIsExtraAscIsCountedAscIdAsc(batchId);
        items.forEach(this::enrichItem);
        return items;
    }

    /** 导出列序，供控制器与前端（如有需要）统一使用 */
    public static String[] exportHeaders() {
        return new String[] {
                "盘点批次号", "盘点日期", "盘点产线", "挡块编号", "适配机型", "厚度(mm)", "规格模板",
                "记录类型", "应盘产线", "当前绑定产线", "现场产线", "实物状态", "重复盘点次数",
                "差异类型", "处理状态", "盘点人", "盘点时间", "盘点备注",
                "处理人", "处理说明", "处理时间", "最近移交单", "最近移交状态", "最近移交日期", "移交追溯提示"
        };
    }

    public List<String> exportRow(StocktakeItem i, StocktakeBatch batch) {
        List<String> row = new ArrayList<>();
        row.add(batch.getBatchNo());
        row.add(String.valueOf(batch.getStocktakeDate()));
        row.add(batch.getLineName());
        row.add(i.getBlockCode());
        row.add(i.getAdapterModel());
        row.add(i.getThickness() == null ? "" : i.getThickness().toPlainString());
        row.add(i.getSpecTemplate());
        row.add(Integer.valueOf(1).equals(i.getIsExtra()) ? "盘盈" : "应盘");
        row.add(i.getExpectedLineName());
        row.add(i.getBoundLineName());
        row.add(i.getSiteLineName());
        row.add(i.getPhysicalStatusText());
        row.add(String.valueOf(i.getRepeatCount() == null ? 0 : i.getRepeatCount()));
        row.add(i.getDiscrepancyTypeText());
        row.add(i.getEffectiveStatusText());
        row.add(i.getCountOperator());
        row.add(i.getCountTime() == null ? "" : i.getCountTime().toString().replace('T', ' '));
        row.add(i.getCountRemark());
        row.add(i.getHandleOperator());
        row.add(i.getHandleNote());
        row.add(i.getHandleTime() == null ? "" : i.getHandleTime().toString().replace('T', ' '));
        row.add(i.getLastTransferNo());
        row.add(i.getLastTransferStatus() == null ? "" : BlockTransferService.statusText(i.getLastTransferStatus()));
        row.add(i.getLastTransferDate());
        row.add(i.getTransferHint());
        return row;
    }

    // ------------------------------------------------------------------
    // 差异计算与统计
    // ------------------------------------------------------------------

    private String normalizePhysicalStatus(String status) {
        if (status == null || status.isBlank()) {
            return StocktakeItem.PHYSICAL_NORMAL;
        }
        return switch (status.trim().toUpperCase()) {
            case StocktakeItem.PHYSICAL_NORMAL, StocktakeItem.PHYSICAL_DAMAGED, StocktakeItem.PHYSICAL_SCRAPPED ->
                    status.trim().toUpperCase();
            default -> throw new RuntimeException("不支持的实物状态：" + status);
        };
    }

    /**
     * 差异类型判定优先级：账外盘盈 &gt; 错线 &gt; 损坏 &gt; 报废 &gt; 重复盘点 &gt; 无差异。
     * 即实物/绑定类实质差异优先于重复录入提示：同一挡块在错线或损坏状态下重复录入时，
     * 仍保留更严重的差异类型，重复次数在 repeatCount 上累计可见。
     */
    private String resolveDiscrepancyType(StocktakeItem item, Long boundLineId, Long siteLineId,
                                          String physicalStatus, boolean repeated) {
        boolean unbound = boundLineId == null;
        boolean boundToOtherLine = boundLineId != null && !boundLineId.equals(item.getExpectedLineId());
        boolean siteMismatch = siteLineId != null && !siteLineId.equals(item.getExpectedLineId());

        if (unbound) {
            return StocktakeItem.DIFF_EXTRA;
        }
        if (boundToOtherLine || siteMismatch) {
            return StocktakeItem.DIFF_WRONG_LINE;
        }
        if (StocktakeItem.PHYSICAL_DAMAGED.equals(physicalStatus)) {
            return StocktakeItem.DIFF_DAMAGED;
        }
        if (StocktakeItem.PHYSICAL_SCRAPPED.equals(physicalStatus)) {
            return StocktakeItem.DIFF_SCRAPPED;
        }
        if (repeated) {
            return StocktakeItem.DIFF_DUPLICATE;
        }
        return StocktakeItem.DIFF_NONE;
    }

    /**
     * 重算批次冗余统计（列表展示与闭环校验共用同一口径）：
     * totalCount-应盘数；countedCount-去重已盘/盘盈数；
     * discrepancyCount-差异数；pendingCount-待处理差异数。
     * 在调用方事务内执行，保证录入/处理与统计同事务落库。
     */
    private void recomputeCounters(Long batchId) {
        StocktakeBatch batch = batchRepository.findById(batchId).orElseThrow();
        List<StocktakeItem> items = itemRepository.findByBatchIdOrderByIsExtraAscIsCountedAscIdAsc(batchId);

        int total = 0;
        int counted = 0;
        int discrepancy = 0;
        int pending = 0;
        for (StocktakeItem item : items) {
            if (Integer.valueOf(0).equals(item.getIsExtra())) {
                total++;
            }
            if (Integer.valueOf(1).equals(item.getIsCounted())) {
                counted++;
            }
            boolean waiting = Integer.valueOf(0).equals(item.getIsCounted())
                    && StocktakeItem.DIFF_NONE.equals(item.getDiscrepancyType());
            if (!waiting && !StocktakeItem.DIFF_NONE.equals(item.getDiscrepancyType())) {
                discrepancy++;
                if (StocktakeItem.STATUS_PENDING.equals(item.getDiscrepancyStatus())) {
                    pending++;
                }
            }
        }
        batch.setTotalCount(total);
        batch.setCountedCount(counted);
        batch.setDiscrepancyCount(discrepancy);
        batch.setPendingCount(pending);
        batchRepository.save(batch);
    }

    private StocktakeBatch getCountingBatch(Long batchId) {
        StocktakeBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("盘点批次不存在"));
        if (!StocktakeBatch.STATUS_COUNTING.equals(batch.getStatus())) {
            throw new RuntimeException("盘点批次已完成封账，请重新打开后再操作");
        }
        return batch;
    }

    // ------------------------------------------------------------------
    // 富化
    // ------------------------------------------------------------------

    private void enrichBatch(StocktakeBatch batch) {
        ProductionLine line = productionLineService.getById(batch.getLineId());
        if (line != null) {
            batch.setLineCode(line.getLineCode());
            batch.setLineName(line.getLineName());
        }
    }

    private void enrichTransfer(BlockTransfer transfer) {
        BufferBlock block = blockRepository.findById(transfer.getBlockId()).orElse(null);
        if (block != null) {
            transfer.setBlockCode(block.getBlockCode());
            transfer.setAdapterModel(block.getAdapterModel());
            transfer.setThickness(block.getThickness());
            transfer.setSpecTemplate(block.getSpecTemplate());
        }
        ProductionLine fromLine = productionLineService.getById(transfer.getFromLineId());
        if (fromLine != null) {
            transfer.setFromLineName(fromLine.getLineName());
        }
        ProductionLine toLine = productionLineService.getById(transfer.getToLineId());
        if (toLine != null) {
            transfer.setToLineName(toLine.getLineName());
        }
    }

    private void enrichItem(StocktakeItem item) {
        if (item.getBlockId() != null) {
            BufferBlock block = blockRepository.findById(item.getBlockId()).orElse(null);
            if (block != null) {
                item.setAdapterModel(block.getAdapterModel());
                item.setThickness(block.getThickness());
                item.setSpecTemplate(block.getSpecTemplate());
                if (item.getBlockCode() == null) {
                    item.setBlockCode(block.getBlockCode());
                }
            }
        }
        item.setExpectedLineName(lineName(item.getExpectedLineId()));
        item.setBoundLineName(lineName(item.getBoundLineId()));
        item.setSiteLineName(lineName(item.getSiteLineId()));

        boolean waiting = Integer.valueOf(0).equals(item.getIsCounted())
                && StocktakeItem.DIFF_NONE.equals(item.getDiscrepancyType());
        String effectiveStatus;
        if (waiting) {
            effectiveStatus = "WAITING";
        } else if (StocktakeItem.DIFF_NONE.equals(item.getDiscrepancyType())) {
            effectiveStatus = "NONE";
        } else {
            effectiveStatus = item.getDiscrepancyStatus() == null ? StocktakeItem.STATUS_PENDING : item.getDiscrepancyStatus();
        }
        item.setEffectiveStatus(effectiveStatus);
        item.setDiscrepancyTypeText(discrepancyTypeText(waiting ? StocktakeItem.DIFF_NONE : item.getDiscrepancyType()));
        item.setEffectiveStatusText(statusText(effectiveStatus));
        item.setPhysicalStatusText(physicalStatusText(item.getPhysicalStatus(), waiting));

        if (item.getBlockId() != null) {
            List<BlockTransfer> transfers = transferRepository
                    .findByBlockIdOrderByTransferDateDesc(item.getBlockId());
            if (!transfers.isEmpty()) {
                BlockTransfer latest = transfers.get(0);
                item.setLastTransferNo(latest.getTransferNo());
                item.setLastTransferStatus(latest.getStatus());
                item.setLastTransferDate(String.valueOf(latest.getTransferDate()));
                item.setTransferHint(buildTransferHint(latest, item));
            }
        }
    }

    private String buildTransferHint(BlockTransfer latest, StocktakeItem item) {
        String statusText = BlockTransferService.statusText(latest.getStatus());
        String base = "最近移交单 " + latest.getTransferNo() + "（" + statusText + "，"
                + latest.getTransferDate() + "）";
        if (BlockTransfer.STATUS_PENDING.equals(latest.getStatus())) {
            return base + "：该挡块存在待确认移交，绑定尚未更新，请与接收方核实实物去向";
        }
        if (BlockTransfer.STATUS_REJECTED.equals(latest.getStatus())) {
            return base + "：移交曾被驳回，挡块保留原归属，请核对现场是否仍按旧归属存放";
        }
        if (StocktakeItem.DIFF_WRONG_LINE.equals(item.getDiscrepancyType())) {
            return base + "：绑定/现场产线不一致，请结合最近移交记录核对";
        }
        return base;
    }

    private String lineName(Long lineId) {
        if (lineId == null) {
            return null;
        }
        ProductionLine line = productionLineService.getById(lineId);
        return line != null ? line.getLineName() : null;
    }

    public static String physicalStatusText(String status, boolean waiting) {
        if (waiting || status == null) {
            return "待盘";
        }
        return switch (status) {
            case StocktakeItem.PHYSICAL_NORMAL -> "正常";
            case StocktakeItem.PHYSICAL_DAMAGED -> "损坏";
            case StocktakeItem.PHYSICAL_SCRAPPED -> "报废";
            default -> status;
        };
    }

    public static String discrepancyTypeText(String type) {
        if (type == null) {
            return "无差异";
        }
        return switch (type) {
            case StocktakeItem.DIFF_NONE -> "无差异";
            case StocktakeItem.DIFF_MISSING -> "缺失";
            case StocktakeItem.DIFF_WRONG_LINE -> "错线";
            case StocktakeItem.DIFF_DUPLICATE -> "重复盘点";
            case StocktakeItem.DIFF_EXTRA -> "盘盈";
            case StocktakeItem.DIFF_DAMAGED -> "损坏";
            case StocktakeItem.DIFF_SCRAPPED -> "报废";
            default -> type;
        };
    }

    public static String statusText(String status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case "WAITING" -> "待盘";
            case "NONE" -> "无差异";
            case StocktakeItem.STATUS_PENDING -> "待处理";
            case StocktakeItem.STATUS_CONFIRMED -> "已确认";
            case StocktakeItem.STATUS_IGNORED -> "已忽略";
            default -> status;
        };
    }

    public static String batchStatusText(String status) {
        if (StocktakeBatch.STATUS_COUNTING.equals(status)) {
            return "盘点中";
        }
        if (StocktakeBatch.STATUS_COMPLETED.equals(status)) {
            return "已完成";
        }
        return status;
    }
}
