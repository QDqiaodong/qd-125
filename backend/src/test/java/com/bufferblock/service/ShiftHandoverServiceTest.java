package com.bufferblock.service;

import com.bufferblock.dto.BorrowHandleDTO;
import com.bufferblock.dto.BorrowReservationCreateDTO;
import com.bufferblock.dto.GaugeCalibrationCreateDTO;
import com.bufferblock.dto.GaugeToolCreateDTO;
import com.bufferblock.dto.HandoverCreateDTO;
import com.bufferblock.dto.HandoverItemConfirmDTO;
import com.bufferblock.dto.HandoverOverviewVO;
import com.bufferblock.dto.HandoverPreviewVO;
import com.bufferblock.entity.BlockBorrowReservation;
import com.bufferblock.entity.BlockBorrowSequence;
import com.bufferblock.entity.BlockTransfer;
import com.bufferblock.entity.BufferBlock;
import com.bufferblock.entity.GaugeCalibration;
import com.bufferblock.entity.GaugeTool;
import com.bufferblock.entity.ProductionLine;
import com.bufferblock.entity.ShiftHandover;
import com.bufferblock.entity.ShiftHandoverItem;
import com.bufferblock.entity.ShiftHandoverSequence;
import com.bufferblock.entity.StocktakeBatch;
import com.bufferblock.entity.StocktakeItem;
import com.bufferblock.repository.BlockBorrowFlowRecordRepository;
import com.bufferblock.repository.BlockBorrowReservationRepository;
import com.bufferblock.repository.BlockBorrowSequenceRepository;
import com.bufferblock.repository.BlockTransferRepository;
import com.bufferblock.repository.BufferBlockRepository;
import com.bufferblock.repository.GaugeCalibrationRepository;
import com.bufferblock.repository.GaugeToolRepository;
import com.bufferblock.repository.ProductionLineRepository;
import com.bufferblock.repository.ShiftHandoverItemRepository;
import com.bufferblock.repository.ShiftHandoverRepository;
import com.bufferblock.repository.ShiftHandoverSequenceRepository;
import com.bufferblock.repository.StocktakeBatchRepository;
import com.bufferblock.repository.StocktakeItemRepository;
import com.bufferblock.repository.TransferFlowRecordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 班组交班全链路：一次性快照登记四类未结事项（未还预约/待确认移交/待处理盘点差异/拦截中点检工装）、
 * 接班人逐条确认（仍拦截中的工装须校准合格后才能确认）、重开一致性（落库）、
 * 交班未完成禁止新开预约、未确认工装条数与校准台拦截条数刷新后同源一致。
 */
@SpringBootTest
class ShiftHandoverServiceTest {

    @Autowired
    private ShiftHandoverService shiftHandoverService;
    @Autowired
    private BorrowReservationService borrowReservationService;
    @Autowired
    private GaugeCalibrationService gaugeService;
    @Autowired
    private ShiftHandoverRepository handoverRepository;
    @Autowired
    private ShiftHandoverItemRepository itemRepository;
    @Autowired
    private ShiftHandoverSequenceRepository sequenceRepository;
    @Autowired
    private BlockBorrowReservationRepository reservationRepository;
    @Autowired
    private BlockBorrowFlowRecordRepository borrowFlowRepository;
    @Autowired
    private BlockBorrowSequenceRepository borrowSequenceRepository;
    @Autowired
    private BlockTransferRepository transferRepository;
    @Autowired
    private TransferFlowRecordRepository transferFlowRepository;
    @Autowired
    private StocktakeBatchRepository batchRepository;
    @Autowired
    private StocktakeItemRepository stocktakeItemRepository;
    @Autowired
    private BufferBlockRepository blockRepository;
    @Autowired
    private ProductionLineRepository lineRepository;
    @Autowired
    private GaugeToolRepository gaugeToolRepository;
    @Autowired
    private GaugeCalibrationRepository gaugeCalibrationRepository;

    private Long lineId;
    private Long otherLineId;

