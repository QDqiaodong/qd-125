package com.bufferblock.service;

import com.bufferblock.dto.CalibrationCreateDTO;
import com.bufferblock.dto.CalibrationDueSoonVO;
import com.bufferblock.dto.CalibrationStatusVO;
import com.bufferblock.dto.TransferCreateDTO;
import com.bufferblock.entity.BlockCalibration;
import com.bufferblock.entity.BlockTransfer;
import com.bufferblock.entity.BlockTransferSequence;
import com.bufferblock.entity.BufferBlock;
import com.bufferblock.entity.ProductionLine;
import com.bufferblock.repository.BlockCalibrationRepository;
import com.bufferblock.repository.BlockLineBindingRepository;
import com.bufferblock.repository.BlockTransferRepository;
import com.bufferblock.repository.BlockTransferSequenceRepository;
import com.bufferblock.repository.BufferBlockRepository;
import com.bufferblock.repository.ProductionLineRepository;
import com.bufferblock.repository.TransferFlowRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 校准临期：30 天临期窗口派生、临期概览条数与清单一致、
 * 不合格挂起不计入临期待办、临期挡块移交必须二次确认。
 */
@SpringBootTest
class CalibrationDueSoonServiceTest {

    @Autowired
    private CalibrationService calibrationService;
    @Autowired
    private BlockTransferService transferService;
    @Autowired
    private BufferBlockService bufferBlockService;
    @Autowired
    private BlockCalibrationRepository calibrationRepository;
    @Autowired
    private BufferBlockRepository blockRepository;
    @Autowired
    private ProductionLineRepository lineRepository;
    @Autowired
    private BlockLineBindingRepository bindingRepository;
    @Autowired
    private BlockTransferRepository transferRepository;
    @Autowired
    private TransferFlowRecordRepository flowRecordRepository;
    @Autowired
    private BlockTransferSequenceRepository sequenceRepository;

    private ProductionLine fromLine;
    private ProductionLine toLine;

    @BeforeEach
    void setUp() {
        calibrationRepository.deleteAll();
        flowRecordRepository.deleteAll();
        transferRepository.deleteAll();
        bindingRepository.deleteAll();
        blockRepository.deleteAll();
        sequenceRepository.deleteAll();
        lineRepository.deleteAll();
        sequenceRepository.save(new BlockTransferSequence("LOCK", 0));

        fromLine = saveLine("DS-L01", "临期测试01号线");
        toLine = saveLine("DS-L02", "临期测试02号线");
    }

    @Test
    void dueSoonWindowBoundariesAreDerivedConsistently() {
        LocalDate today = LocalDate.now();
        BufferBlock dueToday = createBoundBlock("BLK-DS-TODAY");
        calibrate(dueToday.getId(), BlockCalibration.RESULT_PASS, today);
        BufferBlock dueInWindow = createBoundBlock("BLK-DS-30");
        calibrate(dueInWindow.getId(), BlockCalibration.RESULT_PASS,
                today.plusDays(CalibrationService.DUE_SOON_WINDOW_DAYS));
        BufferBlock outsideWindow = createBoundBlock("BLK-DS-31");
        calibrate(outsideWindow.getId(), BlockCalibration.RESULT_PASS,
                today.plusDays(CalibrationService.DUE_SOON_WINDOW_DAYS + 1L));
        BufferBlock overdue = createBoundBlock("BLK-DS-OVER");
        calibrate(overdue.getId(), BlockCalibration.RESULT_PASS, today.minusDays(1));

        assertThat(calibrationService.statusOf(dueToday.getId()).getStatus())
                .isEqualTo(CalibrationStatusVO.DUE_SOON);
        assertThat(calibrationService.statusOf(dueToday.getId()).getDaysUntilDue()).isZero();
        assertThat(calibrationService.statusOf(dueInWindow.getId()).getStatus())
                .isEqualTo(CalibrationStatusVO.DUE_SOON);
        assertThat(calibrationService.statusOf(dueInWindow.getId()).getDaysUntilDue())
                .isEqualTo(CalibrationService.DUE_SOON_WINDOW_DAYS);
        assertThat(calibrationService.statusOf(outsideWindow.getId()).getStatus())
                .isEqualTo(CalibrationStatusVO.NORMAL);
        assertThat(calibrationService.statusOf(overdue.getId()).getStatus())
                .isEqualTo(CalibrationStatusVO.OVERDUE);
    }

    @Test
    void suspendedBlocksNeverAppearInDueSoonOverview() {
        LocalDate today = LocalDate.now();
        BufferBlock suspended = createBoundBlock("BLK-DS-SUSP");
        // 不合格挂起：即使应校日已进入临期窗口，也只能是挂起待修，不得混入临期待办
        calibrate(suspended.getId(), BlockCalibration.RESULT_FAIL, today.plusDays(3));
        BufferBlock dueSoon = createBoundBlock("BLK-DS-OK");
        calibrate(dueSoon.getId(), BlockCalibration.RESULT_PASS, today.plusDays(5));
        BufferBlock overdue = createBoundBlock("BLK-DS-LATE");
        calibrate(overdue.getId(), BlockCalibration.RESULT_PASS, today.minusDays(2));
        BufferBlock uncalibrated = createBoundBlock("BLK-DS-NEW");

        assertThat(calibrationService.statusOf(suspended.getId()).getStatus())
                .isEqualTo(CalibrationStatusVO.SUSPENDED);

        CalibrationDueSoonVO overview = calibrationService.getDueSoonOverview();
        assertThat(overview.getWindowDays()).isEqualTo(CalibrationService.DUE_SOON_WINDOW_DAYS);
        assertThat(overview.getCount()).isEqualTo(overview.getItems().size());
        assertThat(overview.getItems())
                .extracting(CalibrationDueSoonVO.Item::getBlockId)
                .containsExactly(dueSoon.getId());
    }

