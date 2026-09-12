package com.bufferblock.controller;

import com.bufferblock.config.GlobalExceptionHandler;
import com.bufferblock.dto.TransferQueryDTO;
import com.bufferblock.entity.BlockTransfer;
import com.bufferblock.service.BlockTransferService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BlockTransferController.class)
@Import(GlobalExceptionHandler.class)
class BlockTransferControllerExportTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BlockTransferService blockTransferService;

    @Test
    void exportWritesBomHeaderAndEscapedRowsAndPassesFilters() throws Exception {
        BlockTransfer transfer = new BlockTransfer();
        transfer.setTransferNo("TRF-20260912-001");
        when(blockTransferService.listForExport(any())).thenReturn(List.of(transfer));
        when(blockTransferService.exportRow(transfer))
                .thenReturn(List.of("TRF-20260912-001", "BLK-001", "线路调整,产能平衡", "积压过久"));

        MvcResult result = mockMvc.perform(post("/api/transfers/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "PENDING",
                                  "waitSort": "SHORTEST_FIRST",
                                  "blockCode": "BLK"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("filename*=UTF-8''")))
                .andReturn();

        // 筛选与排序参数原样透传给服务层
        ArgumentCaptor<TransferQueryDTO> captor = ArgumentCaptor.forClass(TransferQueryDTO.class);
        org.mockito.Mockito.verify(blockTransferService).listForExport(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getWaitSort()).isEqualTo("SHORTEST_FIRST");
        assertThat(captor.getValue().getBlockCode()).isEqualTo("BLK");

        byte[] body = result.getResponse().getContentAsByteArray();
        // UTF-8 BOM 开头，Excel 直接打开中文不乱码
        assertThat(body[0]).isEqualTo((byte) 0xEF);
        assertThat(body[1]).isEqualTo((byte) 0xBB);
        assertThat(body[2]).isEqualTo((byte) 0xBF);

        String csv = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        assertThat(lines[0]).isEqualTo(String.join(",", BlockTransferService.exportHeaders()));
        assertThat(lines[0]).contains("等待时长").contains("积压标记");
        // 含逗号的字段按 CSV 规范加引号转义
        assertThat(lines[1]).isEqualTo("TRF-20260912-001,BLK-001,\"线路调整,产能平衡\",积压过久");
    }
}
