package com.bufferblock.controller;

import com.bufferblock.entity.BlockTransfer;
import com.bufferblock.entity.BufferBlock;
import com.bufferblock.entity.ProductionLine;
import com.bufferblock.repository.BlockTransferRepository;
import com.bufferblock.repository.BufferBlockRepository;
import com.bufferblock.repository.ProductionLineRepository;
import com.bufferblock.repository.TransferFlowRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 移交确认列表全链路集成测试：等待时长排序、积压标记随查询与导出接口返回。
 */
@SpringBootTest
@AutoConfigureMockMvc
class TransferConfirmIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BlockTransferRepository transferRepository;
    @Autowired
    private BufferBlockRepository blockRepository;
    @Autowired
    private ProductionLineRepository lineRepository;
    @Autowired
    private TransferFlowRecordRepository flowRecordRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long fromLineId;
    private Long toLineId;
    private Long blockId;

    @BeforeEach
    void setUp() {
        flowRecordRepository.deleteAll();
        transferRepository.deleteAll();
        blockRepository.deleteAll();
        lineRepository.deleteAll();

        fromLineId = saveLine("IT-FROM", "集成移出线").getId();
        toLineId = saveLine("IT-TO", "集成移入线").getId();

        BufferBlock block = new BufferBlock();
        block.setBlockCode("BLK-IT-001");
        block.setAdapterModel("集成测试输送机");
        block.setThickness(BigDecimal.TEN);
        blockId = blockRepository.save(block).getId();
    }

    @Test
    void queryReturnsWaitSortedRowsWithLongWaitingFlag() throws Exception {
        savePending("TRF-IT-001", LocalDateTime.now().minusHours(30));
        savePending("TRF-IT-002", LocalDateTime.now().minusHours(1));

        // 最长优先：压了 30 小时的单排在最前，并带积压标记
        mockMvc.perform(post("/api/transfers/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\",\"waitSort\":\"LONGEST_FIRST\",\"page\":1,\"size\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].transferNo").value("TRF-IT-001"))
                .andExpect(jsonPath("$.data.content[0].longWaiting").value(true))
                .andExpect(jsonPath("$.data.content[0].waitingDuration").exists())
                .andExpect(jsonPath("$.data.content[1].transferNo").value("TRF-IT-002"))
                .andExpect(jsonPath("$.data.content[1].longWaiting").value(false));

        // 最短优先：顺序反转，标记不变
        mockMvc.perform(post("/api/transfers/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\",\"waitSort\":\"SHORTEST_FIRST\",\"page\":1,\"size\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].transferNo").value("TRF-IT-002"))
                .andExpect(jsonPath("$.data.content[0].longWaiting").value(false))
                .andExpect(jsonPath("$.data.content[1].transferNo").value("TRF-IT-001"))
                .andExpect(jsonPath("$.data.content[1].longWaiting").value(true));
    }

    @Test
    void exportReturnsCsvWithSameOrderAndBacklogMarks() throws Exception {
        savePending("TRF-IT-011", LocalDateTime.now().minusHours(30));
        savePending("TRF-IT-012", LocalDateTime.now().minusHours(1));

        MvcResult result = mockMvc.perform(post("/api/transfers/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\",\"waitSort\":\"LONGEST_FIRST\"}"))
                .andExpect(status().isOk())
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body[0]).isEqualTo((byte) 0xEF);
        assertThat(body[1]).isEqualTo((byte) 0xBB);
        assertThat(body[2]).isEqualTo((byte) 0xBF);

        String csv = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).contains("等待时长").contains("积压标记");
        // 与列表相同的排队顺序：30 小时单在前并带积压标记，1 小时单在后无标记
        assertThat(lines[1]).startsWith("TRF-IT-011").contains("积压过久");
        assertThat(lines[2]).startsWith("TRF-IT-012").doesNotContain("积压过久");
    }

    private void savePending(String transferNo, LocalDateTime createTime) {
        BlockTransfer transfer = new BlockTransfer();
        transfer.setTransferNo(transferNo);
        transfer.setBlockId(blockId);
        transfer.setFromLineId(fromLineId);
        transfer.setToLineId(toLineId);
        transfer.setTransferDate(LocalDate.now());
        transfer.setTransferOperator("集成测试移交人");
        transfer.setStatus(BlockTransfer.STATUS_PENDING);
        transfer.setPrintCount(0);
        transfer.setReceiptPrintCount(0);
        transfer = transferRepository.saveAndFlush(transfer);
        jdbcTemplate.update("UPDATE block_transfer SET create_time = ? WHERE id = ?",
                Timestamp.valueOf(createTime), transfer.getId());
    }

    private ProductionLine saveLine(String code, String name) {
        ProductionLine line = new ProductionLine();
        line.setLineCode(code);
        line.setLineName(name);
        return lineRepository.save(line);
    }
}
