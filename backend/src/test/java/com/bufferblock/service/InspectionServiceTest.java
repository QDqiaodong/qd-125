package com.bufferblock.service;

import com.bufferblock.dto.BufferBlockDTO;
import com.bufferblock.dto.GaugeBlockedVO;
import com.bufferblock.dto.InspectionCreateDTO;
import com.bufferblock.dto.InspectionItemVO;
import com.bufferblock.dto.InspectionOverviewVO;
import com.bufferblock.dto.InspectionPendingRecheckItemVO;
import com.bufferblock.dto.InspectionPendingRecheckVO;
import com.bufferblock.dto.InspectionQueryDTO;
import com.bufferblock.entity.BlockInspection;
import com.bufferblock.entity.BufferBlock;
import com.bufferblock.entity.GaugeCalibration;
import com.bufferblock.entity.GaugeTool;
import com.bufferblock.entity.ProductionLine;
import com.bufferblock.exception.GaugeCalibrationBlockedException;
import com.bufferblock.exception.InspectionPendingRecheckException;
import com.bufferblock.repository.BlockInspectionRepository;
import com.bufferblock.repository.BlockLineBindingRepository;
import com.bufferblock.repository.BufferBlockRepository;
import com.bufferblock.repository.GaugeCalibrationRepository;
import com.bufferblock.repository.GaugeToolRepository;
import com.bufferblock.repository.ProductionLineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 班次点检台账：按产线筛选在用挡块、班次打卡落库、
 * 最近一次点检结论同源派生、刷新后条数与列表一致、挂起/未绑定挡块不可点检。
 */
@SpringBootTest
class InspectionServiceTest {

    @Autowired
    private InspectionService inspectionService;
    @Autowired
    private BufferBlockService bufferBlockService;
    @Autowired
    private BlockInspectionRepository inspectionRepository;
    @Autowired
    private BufferBlockRepository blockRepository;
    @Autowired
    private ProductionLineRepository lineRepository;
    @Autowired
    private BlockLineBindingRepository bindingRepository;
    @Autowired
    private GaugeToolRepository gaugeToolRepository;
    @Autowired
    private GaugeCalibrationRepository gaugeCalibrationRepository;

    private ProductionLine workshopA;
    private ProductionLine lineA1;
    private ProductionLine lineA2;
    private ProductionLine workshopB;
    private ProductionLine lineB1;

    @BeforeEach
    void setUp() {
        inspectionRepository.deleteAll();
        bindingRepository.deleteAll();
        blockRepository.deleteAll();
        lineRepository.deleteAll();
        gaugeCalibrationRepository.deleteAll();
        gaugeToolRepository.deleteAll();

        workshopA = saveWorkshop("INSP-WA", "一车间");
        lineA1 = saveLine("INSP-LA1", "点检A1号线", workshopA.getId());
        lineA2 = saveLine("INSP-LA2", "点检A2号线", workshopA.getId());
        workshopB = saveWorkshop("INSP-WB", "二车间");
        lineB1 = saveLine("INSP-LB1", "点检B1号线", workshopB.getId());
    }

    @Test
    void overviewOnlyContainsInServiceBlocksBoundToLines() {
        BufferBlock onLine = createBoundBlock("BLK-INSP-1", lineA1.getId());
        BufferBlock suspended = createBoundBlock("BLK-INSP-S", lineA1.getId());
        blockRepository.findById(suspended.getId()).ifPresent(b -> {
            b.setServiceStatus(BufferBlock.STATUS_SUSPENDED);
            blockRepository.save(b);
        });
        BufferBlock unbound = createBlock("BLK-INSP-U");

        InspectionOverviewVO overview = inspectionService.getOverview(new InspectionQueryDTO());

        assertThat(overview.getItems()).extracting(InspectionItemVO::getBlockId)
                .containsExactly(onLine.getId());
        assertThat(overview.getTotalCount()).isEqualTo(1);
        assertThat(overview.getItems()).hasSize(overview.getTotalCount());
        assertThat(overview.getNeverInspectedCount()).isEqualTo(1);
        assertThat(unbound.getId()).isNotNull();
    }

