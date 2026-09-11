package com.bufferblock.controller;

import com.bufferblock.dto.Result;
import com.bufferblock.dto.StocktakeBatchCreateDTO;
import com.bufferblock.dto.StocktakeBatchQueryDTO;
import com.bufferblock.dto.StocktakeCountDTO;
import com.bufferblock.dto.StocktakeHandleDTO;
import com.bufferblock.dto.StocktakeItemQueryDTO;
import com.bufferblock.dto.StocktakeOverviewVO;
import com.bufferblock.dto.StocktakeTraceVO;
import com.bufferblock.entity.StocktakeBatch;
import com.bufferblock.entity.StocktakeItem;
import com.bufferblock.service.StocktakeService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 挡块盘点差异闭环：批次创建、逐项录入、差异标记/处理、筛选、追溯与导出。
 */
@RestController
@RequestMapping("/api/stocktakes")
public class StocktakeController {

    private final StocktakeService stocktakeService;

    public StocktakeController(StocktakeService stocktakeService) {
        this.stocktakeService = stocktakeService;
    }

    // ---------------- 批次 ----------------

    @PostMapping("/query")
    public Result<Page<StocktakeBatch>> queryBatches(@RequestBody StocktakeBatchQueryDTO query) {
        return Result.success(stocktakeService.queryBatches(query));
    }

    @GetMapping("/overview")
    public Result<StocktakeOverviewVO> getOverview() {
        return Result.success(stocktakeService.getOverview());
    }

    @GetMapping("/{batchId}")
    public Result<StocktakeBatch> getBatch(@PathVariable Long batchId) {
        return Result.success(stocktakeService.getBatch(batchId));
    }

    @PostMapping
    public Result<StocktakeBatch> createBatch(@RequestBody StocktakeBatchCreateDTO dto) {
        return Result.success(stocktakeService.createBatch(dto));
    }

    /** 结束盘点：待盘项转缺失，存在待处理差异时拒绝封账 */
    @PostMapping("/{batchId}/finish")
    public Result<StocktakeBatch> finishBatch(@PathVariable Long batchId) {
        return Result.success(stocktakeService.finishBatch(batchId));
    }

    /** 重新打开已完成批次补盘 */
    @PostMapping("/{batchId}/reopen")
    public Result<StocktakeBatch> reopenBatch(@PathVariable Long batchId) {
        return Result.success(stocktakeService.reopenBatch(batchId));
    }

    // ---------------- 明细 / 逐项录入 / 差异处理 ----------------

    @PostMapping("/{batchId}/items/query")
    public Result<Page<StocktakeItem>> queryItems(@PathVariable Long batchId,
                                                  @RequestBody StocktakeItemQueryDTO query) {
        return Result.success(stocktakeService.queryItems(batchId, query));
    }

    /** 逐项录入实物状态与现场产线，系统自动比对并标记差异 */
    @PostMapping("/{batchId}/items/count")
    public Result<StocktakeItem> countItem(@PathVariable Long batchId,
                                           @RequestBody StocktakeCountDTO dto) {
        return Result.success(stocktakeService.countItem(batchId, dto));
    }

    /** 差异确认/忽略（带处理人、处理说明） */
    @PostMapping("/{batchId}/items/{itemId}/handle")
    public Result<StocktakeItem> handleItem(@PathVariable Long batchId,
                                            @PathVariable Long itemId,
                                            @RequestBody StocktakeHandleDTO dto) {
        return Result.success(stocktakeService.handleItem(batchId, itemId, dto));
    }

    /** 删除盘盈误录记录 */
    @DeleteMapping("/{batchId}/items/{itemId}")
    public Result<Void> deleteExtraItem(@PathVariable Long batchId,
                                        @PathVariable Long itemId) {
        stocktakeService.deleteExtraItem(batchId, itemId);
        return Result.success();
    }

    /** 差异详情追溯：盘点明细 + 绑定历史 + 最近移交单与流转记录 */
    @GetMapping("/{batchId}/items/{itemId}/trace")
    public Result<StocktakeTraceVO> getTrace(@PathVariable Long batchId,
                                             @PathVariable Long itemId) {
        return Result.success(stocktakeService.getTrace(batchId, itemId));
    }

    // ---------------- 导出 ----------------

    @GetMapping("/{batchId}/export")
    public void export(@PathVariable Long batchId, HttpServletResponse response) throws IOException {
        StocktakeBatch batch = stocktakeService.getBatch(batchId);
        List<StocktakeItem> items = stocktakeService.listForExport(batchId);

        response.setContentType("text/csv; charset=UTF-8");
        String fileName = "挡块盘点差异_" + batch.getBatchNo() + ".csv";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);

        // UTF-8 BOM，保证 Excel 直接打开中文不乱码
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        StringBuilder sb = new StringBuilder();
        appendCsvLine(sb, List.of(StocktakeService.exportHeaders()));
        for (StocktakeItem item : items) {
            appendCsvLine(sb, stocktakeService.exportRow(item, batch));
        }
        response.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
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