    @BeforeEach
    void setUp() {
        itemRepository.deleteAll();
        handoverRepository.deleteAll();
        sequenceRepository.deleteAll();
        borrowFlowRepository.deleteAll();
        reservationRepository.deleteAll();
        borrowSequenceRepository.deleteAll();
        transferFlowRepository.deleteAll();
        transferRepository.deleteAll();
        stocktakeItemRepository.deleteAll();
        batchRepository.deleteAll();
        blockRepository.deleteAll();
        lineRepository.deleteAll();
        gaugeCalibrationRepository.deleteAll();
        gaugeToolRepository.deleteAll();
        sequenceRepository.save(new ShiftHandoverSequence("LOCK", 0));
        borrowSequenceRepository.save(new BlockBorrowSequence("LOCK", 0));

        ProductionLine line = new ProductionLine();
        line.setLineCode("HO-L01");
        line.setLineName("交班测试01号线");
        lineId = lineRepository.save(line).getId();

        ProductionLine other = new ProductionLine();
        other.setLineCode("HO-L02");
        other.setLineName("交班测试02号线");
        otherLineId = lineRepository.save(other).getId();
    }

    /**
     * 交班中数据会影响借用预约闸门（禁止新开预约），拦截中工装会被后续交班快照，
     * 用例结束后必须清场，避免污染同 JVM 内后运行的其他测试类。
     */
    @AfterEach
    void tearDown() {
        itemRepository.deleteAll();
        handoverRepository.deleteAll();
        gaugeCalibrationRepository.deleteAll();
        gaugeToolRepository.deleteAll();
    }

    @Test
    void createSnapshotsAllThreeCategoriesAndPreviewMatches() {
        Long blockId = saveBlock("BLK-HO-001");
        createActiveReservation(blockId);
        createPendingTransfer(blockId);
        createPendingStocktakeItem(blockId);

        // 登记前预览即正式登记口径：三类各 1 条
        HandoverPreviewVO preview = shiftHandoverService.preview();
        assertThat(preview.getBorrowCount()).isEqualTo(1);
        assertThat(preview.getTransferCount()).isEqualTo(1);
        assertThat(preview.getStocktakeCount()).isEqualTo(1);
        assertThat(preview.getItems()).hasSize(3);

        ShiftHandover handover = shiftHandoverService.create(createDto());
        assertThat(handover.getHandoverNo()).matches("HO-\\d{8}-\\d{3}");
        assertThat(handover.getStatus()).isEqualTo(ShiftHandover.STATUS_IN_PROGRESS);
        assertThat(handover.getTotalCount()).isEqualTo(3);
        assertThat(handover.getConfirmedCount()).isEqualTo(0);
        assertThat(handover.getUnconfirmedCount()).isEqualTo(3);

        // 清单落库：关闭页面再打开（重新查询）内容一致
        List<ShiftHandoverItem> items = shiftHandoverService.getItems(handover.getId());
        assertThat(items).hasSize(3);
        assertThat(items).allMatch(i -> ShiftHandoverItem.STATUS_PENDING.equals(i.getStatus()));
        assertThat(items).anyMatch(i -> ShiftHandoverItem.TYPE_BORROW_UNRETURNED.equals(i.getItemType())
                && i.getRefNo().startsWith("BR-") && "BLK-HO-001".equals(i.getBlockCode()));
        assertThat(items).anyMatch(i -> ShiftHandoverItem.TYPE_TRANSFER_PENDING.equals(i.getItemType())
                && i.getRefNo().startsWith("TRF-"));
        assertThat(items).anyMatch(i -> ShiftHandoverItem.TYPE_STOCKTAKE_PENDING.equals(i.getItemType())
                && i.getRefNo().startsWith("PD-"));

        HandoverOverviewVO overview = shiftHandoverService.overview();
        assertThat(overview.isInProgress()).isTrue();
        assertThat(overview.getUnconfirmedCount()).isEqualTo(3);
        assertThat(overview.getHandoverNo()).isEqualTo(handover.getHandoverNo());
    }

    @Test
    void secondHandoverRejectedWhileFirstInProgress() {
        Long blockId = saveBlock("BLK-HO-002");
        createPendingTransfer(blockId);
        shiftHandoverService.create(createDto());

        assertThatThrownBy(() -> shiftHandoverService.create(createDto()))
                .hasMessageContaining("尚未完成");
    }

    @Test
    void createRejectedWhenNothingPending() {
        assertThatThrownBy(() -> shiftHandoverService.create(createDto()))
                .hasMessageContaining("无需登记交班");
    }