    @Test
    void filterByLeafLineAndWorkshopNode() {
        BufferBlock a1 = createBoundBlock("BLK-INSP-A1", lineA1.getId());
        BufferBlock a2 = createBoundBlock("BLK-INSP-A2", lineA2.getId());
        BufferBlock b1 = createBoundBlock("BLK-INSP-B1", lineB1.getId());

        InspectionQueryDTO leafQuery = new InspectionQueryDTO();
        leafQuery.setLineId(lineA1.getId());
        assertThat(inspectionService.getOverview(leafQuery).getItems())
                .extracting(InspectionItemVO::getBlockId).containsExactly(a1.getId());

        InspectionQueryDTO workshopQuery = new InspectionQueryDTO();
        workshopQuery.setLineId(workshopA.getId());
        assertThat(inspectionService.getOverview(workshopQuery).getItems())
                .extracting(InspectionItemVO::getBlockId)
                .containsExactlyInAnyOrder(a1.getId(), a2.getId());

        assertThat(inspectionService.getOverview(new InspectionQueryDTO()).getItems())
                .extracting(InspectionItemVO::getBlockId)
                .containsExactlyInAnyOrder(a1.getId(), a2.getId(), b1.getId());
    }

    @Test
    void checkInPersistsInspectorShiftResultAndLatestIsDerived() {
        BufferBlock block = createBoundBlock("BLK-INSP-CK", lineA1.getId());
        LocalDateTime firstTime = LocalDateTime.now().minusHours(8);
        checkIn(block.getId(), "MORNING", "USABLE", "点检人甲", firstTime, "早班正常");
        LocalDateTime secondTime = LocalDateTime.now().minusHours(1);
        checkIn(block.getId(), "NIGHT", "UNUSABLE", "点检人乙", secondTime, "晚班发现卡滞");

        BlockInspection latest = inspectionService.getLatest(block.getId());
        assertThat(latest.getShiftCode()).isEqualTo("NIGHT");
        assertThat(latest.getResult()).isEqualTo("UNUSABLE");
        assertThat(latest.getInspector()).isEqualTo("点检人乙");
        assertThat(latest.getNote()).isEqualTo("晚班发现卡滞");
        assertThat(inspectionService.getHistory(block.getId())).hasSize(2);

        // 台账行展示最近一次结论：不可用 1 条，可用 0 条，条数与列表同源
        InspectionOverviewVO overview = inspectionService.getOverview(new InspectionQueryDTO());
        assertThat(overview.getTotalCount()).isEqualTo(1);
        assertThat(overview.getItems()).hasSize(1);
        assertThat(overview.getUnusableCount()).isEqualTo(1);
        assertThat(overview.getUsableCount()).isZero();
        InspectionItemVO row = overview.getItems().get(0);
        assertThat(row.getLastResult()).isEqualTo("UNUSABLE");
        assertThat(row.getLastShiftCode()).isEqualTo("NIGHT");
        assertThat(row.getLastInspector()).isEqualTo("点检人乙");
        assertThat(row.isNeverInspected()).isFalse();

        // 按班次/结论筛选
        InspectionQueryDTO nightQuery = new InspectionQueryDTO();
        nightQuery.setShiftCode("NIGHT");
        assertThat(nightQuery.getShiftCode()).isEqualTo("NIGHT");
        assertThat(inspectionService.getOverview(nightQuery).getItems()).hasSize(1);
        InspectionQueryDTO usableQuery = new InspectionQueryDTO();
        usableQuery.setResult("USABLE");
        assertThat(inspectionService.getOverview(usableQuery).getItems()).isEmpty();
        InspectionQueryDTO neverQuery = new InspectionQueryDTO();
        neverQuery.setResult("NEVER");
        assertThat(inspectionService.getOverview(neverQuery).getItems()).isEmpty();
    }

    @Test
    void countsStayConsistentAcrossReload() {
        BufferBlock usableBlock = createBoundBlock("BLK-INSP-OK", lineA1.getId());
        BufferBlock unusableBlock = createBoundBlock("BLK-INSP-NG", lineA2.getId());
        createBoundBlock("BLK-INSP-NEW", lineB1.getId());

        checkIn(usableBlock.getId(), "MORNING", "USABLE", "点检人甲", LocalDateTime.now(), null);
        checkIn(unusableBlock.getId(), "AFTERNOON", "UNUSABLE", "点检人乙", LocalDateTime.now(), "异常");

        // 模拟刷新/重进：重新调用两次概览接口，条数与列表必须完全一致
        InspectionOverviewVO first = inspectionService.getOverview(new InspectionQueryDTO());
        InspectionOverviewVO second = inspectionService.getOverview(new InspectionQueryDTO());
        for (InspectionOverviewVO vo : java.util.List.of(first, second)) {
            assertThat(vo.getTotalCount()).isEqualTo(3);
            assertThat(vo.getItems()).hasSize(vo.getTotalCount());
            assertThat(vo.getUsableCount()).isEqualTo(1);
            assertThat(vo.getUnusableCount()).isEqualTo(1);
            assertThat(vo.getNeverInspectedCount()).isEqualTo(1);
        }
    }

