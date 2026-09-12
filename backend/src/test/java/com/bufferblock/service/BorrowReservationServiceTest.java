package com.bufferblock.service;

import com.bufferblock.dto.BorrowCancelDTO;
import com.bufferblock.dto.BorrowHandleDTO;
import com.bufferblock.dto.BorrowReservationCreateDTO;
import com.bufferblock.entity.BlockBorrowFlowRecord;
import com.bufferblock.entity.BlockBorrowReservation;
import com.bufferblock.entity.BlockBorrowSequence;
import com.bufferblock.entity.BufferBlock;
import com.bufferblock.entity.ProductionLine;
import com.bufferblock.repository.BlockBorrowFlowRecordRepository;
import com.bufferblock.repository.BlockBorrowReservationRepository;
import com.bufferblock.repository.BlockBorrowSequenceRepository;
import com.bufferblock.repository.BufferBlockRepository;
import com.bufferblock.repository.ProductionLineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 借用预约全链路：预约占用、档案标记、取走归还、取消必写原因、到点未取提醒、重开一致性。
 */
@SpringBootTest
class BorrowReservationServiceTest {

    @Autowired
    private BorrowReservationService borrowReservationService;
    @Autowired
    private BufferBlockService bufferBlockService;
    @Autowired
    private BufferBlockRepository blockRepository;
    @Autowired
    private ProductionLineRepository lineRepository;
    @Autowired
    private BlockBorrowReservationRepository reservationRepository;
    @Autowired
    private BlockBorrowFlowRecordRepository flowRecordRepository;
    @Autowired
    private BlockBorrowSequenceRepository sequenceRepository;

    private Long lineId;

    @BeforeEach
    void setUp() {
        flowRecordRepository.deleteAll();
        reservationRepository.deleteAll();
        sequenceRepository.deleteAll();
        blockRepository.deleteAll();
        lineRepository.deleteAll();
        sequenceRepository.save(new BlockBorrowSequence("LOCK", 0));

        ProductionLine line = new ProductionLine();
        line.setLineCode("BR-L01");
        line.setLineName("借用测试01号线");
        lineId = lineRepository.save(line).getId();
    }

    @Test
    void createMarksBlockAsBorrowedOutAndSecondBookingRejected() {
        Long blockId = saveBlock("BLK-BR-001");

        BlockBorrowReservation reservation = borrowReservationService.create(
                createDto(blockId, LocalDateTime.now().plusHours(2)));

        assertThat(reservation.getStatus()).isEqualTo(BlockBorrowReservation.STATUS_RESERVED);
        assertThat(reservation.getReservationNo()).matches("BR-\\d{8}-\\d{3}");

        // 占用期间档案上能看出“已约出”（刷新/重开后仍一致，因为由落库状态实时派生）
        assertThat(bufferBlockService.getById(blockId).getBorrowedOut()).isTrue();
        assertThat(borrowReservationService.activeReservationMap()).containsKey(blockId);

        // 同一挡块不可重复预约
        assertThatThrownBy(() -> borrowReservationService.create(
                createDto(blockId, LocalDateTime.now().plusHours(3))))
                .hasMessageContaining("占用中的借用预约");

        List<BlockBorrowFlowRecord> flows = flowRecordRepository
                .findByReservationIdOrderByCreateTimeAscIdAsc(reservation.getId());
        assertThat(flows).hasSize(1);
        assertThat(flows.get(0).getAction()).isEqualTo(BlockBorrowFlowRecord.ACTION_BOOK);
    }

