package com.bufferblock.controller;

import com.bufferblock.dto.Result;
import com.bufferblock.dto.TransferCreateDTO;
import com.bufferblock.dto.TransferHandleDTO;
import com.bufferblock.dto.TransferQueryDTO;
import com.bufferblock.entity.BlockTransfer;
import com.bufferblock.entity.TransferFlowRecord;
import com.bufferblock.service.BlockTransferService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    /**
     * 导出当前筛选结果（含等待时长排序与积压标记），带 BOM 的中文 CSV，Excel 直接打开不乱码
     */
    @PostMapping("/export")
    public void export(@RequestBody TransferQueryDTO query, HttpServletResponse response) throws IOException {
        List<BlockTransfer> transfers = blockTransferService.listForExport(query);

        response.setContentType("text/csv; charset=UTF-8");
        String fileName = "移交确认结果_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);

        // UTF-8 BOM，保证 Excel 直接打开中文不乱码
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        StringBuilder sb = new StringBuilder();
        appendCsvLine(sb, List.of(BlockTransferService.exportHeaders()));
        for (BlockTransfer transfer : transfers) {
            appendCsvLine(sb, blockTransferService.exportRow(transfer));
        }
        response.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
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

    private void appendCsvLine(StringBuilder sb, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(csvEscape(values.get(i)));
        }
        sb.append("\r\n");
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