    @Test
    void suspendedAndUnboundBlocksCannotCheckIn() {
        BufferBlock unbound = createBlock("BLK-INSP-UB");
        assertThatThrownBy(() -> checkIn(unbound.getId(), "MORNING", "USABLE", "点检人甲",
                LocalDateTime.now(), null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("绑定产线");

        BufferBlock suspended = createBoundBlock("BLK-INSP-SP", lineA1.getId());
        blockRepository.findById(suspended.getId()).ifPresent(b -> {
            b.setServiceStatus(BufferBlock.STATUS_SUSPENDED);
            blockRepository.save(b);
        });
        assertThatThrownBy(() -> checkIn(suspended.getId(), "MORNING", "USABLE", "点检人甲",
                LocalDateTime.now(), null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("挂起待修");
        assertThat(inspectionRepository.count()).isZero();
    }

    @Test
    void invalidCheckInArgumentsAreRejected() {
        BufferBlock block = createBoundBlock("BLK-INSP-IV", lineA1.getId());

        InspectionCreateDTO noShift = dto(block.getId());
        noShift.setShiftCode(null);
        assertThatThrownBy(() -> inspectionService.createInspection(noShift))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("班次");

        InspectionCreateDTO badResult = dto(block.getId());
        badResult.setResult("MAYBE");
        assertThatThrownBy(() -> inspectionService.createInspection(badResult))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("结论");

        InspectionCreateDTO noInspector = dto(block.getId());
        noInspector.setInspector("  ");
        assertThatThrownBy(() -> inspectionService.createInspection(noInspector))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("点检人");

        assertThat(inspectionRepository.count()).isZero();
    }

    @Test
    void submissionBlockedWhenLineShiftHasUnrecheckedUnusableBlocks() {
        BufferBlock bad = createBoundBlock("BLK-INSP-B1", lineA1.getId());
        BufferBlock other = createBoundBlock("BLK-INSP-B2", lineA1.getId());
        BufferBlock otherLine = createBoundBlock("BLK-INSP-B3", lineA2.getId());

        // 早班：bad 打卡不可用
        checkIn(bad.getId(), "MORNING", "UNUSABLE", "点检人乙", LocalDateTime.now(), "卡滞");

        // 同产线同班次提交其他挡块 -> 被拦截，明细逐条列出挡块编号，本次提交不落库
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        checkIn(other.getId(), "MORNING", "USABLE", "点检人甲", LocalDateTime.now(), null))
                .isInstanceOf(InspectionPendingRecheckException.class)
                .hasMessageContaining("BLK-INSP-B1")
                .satisfies(ex -> {
                    InspectionPendingRecheckVO detail =
                            (InspectionPendingRecheckVO) ((InspectionPendingRecheckException) ex).getDetail();
                    assertThat(detail.getCount()).isEqualTo(1);
                    assertThat(detail.getBlockCodes()).containsExactly("BLK-INSP-B1");
                    assertThat(detail.getLineId()).isEqualTo(lineA1.getId());
                    assertThat(detail.getShiftCode()).isEqualTo("MORNING");
                });
        // 拦截后未落库：other 仍为从未点检
        assertThat(inspectionService.getLatest(other.getId())).isNull();

        // 其他产线同班次不受影响
        checkIn(otherLine.getId(), "MORNING", "USABLE", "点检人丙", LocalDateTime.now(), null);
        assertThat(inspectionService.getLatest(otherLine.getId()).getResult()).isEqualTo("USABLE");

        // 同产线其他班次也不受影响
        checkIn(other.getId(), "AFTERNOON", "USABLE", "点检人甲", LocalDateTime.now(), null);
        assertThat(inspectionService.getLatest(other.getId()).getResult()).isEqualTo("USABLE");
    }

    @Test
    void recheckClearsBlockAndSubmissionPassesAfterwards() {
        BufferBlock bad1 = createBoundBlock("BLK-INSP-R1", lineA1.getId());
        BufferBlock bad2 = createBoundBlock("BLK-INSP-R2", lineA1.getId());
        BufferBlock good = createBoundBlock("BLK-INSP-R3", lineA1.getId());

        checkIn(bad1.getId(), "MORNING", "UNUSABLE", "点检人乙", LocalDateTime.now(), "异常1");
        checkIn(bad2.getId(), "MORNING", "UNUSABLE", "点检人乙", LocalDateTime.now(), "异常2");

        // 此时普通提交被拦截，明细列出 2 块
        assertThatThrownBy(() -> checkIn(good.getId(), "MORNING", "USABLE", "点检人甲",
                LocalDateTime.now(), null))
                .isInstanceOf(InspectionPendingRecheckException.class)
                .satisfies(ex -> {
                    InspectionPendingRecheckVO detail =
                            (InspectionPendingRecheckVO) ((InspectionPendingRecheckException) ex).getDetail();
                    assertThat(detail.getCount()).isEqualTo(2);
                    assertThat(detail.getBlockCodes())
                            .containsExactly("BLK-INSP-R1", "BLK-INSP-R2");
                });

        // 对不可用挡块自身复检（可用）允许落库，并返回仍未复检通过的另一块
        InspectionService.InspectionCheckInResult firstRecheck =
                recheck(bad1.getId(), "MORNING", "USABLE", "复检人甲");
        assertThat(firstRecheck.getInspection().getResult()).isEqualTo("USABLE");
        assertThat(firstRecheck.getPendingRecheck()).isNotNull();
        assertThat(firstRecheck.getPendingRecheck().getCount()).isEqualTo(1);
        assertThat(firstRecheck.getPendingRecheck().getBlockCodes()).containsExactly("BLK-INSP-R2");

        // 只剩一块时普通提交仍被拦截
        assertThatThrownBy(() -> checkIn(good.getId(), "MORNING", "USABLE", "点检人甲",
                LocalDateTime.now(), null))
                .isInstanceOf(InspectionPendingRecheckException.class);

        // 第二块复检通过后再提交应能通过，且不再有待复检清单
        InspectionService.InspectionCheckInResult secondRecheck =
                recheck(bad2.getId(), "MORNING", "USABLE", "复检人甲");
        assertThat(secondRecheck.getPendingRecheck()).isNull();
        InspectionService.InspectionCheckInResult normal =
                submitResult(good.getId(), "MORNING", "USABLE", "点检人甲");
        assertThat(normal.getPendingRecheck()).isNull();
        assertThat(inspectionService.getLatest(good.getId()).getResult()).isEqualTo("USABLE");

        // 复检通过后最近结论派生为可用，概览不可用/待复检条数归零，刷新两次口径一致
        for (int i = 0; i < 2; i++) {
            InspectionOverviewVO overview = inspectionService.getOverview(new InspectionQueryDTO());
            assertThat(overview.getUnusableCount()).isZero();
            assertThat(overview.getUsableCount()).isEqualTo(3);
            assertThat(overview.getPendingRecheckCount()).isZero();
            assertThat(overview.getPendingRecheckItems()).isEmpty();
        }
    }

    @Test
    void pendingRecheckCountMatchesCodesAcrossReload() {
        BufferBlock morningBad = createBoundBlock("BLK-INSP-P1", lineA1.getId());
        BufferBlock nightBad = createBoundBlock("BLK-INSP-P2", lineA1.getId());
        BufferBlock workshopOther = createBoundBlock("BLK-INSP-P3", lineB1.getId());

        checkIn(morningBad.getId(), "MORNING", "UNUSABLE", "点检人乙", LocalDateTime.now(), null);
        checkIn(nightBad.getId(), "NIGHT", "UNUSABLE", "点检人丙", LocalDateTime.now(), null);

        // 概览待复检条数与明细编号一致（不随班次/结论筛选变化），刷新后仍然一致
        for (int i = 0; i < 2; i++) {
            InspectionOverviewVO overview = inspectionService.getOverview(new InspectionQueryDTO());
            assertThat(overview.getPendingRecheckCount()).isEqualTo(2);
            assertThat(overview.getPendingRecheckItems()).hasSize(2);
            assertThat(overview.getPendingRecheckItems()).extracting(InspectionPendingRecheckItemVO::getBlockCode)
                    .containsExactly("BLK-INSP-P1", "BLK-INSP-P2");
            // 行内待复检标记与结论同源
            InspectionItemVO morningRow = overview.getItems().stream()
                    .filter(item -> item.getBlockId().equals(morningBad.getId())).findFirst().orElseThrow();
            assertThat(morningRow.isPendingRecheck()).isTrue();

            // 按班次筛选不改变待复检口径
            InspectionQueryDTO nightQuery = new InspectionQueryDTO();
            nightQuery.setShiftCode("NIGHT");
            assertThat(inspectionService.getOverview(nightQuery).getPendingRecheckCount()).isEqualTo(2);
        }

        // 只看 B 车间时待复检清单为空，且该范围提交不受 A 车间不可用挡块影响
        InspectionQueryDTO workshopBQuery = new InspectionQueryDTO();
        workshopBQuery.setLineId(workshopB.getId());
        assertThat(inspectionService.getOverview(workshopBQuery).getPendingRecheckCount()).isZero();
        checkIn(workshopOther.getId(), "MORNING", "USABLE", "点检人甲", LocalDateTime.now(), null);
    }

    @Test
    void blockArchiveCarriesLatestInspectionResult() {        BufferBlock block = createBoundBlock("BLK-INSP-AR", lineA1.getId());
        assertThat(bufferBlockService.getById(block.getId()).getLastInspectionResult()).isNull();

        checkIn(block.getId(), "MORNING", "USABLE", "点检人甲", LocalDateTime.now().minusHours(5), null);
        checkIn(block.getId(), "NIGHT", "UNUSABLE", "点检人乙", LocalDateTime.now(), "晚班异常");

        BufferBlockDTO archived = bufferBlockService.getById(block.getId());
        assertThat(archived.getLastInspectionResult()).isEqualTo("UNUSABLE");
        assertThat(archived.getLastInspectionShift()).isEqualTo("NIGHT");
        assertThat(archived.getLastInspector()).isEqualTo("点检人乙");
        assertThat(archived.getLastInspectionNote()).isEqualTo("晚班异常");
        assertThat(archived.getLastInspectionTime()).isNotNull();

        assertThat(bufferBlockService.getAllBlocks().stream()
                .filter(dto -> dto.getId().equals(block.getId()))
                .findFirst().orElseThrow().getLastInspectionResult())
                .isEqualTo("UNUSABLE");
    }

    @Test
    void checkInBlockedWhenGaugeOverdueAndPassesAfterCalibrationSameShift() {
        BufferBlock block = createBoundBlock("BLK-GG-1", lineA1.getId());

        // 一把卡尺到期未校准
        GaugeTool overdue = createGauge("KC-GG-1", GaugeTool.TYPE_CALIPER,
                LocalDate.now().minusDays(1), "总装一班");

        // 早班打卡被工装闸门拦住，明细列出超期工装编号，点检不落库
        assertThatThrownBy(() -> checkIn(block.getId(), "MORNING", "USABLE", "点检人甲",
                LocalDateTime.now(), null))
                .isInstanceOf(GaugeCalibrationBlockedException.class)
                .hasMessageContaining("KC-GG-1")
                .satisfies(ex -> {
                    GaugeBlockedVO detail =
                            (GaugeBlockedVO) ((GaugeCalibrationBlockedException) ex).getDetail();
                    assertThat(detail.getCount()).isEqualTo(1);
                    assertThat(detail.getCodes()).containsExactly("KC-GG-1");
                });
        assertThat(inspectionService.getLatest(block.getId())).isNull();

        // 卡尺校准合格，到期日同步到一年后
        passGaugeCalibration(overdue.getId(), LocalDate.now().plusYears(1));

        // 同一班次（早班）再打卡应能通过
        checkIn(block.getId(), "MORNING", "USABLE", "点检人甲", LocalDateTime.now(), null);
        assertThat(inspectionService.getLatest(block.getId()).getResult()).isEqualTo("USABLE");
    }

    @Test
    void checkInBlockedWhenGaugeCalibrationFailed() {
        BufferBlock block = createBoundBlock("BLK-GG-2", lineA1.getId());
        GaugeTool failed = createGauge("SC-GG-1", GaugeTool.TYPE_FEELER,
                LocalDate.now().plusDays(60), "总装一班");
        failGaugeCalibration(failed.getId(), LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(60));

        // 即使到期日还没到，最近结论不合格也要拦住打卡
        assertThatThrownBy(() -> checkIn(block.getId(), "NIGHT", "USABLE", "点检人乙",
                LocalDateTime.now(), null))
                .isInstanceOf(GaugeCalibrationBlockedException.class)
                .satisfies(ex -> {
                    GaugeBlockedVO detail =
                            (GaugeBlockedVO) ((GaugeCalibrationBlockedException) ex).getDetail();
                    assertThat(detail.getCodes()).containsExactly("SC-GG-1");
                });
        assertThat(inspectionService.getLatest(block.getId())).isNull();
    }

    private GaugeTool createGauge(String code, String type, LocalDate dueDate, String team) {
        GaugeTool tool = new GaugeTool();
        tool.setToolCode(code);
        tool.setToolType(type);
        tool.setCalibrationDueDate(dueDate);
        tool.setKeeperTeam(team);
        tool.setDisabled(0);
        return gaugeToolRepository.save(tool);
    }

    private void passGaugeCalibration(Long toolId, LocalDate nextDueDate) {
        GaugeCalibration calibration = new GaugeCalibration();
        calibration.setToolId(toolId);
        calibration.setCalibrationDate(LocalDate.now());
        calibration.setResult(GaugeCalibration.RESULT_PASS);
        calibration.setValidUntil(nextDueDate);
        calibration.setNextDueDate(nextDueDate);
        calibration.setCalibrator("校准员甲");
        gaugeCalibrationRepository.save(calibration);
        gaugeToolRepository.findById(toolId).ifPresent(t -> {
            t.setCalibrationDueDate(nextDueDate);
            gaugeToolRepository.save(t);
        });
    }

    private void failGaugeCalibration(Long toolId, LocalDate calibrationDate, LocalDate nextDueDate) {
        GaugeCalibration calibration = new GaugeCalibration();
        calibration.setToolId(toolId);
        calibration.setCalibrationDate(calibrationDate);
        calibration.setResult(GaugeCalibration.RESULT_FAIL);
        calibration.setValidUntil(nextDueDate);
        calibration.setNextDueDate(nextDueDate);
        calibration.setCalibrator("校准员甲");
        gaugeCalibrationRepository.save(calibration);
    }

    private ProductionLine saveWorkshop(String code, String name) {
        ProductionLine line = new ProductionLine();
        line.setLineCode(code);
        line.setLineName(name);
        return lineRepository.save(line);
    }

    private ProductionLine saveLine(String code, String name, Long parentId) {
        ProductionLine line = new ProductionLine();
        line.setLineCode(code);
        line.setLineName(name);
        line.setParentId(parentId);
        return lineRepository.save(line);
    }

    private BufferBlock createBlock(String code) {
        BufferBlock block = new BufferBlock();
        block.setBlockCode(code);
        block.setAdapterModel("点检测试输送机");
        block.setThickness(BigDecimal.TEN);
        return blockRepository.save(block);
    }

    private BufferBlock createBoundBlock(String code, Long lineId) {
        BufferBlock block = createBlock(code);
        bufferBlockService.bindBlockToLine(block.getId(), lineId, "测试员", 1);
        return block;
    }

    private void checkIn(Long blockId, String shift, String result, String inspector,
                         LocalDateTime time, String note) {
        InspectionCreateDTO dto = dto(blockId);
        dto.setShiftCode(shift);
        dto.setResult(result);
        dto.setInspector(inspector);
        dto.setInspectionTime(time);
        dto.setNote(note);
        inspectionService.createInspection(dto);
    }

    /** 复检/提交并返回完整结果（含仍未复检通过的挡块清单） */
    private InspectionService.InspectionCheckInResult submitResult(Long blockId, String shift, String result,
                                                                   String inspector) {
        InspectionCreateDTO dto = dto(blockId);
        dto.setShiftCode(shift);
        dto.setResult(result);
        dto.setInspector(inspector);
        dto.setInspectionTime(LocalDateTime.now());
        return inspectionService.createInspection(dto);
    }

    private InspectionService.InspectionCheckInResult recheck(Long blockId, String shift, String result,
                                                               String inspector) {
        return submitResult(blockId, shift, result, inspector);
    }

    private InspectionCreateDTO dto(Long blockId) {
        InspectionCreateDTO dto = new InspectionCreateDTO();
        dto.setBlockId(blockId);
        dto.setShiftCode("MORNING");
        dto.setResult("USABLE");
        dto.setInspector("点检人甲");
        dto.setInspectionTime(LocalDateTime.now());
        return dto;
    }
}
