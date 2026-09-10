package com.bufferblock.service;

import com.bufferblock.dto.TransferCreateDTO;
import com.bufferblock.dto.TransferHandleDTO;
import com.bufferblock.dto.TransferQueryDTO;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class BlockTransferService {

    private final BlockTransferRepository blockTransferRepository;
    private final TransferFlowRecordRepository transferFlowRecordRepository;
    private final BufferBlockService bufferBlockService;
    private final ProductionLineService productionLineService;

    private final AtomicInteger dailyCounter = new AtomicInteger(0);

    public BlockTransferService(BlockTransferRepository blockTransferRepository,
                                TransferFlowRecordRepository transferFlowRecordRepository,
                                BufferBlockService bufferBlockService,
                                ProductionLineService productionLineService) {
        this.blockTransferRepository = blockTransferRepository;
        this.transferFlowRecordRepository = transferFlowRecordRepository;
        this.bufferBlockService = bufferBlockService;
        this.productionLineService = productionLineService;
    }

    public Page<BlockTransfer> queryTransfers(TransferQueryDTO query) {
        Pageable pageable = PageRequest.of(query.getPage() - 1, query.getSize());
        Page<BlockTransfer> page = blockTransferRepository.findForConfirm(
                query.getStartDate(),
                query.getEndDate(),
                query.getFromLineId(),
                query.getToLineId(),
                query.getLineId(),
                query.getStatus(),
                query.getBlockCode(),
                pageable
        );
        page.getContent().forEach(this::enrichTransfer);
        return page;
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
        if (dto.getFromLineId().equals(dto.getToLineId())) {
            throw new RuntimeException("移出产线和移入产线不能相同");
        }

        BufferBlock block = bufferBlockService.getEntityById(dto.getBlockId());
        if (block == null) {
            throw new RuntimeException("挡块不存在");
        }

        var currentBinding = bufferBlockService.getCurrentBinding(dto.getBlockId());
        if (currentBinding == null || !currentBinding.getLineId().equals(dto.getFromLineId())) {
            throw new RuntimeException("挡块当前不在指定的移出产线");
        }

        List<BlockTransfer> pendingTransfers =
                blockTransferRepository.findByBlockIdAndStatusOrderByCreateTimeDesc(
                        dto.getBlockId(), BlockTransfer.STATUS_PENDING);
        if (!pendingTransfers.isEmpty()) {
            throw new RuntimeException("该挡块已存在待确认的移交单（" + pendingTransfers.get(0).getTransferNo() + "），请等待接收方处理");
        }

        BlockTransfer transfer = new BlockTransfer();
        transfer.setTransferNo(generateTransferNo());
        transfer.setBlockId(dto.getBlockId());
        transfer.setFromLineId(dto.getFromLineId());
        transfer.setToLineId(dto.getToLineId());
        transfer.setTransferDate(dto.getTransferDate() != null ? dto.getTransferDate() : LocalDate.now());
        transfer.setTransferReason(dto.getTransferReason());
        transfer.setTransferOperator(dto.getTransferOperator());
        transfer.setReceiveOperator(dto.getReceiveOperator());
        transfer.setRemark(dto.getRemark());
        transfer.setStatus(BlockTransfer.STATUS_PENDING);
        transfer.setPrintCount(0);
        transfer.setReceiptPrintCount(0);
        transfer = blockTransferRepository.save(transfer);

        // 登记后进入待确认状态，不立即变更产线绑定
        recordFlow(transfer.getId(), TransferFlowRecord.ACTION_REGISTER, null,
                BlockTransfer.STATUS_PENDING, dto.getTransferOperator(),
                "移交登记，等待接收方确认");

        enrichTransfer(transfer);
        return transfer;
    }

    /**
     * 接收方确认接收：仅确认后才更新当前产线绑定
     */
    @Transactional
    public BlockTransfer confirmTransfer(Long id, TransferHandleDTO dto) {
        BlockTransfer transfer = getPendingTransfer(id);

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
        transfer.setPrintCount(transfer.getPrintCount() + 1);
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
        transferFlowRecordRepository.save(
                new TransferFlowRecord(transferId, action, fromStatus, toStatus, operator, note));
    }

    private String generateTransferNo() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int seq = dailyCounter.incrementAndGet();
        return String.format("TRF-%s-%03d", dateStr, seq);
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

        if (BlockTransfer.STATUS_PENDING.equals(transfer.getStatus()) && transfer.getCreateTime() != null) {
            transfer.setWaitingDuration(formatDuration(Duration.between(transfer.getCreateTime(), LocalDateTime.now())));
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
