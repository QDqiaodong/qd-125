package com.bufferblock.controller;

import com.bufferblock.config.GlobalExceptionHandler;
import com.bufferblock.service.BlockTransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BlockTransferController.class)
@Import(GlobalExceptionHandler.class)
class BlockTransferControllerErrorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BlockTransferService blockTransferService;

    @Test
    void duplicateKeyConflictReturnsFailureResultInsteadOfSuccess() throws Exception {
        when(blockTransferService.createTransfer(any()))
                .thenThrow(new DuplicateKeyException("Duplicate entry 'TRF-20300101-001'"));

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "blockId": 1,
                                  "fromLineId": 2,
                                  "toLineId": 3,
                                  "transferDate": "2030-01-01",
                                  "transferOperator": "张工"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message").value("移交单号或唯一数据冲突，登记未完成，请刷新后重试"));
    }
}