    @Test
    void pickupAndReturnLifecycleReleasesBlockAtEnd() {
        Long blockId = saveBlock("BLK-BR-002");
        BlockBorrowReservation reservation = borrowReservationService.create(
                createDto(blockId, LocalDateTime.now().plusHours(1)));

        BorrowHandleDTO pickup = new BorrowHandleDTO();
        pickup.setOperator("班组取走人");
        BlockBorrowReservation picked = borrowReservationService.pickup(reservation.getId(), pickup);
        assertThat(picked.getStatus()).isEqualTo(BlockBorrowReservation.STATUS_PICKED_UP);
        assertThat(picked.getActualPickupTime()).isNotNull();
        // 已取走仍占用档案
        assertThat(bufferBlockService.getById(blockId).getBorrowedOut()).isTrue();

        BorrowHandleDTO ret = new BorrowHandleDTO();
        ret.setOperator("库管员");
        BlockBorrowReservation returned = borrowReservationService.giveBack(reservation.getId(), ret);
        assertThat(returned.getStatus()).isEqualTo(BlockBorrowReservation.STATUS_RETURNED);
        assertThat(returned.getActualReturnTime()).isNotNull();
        // 归还后占用解除，档案恢复空闲
        assertThat(bufferBlockService.getById(blockId).getBorrowedOut()).isFalse();
    }

    @Test
    void giveBackRejectedWhenNotPickedUp() {
        Long blockId = saveBlock("BLK-BR-003");
        BlockBorrowReservation reservedOnly = borrowReservationService.create(
                createDto(blockId, LocalDateTime.now().plusHours(1)));
        BorrowHandleDTO ret = new BorrowHandleDTO();
        ret.setOperator("库管员");
        assertThatThrownBy(() -> borrowReservationService.giveBack(reservedOnly.getId(), ret))
                .hasMessageContaining("仅已取走");
        // 失败不影响占用，挡块仍显示已约出
        assertThat(bufferBlockService.getById(blockId).getBorrowedOut()).isTrue();
    }

    @Test
    void cancelRequiresReasonAndReleasesBlock() {
        Long blockId = saveBlock("BLK-BR-004");
        BlockBorrowReservation reservation = borrowReservationService.create(
                createDto(blockId, LocalDateTime.now().plusHours(1)));

        BorrowCancelDTO dto = new BorrowCancelDTO();
        dto.setCancelReason("生产计划变更，暂不借用");
        dto.setOperator("调度员");
        BlockBorrowReservation cancelled = borrowReservationService.cancel(reservation.getId(), dto);

        assertThat(cancelled.getStatus()).isEqualTo(BlockBorrowReservation.STATUS_CANCELLED);
        assertThat(cancelled.getCancelReason()).isEqualTo("生产计划变更，暂不借用");
        assertThat(cancelled.getCancelTime()).isNotNull();
        // 取消后挡块恢复空闲，可再次预约
        assertThat(bufferBlockService.getById(blockId).getBorrowedOut()).isFalse();
        BlockBorrowReservation again = borrowReservationService.create(
                createDto(blockId, LocalDateTime.now().plusHours(2)));
        assertThat(again.getStatus()).isEqualTo(BlockBorrowReservation.STATUS_RESERVED);

        List<BlockBorrowFlowRecord> flows = flowRecordRepository
                .findByReservationIdOrderByCreateTimeAscIdAsc(reservation.getId());
        assertThat(flows).anyMatch(f -> BlockBorrowFlowRecord.ACTION_CANCEL.equals(f.getAction())
                && f.getNote().contains("生产计划变更"));
    }

    @Test
    void cancelWithoutReasonOrOnClosedReservationRejected() {
        Long blockId = saveBlock("BLK-BR-010");
        BlockBorrowReservation reservation = borrowReservationService.create(
                createDto(blockId, LocalDateTime.now().plusHours(1)));

        // 不填原因不允许取消
        BorrowCancelDTO noReason = new BorrowCancelDTO();
        noReason.setOperator("调度员");
        assertThatThrownBy(() -> borrowReservationService.cancel(reservation.getId(), noReason))
                .hasMessageContaining("必须填写取消原因");

        // 正常取消
        BorrowCancelDTO dto = new BorrowCancelDTO();
        dto.setCancelReason("计划取消");
        dto.setOperator("调度员");
        borrowReservationService.cancel(reservation.getId(), dto);

        // 已取消的单不能再次取消
        assertThatThrownBy(() -> borrowReservationService.cancel(reservation.getId(), dto))
                .hasMessageContaining("不允许该操作");
    }