    @Test
    void overviewItemsCarryDueDateLineAndLastResultSortedByDueDate() {
        LocalDate today = LocalDate.now();
        BufferBlock later = createBoundBlock("BLK-DS-B");
        calibrate(later.getId(), BlockCalibration.RESULT_PASS, today.plusDays(20));
        BufferBlock sooner = createBoundBlock("BLK-DS-A");
        calibrate(sooner.getId(), BlockCalibration.RESULT_PASS, today.plusDays(2));

        CalibrationDueSoonVO overview = calibrationService.getDueSoonOverview();

        assertThat(overview.getCount()).isEqualTo(2);
        List<CalibrationDueSoonVO.Item> items = overview.getItems();
        assertThat(items).extracting(CalibrationDueSoonVO.Item::getBlockId)
                .containsExactly(sooner.getId(), later.getId());
        CalibrationDueSoonVO.Item first = items.get(0);
        assertThat(first.getBlockCode()).isEqualTo("BLK-DS-A");
        assertThat(first.getLineName()).isEqualTo(fromLine.getLineName());
        assertThat(first.getNextDueDate()).isEqualTo(today.plusDays(2));
        assertThat(first.getDaysUntilDue()).isEqualTo(2);
        assertThat(first.getLastResult()).isEqualTo(BlockCalibration.RESULT_PASS);
        assertThat(first.getLastCalibrator()).isEqualTo("校准员甲");
    }

    @Test
    void dueSoonTransferRequiresExplicitSecondConfirmation() {
        LocalDate today = LocalDate.now();
        BufferBlock dueSoon = createBoundBlock("BLK-DS-TRF");
        calibrate(dueSoon.getId(), BlockCalibration.RESULT_PASS, today.plusDays(10));

        TransferCreateDTO unconfirmed = transferDto(dueSoon.getId());
        assertThatThrownBy(() -> transferService.createTransfer(unconfirmed))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("临期")
                .hasMessageContaining("二次确认");
        assertThat(transferRepository.count()).isZero();

        TransferCreateDTO confirmed = transferDto(dueSoon.getId());
        confirmed.setConfirmDueSoon(true);
        BlockTransfer created = transferService.createTransfer(confirmed);
        assertThat(created.getStatus()).isEqualTo(BlockTransfer.STATUS_PENDING);
        assertThat(transferRepository.count()).isEqualTo(1);
    }

    @Test
    void normalAndUncalibratedBlocksTransferWithoutSecondConfirmation() {
        LocalDate today = LocalDate.now();
        BufferBlock normal = createBoundBlock("BLK-DS-NORMAL");
        calibrate(normal.getId(), BlockCalibration.RESULT_PASS, today.plusDays(90));
        BufferBlock uncalibrated = createBoundBlock("BLK-DS-NONE");

        assertThat(transferService.createTransfer(transferDto(normal.getId())).getStatus())
                .isEqualTo(BlockTransfer.STATUS_PENDING);
        assertThat(transferService.createTransfer(transferDto(uncalibrated.getId())).getStatus())
                .isEqualTo(BlockTransfer.STATUS_PENDING);
    }

    private ProductionLine saveLine(String code, String name) {
        ProductionLine line = new ProductionLine();
        line.setLineCode(code);
        line.setLineName(name);
        return lineRepository.save(line);
    }

    private BufferBlock createBoundBlock(String code) {
        BufferBlock block = new BufferBlock();
        block.setBlockCode(code);
        block.setAdapterModel("临期测试输送机");
        block.setThickness(BigDecimal.TEN);
        block = blockRepository.save(block);
        bufferBlockService.bindBlockToLine(block.getId(), fromLine.getId(), "测试员", 1);
        return block;
    }

    private void calibrate(Long blockId, String result, LocalDate nextDueDate) {
        CalibrationCreateDTO dto = new CalibrationCreateDTO();
        dto.setBlockId(blockId);
        dto.setCalibrationDate(LocalDate.now().minusMonths(6));
        dto.setResult(result);
        dto.setValidUntil(nextDueDate);
        dto.setNextDueDate(nextDueDate);
        dto.setCalibrator("校准员甲");
        calibrationService.createCalibration(dto);
    }

    private TransferCreateDTO transferDto(Long blockId) {
        TransferCreateDTO dto = new TransferCreateDTO();
        dto.setBlockId(blockId);
        dto.setFromLineId(fromLine.getId());
        dto.setToLineId(toLine.getId());
        dto.setTransferDate(LocalDate.now());
        dto.setTransferOperator("移交人甲");
        return dto;
    }
}