    @Test
    void confirmItemsOneByOneAndAutoCompletes() {
        Long blockId = saveBlock("BLK-HO-003");
        createActiveReservation(blockId);
        createPendingTransfer(blockId);
        createPendingStocktakeItem(blockId);
        ShiftHandover handover = shiftHandoverService.create(createDto());
        List<ShiftHandoverItem> items = shiftHandoverService.getItems(handover.getId());

        // 确认人必填
        HandoverItemConfirmDTO blank = new HandoverItemConfirmDTO();
        blank.setOperator("  ");
        assertThatThrownBy(() -> shiftHandoverService.confirmItem(
                handover.getId(), items.get(0).getId(), blank))
                .hasMessageContaining("确认人");

        // 逐条确认：未确认条数随之递减并落库
        ShiftHandover afterFirst = confirm(handover.getId(), items.get(0).getId());
        assertThat(afterFirst.getConfirmedCount()).isEqualTo(1);
        assertThat(afterFirst.getUnconfirmedCount()).isEqualTo(2);
        assertThat(afterFirst.getStatus()).isEqualTo(ShiftHandover.STATUS_IN_PROGRESS);

        ShiftHandover afterSecond = confirm(handover.getId(), items.get(1).getId());
        assertThat(afterSecond.getUnconfirmedCount()).isEqualTo(1);

        // 全部确认后交班自动完成
        ShiftHandover done = confirm(handover.getId(), items.get(2).getId());
        assertThat(done.getStatus()).isEqualTo(ShiftHandover.STATUS_COMPLETED);
        assertThat(done.getUnconfirmedCount()).isEqualTo(0);
        assertThat(done.getFinishTime()).isNotNull();
        assertThat(shiftHandoverService.overview().isInProgress()).isFalse();

        // 确认人/确认时间落库，重开页面（重新查询）仍一致
        List<ShiftHandoverItem> reloaded = shiftHandoverService.getItems(handover.getId());
        assertThat(reloaded).allMatch(i -> ShiftHandoverItem.STATUS_CONFIRMED.equals(i.getStatus())
                && "接班人小李".equals(i.getConfirmOperator()) && i.getConfirmTime() != null);
    }

    @Test
    void confirmTwiceOrOnCompletedHandoverRejected() {
        Long blockId = saveBlock("BLK-HO-004");
        createActiveReservation(blockId);
        createPendingTransfer(blockId);
        ShiftHandover handover = shiftHandoverService.create(createDto());
        List<ShiftHandoverItem> items = shiftHandoverService.getItems(handover.getId());

        confirm(handover.getId(), items.get(0).getId());
        // 同一事项不可重复确认
        assertThatThrownBy(() -> confirm(handover.getId(), items.get(0).getId()))
                .hasMessageContaining("已确认");

        confirm(handover.getId(), items.get(1).getId());
        // 交班完成后不可再确认
        ShiftHandoverItem leftover = items.get(0);
        assertThatThrownBy(() -> confirm(handover.getId(), leftover.getId()))
                .hasMessageContaining("已完成");
    }

    @Test
    void borrowCreateBlockedDuringHandoverAndAllowedAfterCompletion() {
        Long pendingBlock = saveBlock("BLK-HO-005");
        createPendingTransfer(pendingBlock);
        ShiftHandover handover = shiftHandoverService.create(createDto());

        // 交班未完成：禁止新开预约
        Long freeBlock = saveBlock("BLK-HO-006");
        BorrowReservationCreateDTO dto = borrowDto(freeBlock);
        assertThatThrownBy(() -> borrowReservationService.create(dto))
                .hasMessageContaining("禁止新开借用预约");

        // 接班人逐条确认完成后恢复
        for (ShiftHandoverItem item : shiftHandoverService.getItems(handover.getId())) {
            confirm(handover.getId(), item.getId());
        }
        assertThat(shiftHandoverService.overview().isInProgress()).isFalse();
        assertThat(borrowReservationService.create(borrowDto(freeBlock)).getStatus())
                .isEqualTo(BlockBorrowReservation.STATUS_RESERVED);
    }