    @Test
    void overduePickupIsFlaggedAndRemindedAndStillPickable() {
        Long blockId = saveBlock("BLK-BR-005");
        BlockBorrowReservation reservation = borrowReservationService.create(
                createDto(blockId, LocalDateTime.now().minusMinutes(30)));

        // 刚约定未来时间的单不提醒
        Long futureBlock = saveBlock("BLK-BR-006");
        borrowReservationService.create(createDto(futureBlock, LocalDateTime.now().plusHours(2)));

        int reminded = borrowReservationService.scanOverduePickups();
        assertThat(reminded).isEqualTo(1);

        BlockBorrowReservation reloaded = borrowReservationService.getById(reservation.getId());
        assertThat(reloaded.getStatus()).isEqualTo(BlockBorrowReservation.STATUS_OVERDUE);
        assertThat(reloaded.getRemindCount()).isEqualTo(1);
        assertThat(reloaded.getLastRemindTime()).isNotNull();
        assertThat(reloaded.getOverdueDuration()).isNotBlank();
        // 逾时未取期间档案仍显示已约出
        assertThat(bufferBlockService.getById(blockId).getBorrowedOut()).isTrue();

        // 幂等：再扫一遍不会重复提醒已置为 OVERDUE 的单
        assertThat(borrowReservationService.scanOverduePickups()).isZero();

        // 逾时后仍可补取走
        BorrowHandleDTO pickup = new BorrowHandleDTO();
        pickup.setOperator("迟到的班组");
        BlockBorrowReservation picked = borrowReservationService.pickup(reservation.getId(), pickup);
        assertThat(picked.getStatus()).isEqualTo(BlockBorrowReservation.STATUS_PICKED_UP);

        List<BlockBorrowFlowRecord> flows = flowRecordRepository
                .findByReservationIdOrderByCreateTimeAscIdAsc(reservation.getId());
        assertThat(flows).anyMatch(f -> BlockBorrowFlowRecord.ACTION_OVERDUE_REMIND.equals(f.getAction()));
        assertThat(flows).anyMatch(f -> BlockBorrowFlowRecord.ACTION_PICKUP.equals(f.getAction()));
    }

    @Test
    void createRejectsMissingRequiredFields() {
        Long blockId = saveBlock("BLK-BR-007");
        BorrowReservationCreateDTO noTeam = createDto(blockId, LocalDateTime.now().plusHours(1));
        noTeam.setTeamName("  ");
        assertThatThrownBy(() -> borrowReservationService.create(noTeam))
                .hasMessageContaining("借用班组");

        BorrowReservationCreateDTO noReturnPoint = createDto(blockId, LocalDateTime.now().plusHours(1));
        noReturnPoint.setReturnPoint(null);
        assertThatThrownBy(() -> borrowReservationService.create(noReturnPoint))
                .hasMessageContaining("归还点");

        BorrowReservationCreateDTO badTime = createDto(blockId, LocalDateTime.now().plusHours(1));
        badTime.setPlannedReturnTime(LocalDateTime.now().minusHours(1));
        assertThatThrownBy(() -> borrowReservationService.create(badTime))
                .hasMessageContaining("计划归还时间不能早于");
    }

    private Long saveBlock(String code) {
        BufferBlock block = new BufferBlock();
        block.setBlockCode(code);
        block.setAdapterModel("测试输送机");
        block.setThickness(new BigDecimal("50.00"));
        return blockRepository.save(block).getId();
    }

    private BorrowReservationCreateDTO createDto(Long blockId, LocalDateTime pickupTime) {
        BorrowReservationCreateDTO dto = new BorrowReservationCreateDTO();
        dto.setBlockId(blockId);
        dto.setTeamName("甲班");
        dto.setPickupTime(pickupTime);
        dto.setPlannedReturnTime(pickupTime.plusDays(1));
        dto.setReturnPoint("借用测试01号线");
        dto.setContactPerson("张三");
        dto.setContactPhone("13800000000");
        dto.setPurpose("临时换型");
        dto.setOperator("计划员");
        return dto;
    }
}
