package com.bufferblock.controller;

import com.bufferblock.entity.BufferBlock;
import com.bufferblock.entity.ProductionLine;
import com.bufferblock.repository.BlockBorrowFlowRecordRepository;
import com.bufferblock.repository.BlockBorrowReservationRepository;
import com.bufferblock.repository.BlockBorrowSequenceRepository;
import com.bufferblock.repository.BufferBlockRepository;
import com.bufferblock.repository.ProductionLineRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 借用预约全链路：新预约占用档案、取消必写原因、到点未取提醒后状态持久可查。
 */
@SpringBootTest
@AutoConfigureMockMvc
class BorrowReservationControllerIntegrationTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BufferBlockRepository blockRepository;
    @Autowired private ProductionLineRepository lineRepository;
    @Autowired private BlockBorrowReservationRepository reservationRepository;
    @Autowired private BlockBorrowFlowRecordRepository flowRecordRepository;
    @Autowired private BlockBorrowSequenceRepository sequenceRepository;

    private Long blockId;

    @BeforeEach
    void setUp() {
        flowRecordRepository.deleteAll();
        reservationRepository.deleteAll();
        sequenceRepository.deleteAll();
        sequenceRepository.save(new com.bufferblock.entity.BlockBorrowSequence("LOCK", 0));
        blockRepository.deleteAll();
        lineRepository.deleteAll();

        ProductionLine line = new ProductionLine();
        line.setLineCode("BR-IT-L1");
        line.setLineName("集成测试线");
        lineRepository.save(line);

        BufferBlock block = new BufferBlock();
        block.setBlockCode("BLK-IT-BR-001");
        block.setAdapterModel("集成输送机");
        block.setThickness(new BigDecimal("50.00"));
        blockId = blockRepository.save(block).getId();
    }

    @Test
    void bookingLifecycleThroughHttpMatchesBlockMark() throws Exception {
        // 1. 新预约（HTTP 层断言使用 ASCII 值；中文业务提示由服务层测试覆盖）
        String body = "{\"blockId\":" + blockId
                + ",\"teamName\":\"TEAM-B\""
                + ",\"pickupTime\":\"" + LocalDateTime.now().plusHours(2).format(FMT) + "\""
                + ",\"returnPoint\":\"RACK-01\""
                + ",\"operator\":\"PLAN-A\"}";
        MvcResult created = mockMvc.perform(post("/api/borrow-reservations")
                        .contentType(MediaType.parseMediaType("application/json;charset=UTF-8")).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.reservationNo").value(org.hamcrest.Matchers.matchesPattern("BR-\\d{8}-\\d{3}")))
                .andReturn();
        Long reservationId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 2. 档案列表能看到“已约出”
        MvcResult blocks = mockMvc.perform(get("/api/blocks"))
                .andExpect(status().isOk()).andReturn();
        JsonNode blockNode = findBlock(blocks, blockId);
        assertThat(blockNode.path("borrowedOut").asBoolean()).isTrue();
        assertThat(blockNode.path("borrowTeamName").asText()).isEqualTo("TEAM-B");
        assertThat(blockNode.path("borrowReservationNo").asText()).startsWith("BR-");

        // 3. 重复预约同一挡块被拒
        String dupBody = "{\"blockId\":" + blockId + ",\"teamName\":\"TEAM-C\""
                + ",\"pickupTime\":\"" + LocalDateTime.now().plusHours(3).format(FMT) + "\""
                + ",\"returnPoint\":\"RACK-01\",\"operator\":\"PLAN-A\"}";
        mockMvc.perform(post("/api/borrow-reservations")
                        .contentType(MediaType.parseMediaType("application/json;charset=UTF-8")).content(dupBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").isNotEmpty());

        // 4. 取消不写原因被拒
        mockMvc.perform(post("/api/borrow-reservations/" + reservationId + "/cancel")
                        .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                        .content("{\"operator\":\"DISPATCH-A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").isNotEmpty());

        // 5. 写原因后取消成功，档案恢复空闲
        mockMvc.perform(post("/api/borrow-reservations/" + reservationId + "/cancel")
                        .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                        .content("{\"cancelReason\":\"plan changed\",\"operator\":\"DISPATCH-A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("plan changed"));

        JsonNode freeBlock = findBlock(
                mockMvc.perform(get("/api/blocks")).andReturn(), blockId);
        assertThat(freeBlock.path("borrowedOut").asBoolean()).isFalse();
    }

    @Test
    void pickupAndReturnWithChangedPointReleasesBlockMark() throws Exception {
        // 1. 预约
        String body = "{\"blockId\":" + blockId
                + ",\"teamName\":\"TEAM-R\""
                + ",\"pickupTime\":\"" + LocalDateTime.now().minusHours(2).format(FMT) + "\""
                + ",\"plannedReturnTime\":\"" + LocalDateTime.now().minusHours(1).format(FMT) + "\""
                + ",\"returnPoint\":\"RACK-01\""
                + ",\"operator\":\"PLAN-A\"}";
        MvcResult created = mockMvc.perform(post("/api/borrow-reservations")
                        .contentType(MediaType.parseMediaType("application/json;charset=UTF-8")).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andReturn();
        Long reservationId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 2. 取走
        mockMvc.perform(post("/api/borrow-reservations/" + reservationId + "/pickup")
                        .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                        .content("{\"operator\":\"PICKER-A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.data.actualPickupTime").isNotEmpty())
                .andExpect(jsonPath("$.data.pickupOperator").value("PICKER-A"))
                // 已过计划还期：超期未还标记实时派生
                .andExpect(jsonPath("$.data.overdueReturn").value(true));

        // 3. 归还时改归还点
        mockMvc.perform(post("/api/borrow-reservations/" + reservationId + "/return")
                        .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                        .content("{\"operator\":\"KEEPER-A\",\"actualReturnPoint\":\"RACK-09\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURNED"))
                .andExpect(jsonPath("$.data.actualReturnPoint").value("RACK-09"))
                .andExpect(jsonPath("$.data.returnPoint").value("RACK-01"))
                .andExpect(jsonPath("$.data.overdueReturn").value(false));

        // 4. 档案恢复空闲
        JsonNode freeBlock = findBlock(
                mockMvc.perform(get("/api/blocks")).andReturn(), blockId);
        assertThat(freeBlock.path("borrowedOut").asBoolean()).isFalse();
    }

    private JsonNode findBlock(MvcResult result, Long id) throws Exception {
        JsonNode array = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        for (JsonNode node : array) {
            if (node.path("id").asLong() == id) {
                return node;
            }
        }
        throw new IllegalStateException("挡块未在列表中找到");
    }
}
