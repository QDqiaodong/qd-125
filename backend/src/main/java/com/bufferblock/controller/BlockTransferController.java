package com.bufferblock.controller;

import com.bufferblock.dto.Result;
import com.bufferblock.dto.TransferCreateDTO;
import com.bufferblock.dto.TransferHandleDTO;
import com.bufferblock.dto.TransferQueryDTO;
import com.bufferblock.entity.BlockTransfer;
import com.bufferblock.entity.TransferFlowRecord;
import com.bufferblock.service.BlockTransferService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/transfers")
public class BlockTransferController {

    private final BlockTransferService blockTransferService;

    public BlockTransferController(BlockTransferService blockTransferService) {
        this.blockTransferService = blockTransferService;
    }

    @PostMapping("/query")
    public Result<Page<BlockTransfer>> queryTransfers(@RequestBody TransferQueryDTO query) {
        return Result.success(blockTransferService.queryTransfers(query));
    }

    @GetMapping("/range")
    public Result<List<BlockTransfer>> getTransfersByDateRange(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(blockTransferService.getTransfersByDateRange(startDate, endDate));
    }

    @GetMapping("/{id}")
    public Result<BlockTransfer> getById(@PathVariable Long id) {
        return Result.success(blockTransferService.getById(id));
    }

    @PostMapping
    public Result<BlockTransfer> createTransfer(@RequestBody TransferCreateDTO dto) {
        return Result.success(blockTransferService.createTransfer(dto));
    }

    /** 接收方确认接收，确认后更新产线绑定 */
    @PostMapping("/{id}/confirm")
    public Result<BlockTransfer> confirmTransfer(@PathVariable Long id, @RequestBody TransferHandleDTO dto) {
        return Result.success(blockTransferService.confirmTransfer(id, dto));
    }

    /** 接收方驳回，保留原归属 */
    @PostMapping("/{id}/reject")
    public Result<BlockTransfer> rejectTransfer(@PathVariable Long id, @RequestBody TransferHandleDTO dto) {
        return Result.success(blockTransferService.rejectTransfer(id, dto));
    }

    /** 完整流转记录 */
    @GetMapping("/{id}/flow-records")
    public Result<List<TransferFlowRecord>> getFlowRecords(@PathVariable Long id) {
        return Result.success(blockTransferService.getFlowRecords(id));
    }

    @PostMapping("/{id}/print")
    public Result<BlockTransfer> markPrinted(@PathVariable Long id) {
        return Result.success(blockTransferService.markPrinted(id));
    }

    /** 打印确认回执 */
    @PostMapping("/{id}/receipt-print")
    public Result<BlockTransfer> markReceiptPrinted(@PathVariable Long id) {
        return Result.success(blockTransferService.markReceiptPrinted(id));
    }

    @GetMapping("/block/{blockId}")
    public Result<List<BlockTransfer>> getTransfersByBlockId(@PathVariable Long blockId) {
        return Result.success(blockTransferService.getTransfersByBlockId(blockId));
    }
}
