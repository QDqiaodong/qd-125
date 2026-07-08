package com.bufferblock.service;

import com.bufferblock.dto.TransferCreateDTO;
import com.bufferblock.dto.TransferQueryDTO;
import com.bufferblock.entity.BlockTransfer;
import com.bufferblock.entity.BufferBlock;
import com.bufferblock.entity.ProductionLine;
import com.bufferblock.repository.BlockTransferRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class BlockTransferService {

    private final BlockTransferRepository blockTransferRepository;
    private final BufferBlockService bufferBlockService;
    private final ProductionLineService productionLineService;

    private final AtomicInteger dailyCounter = new AtomicInteger(0);

    public BlockTransferService(BlockTransferRepository blockTransferRepository,
                                BufferBlockService bufferBlockService,
                                ProductionLineService productionLineService) {
        this.blockTransferRepository = blockTransferRepository;
        this.bufferBlockService = bufferBlockService;
        this.productionLineService = productionLineService;
    }

    public Page<BlockTransfer> queryTransfers(TransferQueryDTO query) {
        Pageable pageable = PageRequest.of(query.getPage() - 1, query.getSize());
        Page<BlockTransfer> page = blockTransferRepository.findByConditions(
                query.getStartDate(),
                query.getEndDate(),
                query.getFromLineId(),
                query.getToLineId(),
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
        transfer.setPrintCount(0);
        transfer = blockTransferRepository.save(transfer);

        bufferBlockService.bindBlockToLine(dto.getBlockId(), dto.getToLineId(), dto.getTransferOperator(), 2);

        enrichTransfer(transfer);
        return transfer;
    }

    @Transactional
    public BlockTransfer markPrinted(Long id) {
        BlockTransfer transfer = blockTransferRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("移交记录不存在"));
        transfer.setPrintCount(transfer.getPrintCount() + 1);
        transfer.setLastPrintTime(LocalDateTime.now());
        transfer = blockTransferRepository.save(transfer);
        enrichTransfer(transfer);
        return transfer;
    }

    public List<BlockTransfer> getTransfersByBlockId(Long blockId) {
        List<BlockTransfer> transfers = blockTransferRepository.findByBlockIdOrderByTransferDateDesc(blockId);
        transfers.forEach(this::enrichTransfer);
        return transfers;
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
}
