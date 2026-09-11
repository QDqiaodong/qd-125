package com.bufferblock.controller;

import com.bufferblock.config.GlobalExceptionHandler;
import com.bufferblock.entity.StocktakeBatch;
import com.bufferblock.service.StocktakeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StocktakeController.class)
@Import(GlobalExceptionHandler.class)
class StocktakeControllerErrorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StocktakeService stocktakeService;

    @Test
    void businessValidationFailureReturnsExplicitMessage() throws Exception {
        when(stocktakeService.createBatch(any()))
                .thenThrow(new RuntimeException("该产线已存在盘点中的批次（PD-20320510-001），请先完成或关闭该批次"));

        mockMvc.perform(post("/api/stocktakes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lineId": 2,
                                  "stocktakeDate": "2032-05-10",
                                  "operator": "张工"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message").value(
                        "该产线已存在盘点中的批次（PD-20320510-001），请先完成或关闭该批次"));
    }

    @Test
    void finishWithPendingDiscrepanciesReturnsFailureResult() throws Exception {
        when(stocktakeService.finishBatch(1L))
                .thenThrow(new RuntimeException("仍有 2 条待处理差异，请全部确认或忽略后再结束盘点"));

        mockMvc.perform(post("/api/stocktakes/1/finish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(
                        "仍有 2 条待处理差异，请全部确认或忽略后再结束盘点"));
    }

    @Test
    void countOnCompletedBatchReturnsFailureResult() throws Exception {
        when(stocktakeService.countItem(org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenThrow(new RuntimeException("盘点批次已完成封账，请重新打开后再操作"));

        mockMvc.perform(post("/api/stocktakes/1/items/count")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "blockCode": "BLK-1",
                                  "physicalStatus": "NORMAL",
                                  "operator": "张工"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("盘点批次已完成封账，请重新打开后再操作"));
    }

    @Test
    void successCreateReturnsBatchData() throws Exception {
        StocktakeBatch batch = new StocktakeBatch();
        batch.setId(7L);
        batch.setBatchNo("PD-20320510-001");
        batch.setTotalCount(3);
        when(stocktakeService.createBatch(any())).thenReturn(batch);

        mockMvc.perform(post("/api/stocktakes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lineId": 2,
                                  "stocktakeDate": "2032-05-10",
                                  "operator": "张工"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.batchNo").value("PD-20320510-001"))
                .andExpect(jsonPath("$.data.totalCount").value(3));
    }
}
