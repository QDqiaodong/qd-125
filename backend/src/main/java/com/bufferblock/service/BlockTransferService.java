package com.bufferblock.service;

import com.bufferblock.dto.TransferCreateDTO;
import com.bufferblock.dto.TransferHandleDTO;
import com.bufferblock.dto.TransferQueryDTO;
import com.bufferblock.dto.CalibrationStatusVO;
import com.bufferblock.entity.BlockTransfer;
import com.bufferblock.entity.BufferBlock;
import com.bufferblock.entity.ProductionLine;
import com.bufferblock.entity.TransferFlowRecord;
import com.bufferblock.repository.BlockTransferRepository;
import com.bufferblock.repository.TransferFlowRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class BlockTransferService {

    /** 等待时长排序：最长优先 */
    public static final String WAIT_SORT_LONGEST_FIRST = "LONGEST_FIRST";
    /** 等待时长排序：最短优先 */
    public static final String WAIT_SORT_SHORTEST_FIRST = "SHORTEST_FIRST";
    /** 积压标记阈值：待确认单等待达到 24 小时视为压得太久，列表与导出统一口径 */
    public static final long LONG_WAIT_THRESHOLD_MINUTES = 24 * 60;

    private final BlockTransferRepository blockTransferRepository;
    private final TransferFlowRecordRepository transferFlowRecordRepository;
    private final BufferBlockService bufferBlockService;
    private final ProductionLineService productionLineService;
    private final TransferNumberService transferNumberService;
    private final CalibrationService calibrationService;

    public BlockTransferService(BlockTransferRepository blockTransferRepository,
                                TransferFlowRecordRepository transferFlowRecordRepository,
                                BufferBlockService bufferBlockService,
                                ProductionLineService productionLineService,
                                TransferNumberService transferNumberService,
                                CalibrationService calibrationService) {
        this.blockTransferRepository = blockTransferRepository;
        this.transferFlowRecordRepository = transferFlowRecordRepository;
        this.bufferBlockService = bufferBlockService;
        this.productionLineService = productionLineService;
        this.transferNumberService = transferNumberService;
        this.calibrationService = calibrationService;
    }

    public Page<BlockTransfer> queryTransfers(TransferQueryDTO query) {
        Pageable pageable = PageRequest.of(query.getPage() - 1, query.getSize());
        Page<BlockTransfer> page = queryForConfirm(query, pageable);
        page.getContent().forEach(this::enrichTransfer);
        return page;
    }

    /**
     * 按当前筛选与等待时长排序导出全部结果（不分页），顺序与列表页保持一致
     */
    public List<BlockTransfer> listForExport(TransferQueryDTO query) {
        List<BlockTransfer> transfers = queryForConfirm(query, Pageable.unpaged()).getContent();
        transfers.forEach(this::enrichTransfer);
        return transfers;
    }

    /**
     * 移交确认列表统一查询入口：指定等待时长排序时按等待时长排（待确认为实时等待，
     * 已办理定格在办理时刻），否则保持默认的移交日期/登记时间倒序
     */
    private Page<BlockTransfer> queryForConfirm(TransferQueryDTO query, Pageable pageable) {
        String waitSort = query.getWaitSort();
        if (WAIT_SORT_LONGEST_FIRST.equals(waitSort)) {
            return blockTransferRepository.findForConfirmWaitLongestFirst(
                    query.getStartDate(), query.getEndDate(), query.getFromLineId(), query.getToLineId(),
                    query.getLineId(), query.getStatus(), query.getBlockCode(), pageable);
        }
        if (WAIT_SORT_SHORTEST_FIRST.equals(waitSort)) {
            return blockTransferRepository.findForConfirmWaitShortestFirst(
                    query.getStartDate(), query.getEndDate(), query.getFromLineId(), query.getToLineId(),
                    query.getLineId(), query.getStatus(), query.getBlockCode(), pageable);
        }
        return blockTransferRepository.findForConfirm(
                query.getStartDate(), query.getEndDate(), query.getFromLineId(), query.getToLineId(),
                query.getLineId(), query.getStatus(), query.getBlockCode(), pageable);
    }

    public List<BlockTransfer> getTransfersByDateRange(LocalDate startDate, LocalDate endDate) {
        List<BlockTransfer> transfers = blockTransferRepository.findByDateRange(startDate, endDate);
        transfers.forEach(this::enrichTransfer);
        return transfers;
    }

    public BlockTransfer getById(Long id) {
        BlockTransfer transfer = blockTransferRepository.findById(id).orElse(null);
        if (transfer != null) {
            enrichTransfer(transfer);
        }
        return transfer;
    }

    @Transactional
    public BlockTransfer createTransfer(TransferCreateDTO dto) {
        validateCreateRequest(dto);
        LocalDate transferDate = dto.getTransferDate() != null ? dto.getTransferDate() : LocalDate.now();

        BufferBlock block = bufferBlockService.lockEntityById(dto.getBlockId());
        if (block == null) {
            throw new RuntimeException("挡块不存在");
        }

        var currentBinding = bufferBlockService.getCurrentBinding(dto.getBlockId());
        if (currentBinding == null || !currentBinding.getLineId().equals(dto.getFromLineId())) {
            throw new RuntimeException("挡块当前不在指定的移出产线");
        }

        // 挂起待修或校准逾期的挡块不得办理移交（不登记、不改绑定），返回明确的逾期/挂起说明
        calibrationService.assertTransferable(dto.getBlockId());

        List<BlockTransfer> pendingTransfers =
                blockTransferRepository.findByBlockIdAndStatusOrderByCreateTimeDesc(
                        dto.getBlockId(), BlockTransfer.STATUS_PENDING);
        if (!pendingTransfers.isEmpty()) {
            throw new RuntimeException("该挡块已存在待确认的移交单（" + pendingTransfers.get(0).getTransferNo() + "），请等待接收方处理");
        }

        BlockTransfer transfer = new BlockTransfer();
        transfer.setTransferNo(transferNumberService.nextTransferNo(transferDate));
        transfer.setBlockId(dto.getBlockId());
        transfer.setFromLineId(dto.getFromLineId());
        transfer.setToLineId(dto.getToLineId());
        transfer.setTransferDate(transferDate);
        transfer.setTransferReason(dto.getTransferReason());
        transfer.setTransferOperator(dto.getTransferOperator().trim());
        transfer.setReceiveOperator(dto.getReceiveOperator());
        transfer.setRemark(dto.getRemark());
        transfer.setStatus(BlockTransfer.STATUS_PENDING);
        transfer.setPrintCount(0);
        transfer.setReceiptPrintCount(0);
        transfer = blockTransferRepository.saveAndFlush(transfer);

        // 登记后进入待确认状态，不立即变更产线绑定
        recordFlow(transfer.getId(), TransferFlowRecord.ACTION_REGISTER, null,
                BlockTransfer.STATUS_PENDING, transfer.getTransferOperator(),
                "移交登记，等待接收方确认");

        enrichTransfer(transfer);
        return transfer;
    }

    private void validateCreateRequest(TransferCreateDTO dto) {
        if (dto == null) {
            throw new RuntimeException("移交登记参数不能为空");
        }
        if (dto.getBlockId() == null) {
            throw new RuntimeException("请选择挡块");
        }
        if (dto.getFromLineId() == null || dto.getToLineId() == null) {
            throw new RuntimeException("请选择移出产线和移入产线");
        }
        if (dto.getFromLineId().equals(dto.getToLineId())) {
            throw new RuntimeException("移出产线和移入产线不能相同");
        }
        if (dto.getTransferOperator() == null || dto.getTransferOperator().isBlank()) {
            throw new RuntimeException("请输入移交人");
        }
    }

    /**
     * 接收方确认接收：仅确认后才更新当前产线绑定
     */
    @Transactional
    public BlockTransfer confirmTransfer(Long id, TransferHandleDTO dto) {
        BlockTransfer transfer = getPendingTransfer(id);

        // 确认即变更绑定的最后关口：登记后校准逾期或挂起的，同样阻断，绑定保持不变
        calibrationService.assertTransferable(transfer.getBlockId());

        String operator = resolveReceiveOperator(transfer, dto);
        transfer.setStatus(BlockTransfer.STATUS_CONFIRMED);
        transfer.setHandleTime(LocalDateTime.now());
        transfer.setHandleNote(dto.getHandleNote());
        transfer.setReceiveOperator(operator);
        transfer = blockTransferRepository.save(transfer);

        // 确认后更新挡块当前产线绑定
        bufferBlockService.bindBlockToLine(transfer.getBlockId(), transfer.getToLineId(),
                operator, 2);

        recordFlow(transfer.getId(), TransferFlowRecord.ACTION_CONFIRM,
                BlockTransfer.STATUS_PENDING, BlockTransfer.STATUS_CONFIRMED,
                operator, dto.getHandleNote());

        enrichTransfer(transfer);
        return transfer;
    }

    /**
     * 接收方驳回：保留原归属，不变更产线绑定
     */
    @Transactional
    public BlockTransfer rejectTransfer(Long id, TransferHandleDTO dto) {
        BlockTransfer transfer = getPendingTransfer(id);

        String operator = resolveReceiveOperator(transfer, dto);
        transfer.setStatus(BlockTransfer.STATUS_REJECTED);
        transfer.setHandleTime(LocalDateTime.now());
        transfer.setHandleNote(dto.getHandleNote());
        transfer.setReceiveOperator(operator);
        transfer = blockTransferRepository.save(transfer);

        recordFlow(transfer.getId(), TransferFlowRecord.ACTION_REJECT,
                BlockTransfer.STATUS_PENDING, BlockTransfer.STATUS_REJECTED,
                operator, dto.getHandleNote());

        enrichTransfer(transfer);
        return transfer;
    }

    private String resolveReceiveOperator(BlockTransfer transfer, TransferHandleDTO dto) {
        String operator = dto.getReceiveOperator() != null && !dto.getReceiveOperator().isBlank()
                ? dto.getReceiveOperator().trim()
                : transfer.getReceiveOperator();
        if (operator == null || operator.isBlank()) {
            throw new RuntimeException("请填写接收方处理人");
        }
        if (dto.getHandleNote() == null || dto.getHandleNote().isBlank()) {
            throw new RuntimeException("请填写处理说明");
        }
        return operator;
    }

    @Transactional
    public BlockTransfer markPrinted(Long id) {
        BlockTransfer transfer = blockTransferRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("移交记录不存在"));
        transfer.setPrintCount((transfer.getPrintCount() == null ? 0 : transfer.getPrintCount()) + 1);
        transfer.setLastPrintTime(LocalDateTime.now());
        transfer = blockTransferRepository.save(transfer);

        recordFlow(transfer.getId(), TransferFlowRecord.ACTION_PRINT_ORDER,
                transfer.getStatus(), transfer.getStatus(),
                transfer.getTransferOperator(), "打印移交单");

        enrichTransfer(transfer);
        return transfer;
    }

    /**
     * 打印确认回执（仅已确认单据可打印）
     */
    @Transactional
    public BlockTransfer markReceiptPrinted(Long id) {
        BlockTransfer transfer = blockTransferRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("移交记录不存在"));
        if (!BlockTransfer.STATUS_CONFIRMED.equals(transfer.getStatus())) {
            throw new RuntimeException("仅已确认的移交单可打印确认回执");
        }
        transfer.setReceiptPrintCount(transfer.getReceiptPrintCount() == null ? 1 : transfer.getReceiptPrintCount() + 1);
        transfer.setLastReceiptPrintTime(LocalDateTime.now());
        transfer = blockTransferRepository.save(transfer);

        recordFlow(transfer.getId(), TransferFlowRecord.ACTION_PRINT_RECEIPT,
                transfer.getStatus(), transfer.getStatus(),
                transfer.getReceiveOperator(), "打印确认回执");

        enrichTransfer(transfer);
        return transfer;
    }

    public List<BlockTransfer> getTransfersByBlockId(Long blockId) {
        List<BlockTransfer> transfers = blockTransferRepository.findByBlockIdOrderByTransferDateDesc(blockId);
        transfers.forEach(this::enrichTransfer);
        return transfers;
    }

    public List<TransferFlowRecord> getFlowRecords(Long transferId) {
        return transferFlowRecordRepository.findByTransferIdOrderByCreateTimeAscIdAsc(transferId);
    }

    private BlockTransfer getPendingTransfer(Long id) {
        BlockTransfer transfer = blockTransferRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("移交记录不存在"));
        if (!BlockTransfer.STATUS_PENDING.equals(transfer.getStatus())) {
            throw new RuntimeException("仅待确认状态的移交单可处理，当前状态：" + statusText(transfer.getStatus()));
        }
        return transfer;
    }

    private void recordFlow(Long transferId, String action, String fromStatus, String toStatus,
                            String operator, String note) {
        transferFlowRecordRepository.saveAndFlush(
                new TransferFlowRecord(transferId, action, fromStatus, toStatus, operator, note));
    }

    private void enrichTransfer(BlockTransfer transfer) {
        BufferBlock block = bufferBlockService.getEntityById(transfer.getBlockId());
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

        if (transfer.getCreateTime() != null) {
            if (BlockTransfer.STATUS_PENDING.equals(transfer.getStatus())) {
                // 待确认单：从登记时刻到当前时刻的实时等待时长
                Duration waiting = Duration.between(transfer.getCreateTime(), LocalDateTime.now());
                transfer.setWaitingDuration(formatDuration(waiting));
                // 压得太久的待确认单给出积压标记，列表与导出共用同一阈值口径
                transfer.setLongWaiting(waiting.toMinutes() >= LONG_WAIT_THRESHOLD_MINUTES);
            } else {
                // 已确认/已驳回：等待时长停在办理时刻，不再随当前时间继续增长
                LocalDateTime endTime = transfer.getHandleTime() != null
                        ? transfer.getHandleTime()
                        : transfer.getUpdateTime();
                transfer.setWaitingDuration(formatDuration(
                        Duration.between(transfer.getCreateTime(), endTime)));
                transfer.setLongWaiting(false);
            }
        } else {
            transfer.setLongWaiting(false);
        }

        CalibrationStatusVO calibrationStatus = calibrationService.statusOf(transfer.getBlockId());
        if (calibrationStatus != null) {
            transfer.setCalibrationStatus(calibrationStatus.getStatus());
            transfer.setOverdueDays(calibrationStatus.getOverdueDays());
            if (calibrationStatus.getNextDueDate() != null) {
                transfer.setNextDueDate(calibrationStatus.getNextDueDate().toString());
            }
        }
    }

    /**
     * 将等待时长格式化为“X天X小时X分钟”
     */
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

    /** 导出列序，与移交确认列表展示口径一致 */
    public static String[] exportHeaders() {
        return new String[] {
                "移交单号", "挡块编号", "适配机型", "厚度(mm)", "规格模板",
                "原归属产线", "目标产线", "移交日期", "状态", "等待时长", "积压标记",
                "登记时间", "处理时间", "移交人", "接收方处理人", "移交原因", "处理说明"
        };
    }

    public List<String> exportRow(BlockTransfer t) {
        List<String> row = new ArrayList<>();
        row.add(t.getTransferNo());
        row.add(t.getBlockCode());
        row.add(t.getAdapterModel());
        row.add(t.getThickness() == null ? "" : t.getThickness().toPlainString());
        row.add(t.getSpecTemplate());
        row.add(t.getFromLineName());
        row.add(t.getToLineName());
        row.add(t.getTransferDate() == null ? "" : t.getTransferDate().toString());
        row.add(statusText(t.getStatus()));
        row.add(t.getWaitingDuration());
        row.add(Boolean.TRUE.equals(t.getLongWaiting()) ? "积压过久" : "");
        row.add(formatDateTime(t.getCreateTime()));
        row.add(formatDateTime(t.getHandleTime()));
        row.add(t.getTransferOperator());
        row.add(t.getReceiveOperator());
        row.add(t.getTransferReason());
        row.add(t.getHandleNote());
        return row;
    }

    private String formatDateTime(LocalDateTime time) {
        return time == null ? "" : time.toString().replace('T', ' ');
    }

    public static String statusText(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case BlockTransfer.STATUS_PENDING -> "待确认";
            case BlockTransfer.STATUS_CONFIRMED -> "已确认";
            case BlockTransfer.STATUS_REJECTED -> "已驳回";
            default -> status;
        };
    }
}