    @Test
    void snapshotStableWhileSourceStatusFollowsBusiness() {
        Long blockId = saveBlock("BLK-HO-007");
        BlockBorrowReservation reservation = createActiveReservation(blockId);
        ShiftHandover handover = shiftHandoverService.create(createDto());
        ShiftHandoverItem item = shiftHandoverService.getItems(handover.getId()).get(0);
        assertThat(item.getSourceStillOpen()).isTrue();

        // 交班登记后源单据被归还：清单快照不变，源状态实时反映“已归还”
        BorrowHandleDTO pickup = new BorrowHandleDTO();
        pickup.setOperator("班组取走人");
        borrowReservationService.pickup(reservation.getId(), pickup);
        BorrowHandleDTO ret = new BorrowHandleDTO();
        ret.setOperator("库管员");
        borrowReservationService.giveBack(reservation.getId(), ret);

        ShiftHandoverItem reloaded = shiftHandoverService.getItems(handover.getId()).get(0);
        assertThat(reloaded.getStatus()).isEqualTo(ShiftHandoverItem.STATUS_PENDING);
        assertThat(reloaded.getSourceStillOpen()).isFalse();
        assertThat(reloaded.getSourceStatusText()).contains("已归还");
        // 未确认条数不受源单据变化影响，仍须接班人确认后才完成交班
        assertThat(shiftHandoverService.getById(handover.getId()).getUnconfirmedCount()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- 拦截中点检工装

    @Test
    void blockedGaugesSnapshottedAndMatchBench() {
        // 到期未校准 + 结论不合格进入拦截清单；合格在期与停用（即便已过期）不拦截
        createGaugeTool("KC-HO-1", GaugeTool.TYPE_CALIPER, LocalDate.now().minusDays(3), "总装一班");
        GaugeTool failed = createGaugeTool("SC-HO-1", GaugeTool.TYPE_FEELER,
                LocalDate.now().plusDays(30), "总装二班");
        calibrateGauge(failed.getId(), GaugeCalibration.RESULT_FAIL);
        createGaugeTool("BFB-HO-OK", GaugeTool.TYPE_DIAL_INDICATOR, LocalDate.now().plusDays(30), "总装三班");
        GaugeTool disabled = createGaugeTool("KC-HO-STOP", GaugeTool.TYPE_CALIPER,
                LocalDate.now().minusDays(10), "总装一班");
        disableGauge(disabled);

        // 登记前预览即正式登记口径：拦截中工装 2 条，与校准台拦截条数一致
        HandoverPreviewVO preview = shiftHandoverService.preview();
        assertThat(preview.getGaugeCount()).isEqualTo(2);
        assertThat(preview.getItems()).hasSize(2);

        ShiftHandover handover = shiftHandoverService.create(createDto());
        assertThat(handover.getTotalCount()).isEqualTo(2);

        List<ShiftHandoverItem> items = shiftHandoverService.getItems(handover.getId());
        assertThat(items).hasSize(2);
        assertThat(items).allMatch(i -> ShiftHandoverItem.TYPE_GAUGE_BLOCKED.equals(i.getItemType())
                && ShiftHandoverItem.STATUS_PENDING.equals(i.getStatus()));
        // 接班人可逐条核对工装编号与拦截原因
        assertThat(items).anyMatch(i -> "KC-HO-1".equals(i.getRefNo())
                && i.getSummary().contains("到期未校准") && i.getSummary().contains("总装一班"));
        assertThat(items).anyMatch(i -> "SC-HO-1".equals(i.getRefNo())
                && i.getSummary().contains("校准结论不合格") && i.getSummary().contains("总装二班"));
        // 源单当前状态实时派生：两件都仍拦截中
        assertThat(items).allMatch(i -> Boolean.TRUE.equals(i.getSourceStillOpen()));
        assertThat(items).anyMatch(i -> i.getSourceStatusText().contains("到期未校准"));
        assertThat(items).anyMatch(i -> i.getSourceStatusText().contains("校准结论不合格"));

        // 刷新交班页（重新查询概览）：未确认工装条数与校准台拦截条数对得上
        HandoverOverviewVO overview = shiftHandoverService.overview();
        assertThat(overview.getUnconfirmedGaugeCount()).isEqualTo(2);
        assertThat(overview.getUnconfirmedGaugeCount())
                .isEqualTo(gaugeService.getBlockedTools().getCount());
    }

    @Test
    void onlyBlockedGaugesJustifyHandover() {
        createGaugeTool("KC-HO-2", GaugeTool.TYPE_CALIPER, LocalDate.now().minusDays(1), "甲班");

        // 没有其他三类未结事项，仅拦截中工装也需要交班
        ShiftHandover handover = shiftHandoverService.create(createDto());
        assertThat(handover.getTotalCount()).isEqualTo(1);
        assertThat(shiftHandoverService.getItems(handover.getId()).get(0).getItemType())
                .isEqualTo(ShiftHandoverItem.TYPE_GAUGE_BLOCKED);
    }

    @Test
    void gaugeItemConfirmRejectedWhileStillBlocked() {
        GaugeTool tool = createGaugeTool("KC-HO-3", GaugeTool.TYPE_CALIPER,
                LocalDate.now().minusDays(2), "甲班");
        ShiftHandover handover = shiftHandoverService.create(createDto());
        ShiftHandoverItem gaugeItem = shiftHandoverService.getItems(handover.getId()).get(0);

        // 工装仍拦截中：接班人不能确认该条
        assertThatThrownBy(() -> confirm(handover.getId(), gaugeItem.getId()))
                .hasMessageContaining("仍在拦截中")
                .hasMessageContaining("KC-HO-3");
        assertThat(shiftHandoverService.getById(handover.getId()).getStatus())
                .isEqualTo(ShiftHandover.STATUS_IN_PROGRESS);

        // 校准合格移出拦截后，接班人才能确认，交班随之完成
        calibrateGauge(tool.getId(), GaugeCalibration.RESULT_PASS);
        ShiftHandoverItem reloaded = shiftHandoverService.getItems(handover.getId()).get(0);
        assertThat(reloaded.getSourceStillOpen()).isFalse();
        assertThat(reloaded.getSourceStatusText()).contains("已移出拦截清单");

        ShiftHandover done = confirm(handover.getId(), gaugeItem.getId());
        assertThat(done.getStatus()).isEqualTo(ShiftHandover.STATUS_COMPLETED);
        assertThat(shiftHandoverService.overview().isInProgress()).isFalse();
    }

    @Test
    void unconfirmedGaugeCountMatchesBenchAfterRefresh() {
        GaugeTool overdue = createGaugeTool("KC-HO-4", GaugeTool.TYPE_CALIPER,
                LocalDate.now().minusDays(5), "甲班");
        createGaugeTool("SC-HO-4", GaugeTool.TYPE_FEELER, LocalDate.now().minusDays(1), "乙班");
        ShiftHandover handover = shiftHandoverService.create(createDto());
        assertThat(handover.getTotalCount()).isEqualTo(2);

        // 校准合格一件：校准台拦截条数降为 1，刷新交班页后未确认工装条数同步为 1
        calibrateGauge(overdue.getId(), GaugeCalibration.RESULT_PASS);
        HandoverOverviewVO afterCalibrate = shiftHandoverService.overview();
        assertThat(afterCalibrate.getUnconfirmedGaugeCount()).isEqualTo(1);
        assertThat(afterCalibrate.getUnconfirmedGaugeCount())
                .isEqualTo(gaugeService.getBlockedTools().getCount());

        // 交班中新拦截一件：自动补入交班清单，总数随之更新，条数仍与校准台对得上
        createGaugeTool("BFB-HO-4", GaugeTool.TYPE_DIAL_INDICATOR, LocalDate.now().minusDays(2), "丙班");
        List<ShiftHandoverItem> items = shiftHandoverService.getItems(handover.getId());
        assertThat(items).hasSize(3);
        assertThat(items).anyMatch(i -> "BFB-HO-4".equals(i.getRefNo())
                && ShiftHandoverItem.STATUS_PENDING.equals(i.getStatus()));
        ShiftHandover reloaded = shiftHandoverService.getById(handover.getId());
        assertThat(reloaded.getTotalCount()).isEqualTo(3);
        assertThat(reloaded.getUnconfirmedCount()).isEqualTo(3);
        HandoverOverviewVO afterNewBlock = shiftHandoverService.overview();
        assertThat(afterNewBlock.getUnconfirmedGaugeCount()).isEqualTo(2);
        assertThat(afterNewBlock.getUnconfirmedGaugeCount())
                .isEqualTo(gaugeService.getBlockedTools().getCount());
    }

    @Test
    void borrowGateMessageMentionsBlockedGauges() {
        createGaugeTool("KC-HO-5", GaugeTool.TYPE_CALIPER, LocalDate.now().minusDays(1), "甲班");
        shiftHandoverService.create(createDto());

        // 存在未确认工装：禁止新开借用预约，提示含拦截中工装件数
        Long freeBlock = saveBlock("BLK-HO-008");
        assertThatThrownBy(() -> borrowReservationService.create(borrowDto(freeBlock)))
                .hasMessageContaining("禁止新开借用预约")
                .hasMessageContaining("拦截中点检工装 1 件");
    }

    private GaugeTool createGaugeTool(String code, String type, LocalDate dueDate, String team) {
        GaugeToolCreateDTO dto = new GaugeToolCreateDTO();
        dto.setToolCode(code);
        dto.setToolType(type);
        dto.setCalibrationDueDate(dueDate);
        dto.setKeeperTeam(team);
        return gaugeService.createTool(dto);
    }

    private void disableGauge(GaugeTool tool) {
        GaugeToolCreateDTO dto = new GaugeToolCreateDTO();
        dto.setId(tool.getId());
        dto.setToolCode(tool.getToolCode());
        dto.setToolType(tool.getToolType());
        dto.setCalibrationDueDate(tool.getCalibrationDueDate());
        dto.setKeeperTeam(tool.getKeeperTeam());
        dto.setDisabled(1);
        gaugeService.updateTool(dto);
    }

    private void calibrateGauge(Long toolId, String result) {
        GaugeCalibrationCreateDTO dto = new GaugeCalibrationCreateDTO();
        dto.setToolId(toolId);
        dto.setCalibrationDate(LocalDate.now());
        dto.setResult(result);
        dto.setValidUntil(LocalDate.now().plusYears(1));
        dto.setNextDueDate(LocalDate.now().plusYears(1));
        dto.setCalibrator("校准员甲");
        gaugeService.createCalibration(dto);
    }

    private ShiftHandover confirm(Long handoverId, Long itemId) {
        HandoverItemConfirmDTO dto = new HandoverItemConfirmDTO();
        dto.setOperator("接班人小李");
        return shiftHandoverService.confirmItem(handoverId, itemId, dto);
    }

    private Long saveBlock(String code) {
        BufferBlock block = new BufferBlock();
        block.setBlockCode(code);
        block.setAdapterModel("测试输送机");
        block.setThickness(new BigDecimal("50.00"));
        return blockRepository.save(block).getId();
    }

    /** 占用中（未还）预约：约定取用时间在未来，状态为已预约 */
    private BlockBorrowReservation createActiveReservation(Long blockId) {
        return borrowReservationService.create(borrowDto(blockId));
    }

    private BorrowReservationCreateDTO borrowDto(Long blockId) {
        BorrowReservationCreateDTO dto = new BorrowReservationCreateDTO();
        dto.setBlockId(blockId);
        dto.setTeamName("甲班");
        dto.setPickupTime(LocalDateTime.now().plusHours(2));
        dto.setPlannedReturnTime(LocalDateTime.now().plusDays(1));
        dto.setReturnPoint("交班测试01号线");
        dto.setOperator("计划员");
        return dto;
    }

    private void createPendingTransfer(Long blockId) {
        BlockTransfer transfer = new BlockTransfer();
        transfer.setTransferNo("TRF-" + System.nanoTime());
        transfer.setBlockId(blockId);
        transfer.setFromLineId(lineId);
        transfer.setToLineId(otherLineId);
        transfer.setTransferDate(LocalDate.now());
        transfer.setTransferOperator("张工");
        transfer.setStatus(BlockTransfer.STATUS_PENDING);
        transfer.setPrintCount(0);
        transfer.setReceiptPrintCount(0);
        transferRepository.saveAndFlush(transfer);
    }

    private void createPendingStocktakeItem(Long blockId) {
        StocktakeBatch batch = new StocktakeBatch();
        batch.setBatchNo("PD-" + System.nanoTime());
        batch.setLineId(lineId);
        batch.setStocktakeDate(LocalDate.now());
        batch.setOperator("盘点负责人");
        batch.setStatus(StocktakeBatch.STATUS_COUNTING);
        batch.setTotalCount(1);
        batch.setCountedCount(0);
        batch.setDiscrepancyCount(1);
        batch.setPendingCount(1);
        batch = batchRepository.saveAndFlush(batch);

        StocktakeItem item = new StocktakeItem();
        item.setBatchId(batch.getId());
        item.setBlockId(blockId);
        item.setBlockCode("BLK-HO-ST");
        item.setExpectedLineId(lineId);
        item.setIsExtra(0);
        item.setIsCounted(0);
        item.setRepeatCount(0);
        item.setDiscrepancyType(StocktakeItem.DIFF_MISSING);
        item.setDiscrepancyStatus(StocktakeItem.STATUS_PENDING);
        stocktakeItemRepository.saveAndFlush(item);
    }

    private HandoverCreateDTO createDto() {
        HandoverCreateDTO dto = new HandoverCreateDTO();
        dto.setFromTeam("甲班");
        dto.setToTeam("乙班");
        dto.setHandoverOperator("交班人老王");
        dto.setReceiveOperator("接班人小李");
        return dto;
    }
}
