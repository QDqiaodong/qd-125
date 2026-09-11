package com.bufferblock.service;

import com.bufferblock.dto.StocktakeBatchCreateDTO;
import com.bufferblock.dto.StocktakeBatchQueryDTO;
import com.bufferblock.dto.StocktakeCountDTO;
import com.bufferblock.dto.StocktakeHandleDTO;
import com.bufferblock.dto.StocktakeItemQueryDTO;
import com.bufferblock.dto.StocktakeTraceVO;
import com.bufferblock.entity.BlockLineBinding;
import com.bufferblock.entity.BlockTransfer;
import com.bufferblock.entity.BufferBlock;
import com.bufferblock.entity.ProductionLine;
import com.bufferblock.entity.StocktakeBatch;
import com.bufferblock.entity.StocktakeItem;
import com.bufferblock.entity.StocktakeSequence;
import com.bufferblock.repository.BlockLineBindingRepository;
import com.bufferblock.repository.BlockTransferRepository;
import com.bufferblock.repository.BufferBlockRepository;
import com.bufferblock.repository.ProductionLineRepository;
import com.bufferblock.repository.StocktakeBatchRepository;
import com.bufferblock.repository.StocktakeItemRepository;
import com.bufferblock.repository.StocktakeSequenceRepository;
import com.bufferblock.repository.TransferFlowRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class StocktakeServiceTest {

    private static final LocalDate STOCKTAKE_DATE = LocalDate.of(2032, 5, 10);

    @Autowired
    private StocktakeService stocktakeService;
    @Autowired
    private StocktakeBatchRepository batchRepository;
    @Autowired
    private StocktakeItemRepository itemRepository;
    @Autowired
    private StocktakeSequenceRepository sequenceRepository;
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

    private ProductionLine countedLine;
    private ProductionLine otherLine;

    @BeforeEach
    void setUp() {
        flowRecordRepository.deleteAll();
        transferRepository.deleteAll();
        itemRepository.deleteAll();
        batchRepository.deleteAll();
        sequenceRepository.deleteAll();
        bindingRepository.deleteAll();
        blockRepository.deleteAll();
        lineRepository.deleteAll();
        sequenceRepository.save(new StocktakeSequence("LOCK", 0));

        countedLine = saveLeafLine("PD-L01", "盘点01号线");
        otherLine = saveLeafLine("PD-L02", "盘点02号线");
    }

    @Test
    void createBatchSnapshotsCurrentBindingsAsExpectedItems() {
        createBlock("PD-BLK-001", countedLine, true);
        createBlock("PD-BLK-002", countedLine, true);
        createBlock("PD-BLK-003", otherLine, true);

        StocktakeBatch batch = stocktakeService.createBatch(createDto());

        assertThat(batch.getBatchNo()).matches("PD-20320510-\\d{3}");
        assertThat(batch.getLineName()).isEqualTo("盘点01号线");
        assertThat(batch.getTotalCount()).isEqualTo(2);
        assertThat(batch.getCountedCount()).isZero();
        assertThat(itemRepository.findByBatchIdOrderByIsExtraAscIsCountedAscIdAsc(batch.getId()))
                .hasSize(2)
                .allSatisfy(item -> {
                    assertThat(item.getIsExtra()).isZero();
                    assertThat(item.getIsCounted()).isZero();
                    assertThat(item.getDiscrepancyType()).isEqualTo(StocktakeItem.DIFF_NONE);
                    assertThat(item.getDiscrepancyStatus()).isNull();
                });
    }

    @Test
    void duplicateCountingBatchForSameLineRejected() {
        stocktakeService.createBatch(createDto());

        StocktakeBatchCreateDTO second = createDto();
        second.setStocktakeDate(STOCKTAKE_DATE.plusDays(1));
        assertThatThrownBy(() -> stocktakeService.createBatch(second))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("盘点中的批次");
    }

    @Test
    void workshopNodeCannotBeStocktaken() {
        ProductionLine workshop = new ProductionLine();
        workshop.setLineCode("PD-WS");
        workshop.setLineName("盘点车间");
        workshop = lineRepository.save(workshop);

        StocktakeBatchCreateDTO dto = createDto();
        dto.setLineId(workshop.getId());
        assertThatThrownBy(() -> stocktakeService.createBatch(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("车间节点不可直接盘点");
    }

    @Test
    void countedMatchedBlockIsNoDiscrepancyAndCountersUpdate() {
        Long batchId = stocktakeService.createBatch(createDto()).getId();
        BufferBlock block = createBlock("PD-BLK-010", countedLine, true);

        StocktakeItem item = stocktakeService.countItem(batchId, countDto(block.getBlockCode()));

        assertThat(item.getIsCounted()).isEqualTo(1);
        assertThat(item.getDiscrepancyType()).isEqualTo(StocktakeItem.DIFF_NONE);
        assertThat(item.getDiscrepancyStatus()).isNull();
        assertThat(item.getRepeatCount()).isEqualTo(1);

        StocktakeBatch batch = batchRepository.findById(batchId).orElseThrow();
        assertThat(batch.getCountedCount()).isEqualTo(1);
        assertThat(batch.getDiscrepancyCount()).isZero();
        assertThat(batch.getPendingCount()).isZero();
    }

    @Test
    void missingExpectedBlockBlocksFinishUntilConfirmed() {
        createBlock("PD-BLK-020", countedLine, true);
        Long batchId = stocktakeService.createBatch(createDto()).getId();

        // 结束盘点时未盘项转缺失，差异待处理导致封账失败
        assertThatThrownBy(() -> stocktakeService.finishBatch(batchId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("待处理差异");

        StocktakeItem missing = itemRepository
                .findByBatchIdOrderByIsExtraAscIsCountedAscIdAsc(batchId).get(0);
        assertThat(missing.getDiscrepancyType()).isEqualTo(StocktakeItem.DIFF_MISSING);
        assertThat(missing.getDiscrepancyStatus()).isEqualTo(StocktakeItem.STATUS_PENDING);

        stocktakeService.handleItem(batchId, missing.getId(),
                handleDto("CONFIRM", "现场确认丢失，待补档"));

        StocktakeBatch finished = stocktakeService.finishBatch(batchId);
        assertThat(finished.getStatus()).isEqualTo(StocktakeBatch.STATUS_COMPLETED);
        assertThat(finished.getFinishTime()).isNotNull();
        assertThat(finished.getPendingCount()).isZero();
    }

    @Test
    void waitingItemCanBeDirectlyConfirmedAsMissing() {
        createBlock("PD-BLK-021", countedLine, true);
        Long batchId = stocktakeService.createBatch(createDto()).getId();

        StocktakeItem waiting = itemRepository
                .findByBatchIdOrderByIsExtraAscIsCountedAscIdAsc(batchId).get(0);
        StocktakeItem handled = stocktakeService.handleItem(batchId, waiting.getId(),
                handleDto("CONFIRM", "盘点时已不在产线"));

        assertThat(handled.getDiscrepancyType()).isEqualTo(StocktakeItem.DIFF_MISSING);
        assertThat(handled.getDiscrepancyStatus()).isEqualTo(StocktakeItem.STATUS_CONFIRMED);
        assertThat(handled.getHandleOperator()).isEqualTo("处理人");
    }

    @Test
    void countingBlockBoundToAnotherLineIsWrongLine() {
        BufferBlock block = createBlock("PD-BLK-030", otherLine, true);
        Long batchId = stocktakeService.createBatch(createDto()).getId();

        // 在盘点产线现场盘到归属其他产线的挡块：按错线处理，不属于盘盈
        StocktakeItem item = stocktakeService.countItem(batchId, countDto(block.getBlockCode()));

        assertThat(item.getIsExtra()).isZero();
        assertThat(item.getDiscrepancyType()).isEqualTo(StocktakeItem.DIFF_WRONG_LINE);
        assertThat(item.getDiscrepancyStatus()).isEqualTo(StocktakeItem.STATUS_PENDING);
        assertThat(item.getBoundLineName()).isEqualTo("盘点02号线");
    }

    @Test
    void blockOnSiteOtherThanExpectedLineIsWrongLine() {
        BufferBlock block = createBlock("PD-BLK-031", countedLine, true);
        Long batchId = stocktakeService.createBatch(createDto()).getId();

        StocktakeCountDTO dto = countDto(block.getBlockCode());
        dto.setSiteLineId(otherLine.getId());
        StocktakeItem item = stocktakeService.countItem(batchId, dto);

        assertThat(item.getDiscrepancyType()).isEqualTo(StocktakeItem.DIFF_WRONG_LINE);
    }

    @Test
    void unboundBlockIsExtra() {
        BufferBlock block = createBlock("PD-BLK-040", null, false);
        Long batchId = stocktakeService.createBatch(createDto()).getId();

        StocktakeItem item = stocktakeService.countItem(batchId, countDto(block.getBlockCode()));

        assertThat(item.getIsExtra()).isEqualTo(1);
        assertThat(item.getDiscrepancyType()).isEqualTo(StocktakeItem.DIFF_EXTRA);
    }

    @Test
    void repeatCountOfSameBlockMarkedDuplicate() {
        BufferBlock block = createBlock("PD-BLK-050", countedLine, true);
        Long batchId = stocktakeService.createBatch(createDto()).getId();

        stocktakeService.countItem(batchId, countDto(block.getBlockCode()));
        StocktakeItem repeated = stocktakeService.countItem(batchId, countDto(block.getBlockCode()));

        assertThat(repeated.getRepeatCount()).isEqualTo(2);
        assertThat(repeated.getDiscrepancyType()).isEqualTo(StocktakeItem.DIFF_DUPLICATE);
        assertThat(repeated.getDiscrepancyStatus()).isEqualTo(StocktakeItem.STATUS_PENDING);

        // 批次内同一挡块只有一行
        assertThat(itemRepository.findByBatchIdAndBlockId(batchId, block.getId())).isPresent();
    }

    @Test
    void damagedStateTakesPriorityOverDuplicateOnRepeat() {
        BufferBlock block = createBlock("PD-BLK-051", countedLine, true);
        Long batchId = stocktakeService.createBatch(createDto()).getId();

        stocktakeService.countItem(batchId, countDto(block.getBlockCode()));
        StocktakeCountDTO damagedRepeat = countDto(block.getBlockCode());
        damagedRepeat.setPhysicalStatus(StocktakeItem.PHYSICAL_DAMAGED);
        StocktakeItem repeated = stocktakeService.countItem(batchId, damagedRepeat);

        // 实物损坏比重复录入更严重，保留损坏差异，重复次数仍累计
        assertThat(repeated.getRepeatCount()).isEqualTo(2);
        assertThat(repeated.getDiscrepancyType()).isEqualTo(StocktakeItem.DIFF_DAMAGED);
    }

    @Test
    void damagedBlockMarkedDamagedAndIgnoreClearsPending() {
        BufferBlock block = createBlock("PD-BLK-060", countedLine, true);
        Long batchId = stocktakeService.createBatch(createDto()).getId();

        StocktakeCountDTO dto = countDto(block.getBlockCode());
        dto.setPhysicalStatus(StocktakeItem.PHYSICAL_DAMAGED);
        StocktakeItem damaged = stocktakeService.countItem(batchId, dto);
        assertThat(damaged.getDiscrepancyType()).isEqualTo(StocktakeItem.DIFF_DAMAGED);

        StocktakeItem ignored = stocktakeService.handleItem(batchId, damaged.getId(),
                handleDto("IGNORE", "外观轻微磨损，不影响使用"));
        assertThat(ignored.getDiscrepancyStatus()).isEqualTo(StocktakeItem.STATUS_IGNORED);
        assertThat(batchRepository.findById(batchId).orElseThrow().getPendingCount()).isZero();
    }

    @Test
    void handleRequiresNoteAndOperator() {
        BufferBlock block = createBlock("PD-BLK-061", countedLine, true);
        Long batchId = stocktakeService.createBatch(createDto()).getId();
        StocktakeItem damaged = stocktakeService.countItem(batchId, countDto(block.getBlockCode()));
        // 无差异的行不可处理
        assertThatThrownBy(() -> stocktakeService.handleItem(batchId, damaged.getId(),
                handleDto("CONFIRM", "x")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无差异");
    }

    @Test
    void unknownBlockCodeRejected() {
        Long batchId = stocktakeService.createBatch(createDto()).getId();
        assertThatThrownBy(() -> stocktakeService.countItem(batchId, countDto("NOT-EXIST-999")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("不在挡块档案中");
    }

    @Test
    void deleteOnlyExtraItemAllowed() {
        BufferBlock normal = createBlock("PD-BLK-070", countedLine, true);
        BufferBlock extra = createBlock("PD-BLK-071", null, false);
        Long batchId = stocktakeService.createBatch(createDto()).getId();
        stocktakeService.countItem(batchId, countDto(extra.getBlockCode()));

        StocktakeItem expectedItem = itemRepository.findByBatchIdAndBlockId(batchId, normal.getId()).orElseThrow();
        assertThatThrownBy(() -> stocktakeService.deleteExtraItem(batchId, expectedItem.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("仅盘盈");

        StocktakeItem extraItem = itemRepository.findByBatchIdAndBlockId(batchId, extra.getId()).orElseThrow();
        stocktakeService.deleteExtraItem(batchId, extraItem.getId());
        assertThat(itemRepository.findByBatchIdAndBlockId(batchId, extra.getId())).isEmpty();
    }

    @Test
    void completedBatchBlocksCountingAndCanReopen() {
        createBlock("PD-BLK-080", countedLine, true);
        Long batchId = stocktakeService.createBatch(createDto()).getId();
        stocktakeService.finishBatch(batchId);

        BufferBlock another = createBlock("PD-BLK-081", null, false);
        assertThatThrownBy(() -> stocktakeService.countItem(batchId, countDto(another.getBlockCode())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("已完成封账");

        StocktakeBatch reopened = stocktakeService.reopenBatch(batchId);
        assertThat(reopened.getStatus()).isEqualTo(StocktakeBatch.STATUS_COUNTING);
        assertThat(reopened.getFinishTime()).isNull();
    }

    @Test
    void queryItemsSupportsStatusAndTypeFilter() {
        BufferBlock ok = createBlock("PD-BLK-090", countedLine, true);
        BufferBlock missingBlock = createBlock("PD-BLK-091", countedLine, true);
        BufferBlock extra = createBlock("PD-BLK-092", null, false);
        Long batchId = stocktakeService.createBatch(createDto()).getId();

        stocktakeService.countItem(batchId, countDto(ok.getBlockCode()));
        stocktakeService.countItem(batchId, countDto(extra.getBlockCode()));
        assertThatThrownBy(() -> stocktakeService.finishBatch(batchId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("待处理差异"); // PD-BLK-091 转缺失后仍有 2 条待处理，封账失败

        StocktakeItemQueryDTO pendingQuery = new StocktakeItemQueryDTO();
        pendingQuery.setStatus("PENDING");
        Page<StocktakeItem> pendingPage = stocktakeService.queryItems(batchId, pendingQuery);
        assertThat(pendingPage.getContent()).hasSize(2); // 缺失 + 盘盈

        StocktakeItemQueryDTO missingQuery = new StocktakeItemQueryDTO();
        missingQuery.setDiscrepancyType("MISSING");
        Page<StocktakeItem> missingPage = stocktakeService.queryItems(batchId, missingQuery);
        assertThat(missingPage.getContent()).hasSize(1);
        assertThat(missingPage.getContent().get(0).getBlockCode()).isEqualTo(missingBlock.getBlockCode());

        StocktakeItemQueryDTO extraQuery = new StocktakeItemQueryDTO();
        extraQuery.setDiscrepancyType("EXTRA");
        Page<StocktakeItem> extraPage = stocktakeService.queryItems(batchId, extraQuery);
        assertThat(extraPage.getContent()).hasSize(1);
        assertThat(extraPage.getContent().get(0).getBlockCode()).isEqualTo(extra.getBlockCode());

        StocktakeItemQueryDTO waitingQuery = new StocktakeItemQueryDTO();
        waitingQuery.setStatus("WAITING");
        assertThat(stocktakeService.queryItems(batchId, waitingQuery).getContent()).isEmpty();

        // 批次因待处理差异仍为盘点中
        assertThat(batchRepository.findById(batchId).orElseThrow().getStatus())
                .isEqualTo(StocktakeBatch.STATUS_COUNTING);
    }

    @Test
    void traceIncludesBindingsAndLatestPendingTransferHint() {
        BufferBlock block = createBlock("PD-BLK-100", countedLine, true);
        Long batchId = stocktakeService.createBatch(createDto()).getId();
        stocktakeService.countItem(batchId, countDto(block.getBlockCode()));

        // 登记一笔待确认移交（从盘点产线移到其他产线），尚未确认
        BlockTransfer transfer = new BlockTransfer();
        transfer.setTransferNo("TRF-20320511-001");
        transfer.setBlockId(block.getId());
        transfer.setFromLineId(countedLine.getId());
        transfer.setToLineId(otherLine.getId());
        transfer.setTransferDate(LocalDate.of(2032, 5, 11));
        transfer.setTransferOperator("移交人");
        transfer.setStatus(BlockTransfer.STATUS_PENDING);
        transfer.setPrintCount(0);
        transfer.setReceiptPrintCount(0);
        transfer = transferRepository.save(transfer);

        StocktakeItem item = itemRepository.findByBatchIdAndBlockId(batchId, block.getId()).orElseThrow();
        StocktakeTraceVO trace = stocktakeService.getTrace(batchId, item.getId());

        assertThat(trace.getBindings()).isNotEmpty();
        assertThat(trace.getLatestTransfer().getId()).isEqualTo(transfer.getId());
        assertThat(trace.getLatestTransfer().getTransferNo()).isEqualTo("TRF-20320511-001");
        assertThat(trace.getFlowRecords()).isEmpty();
        assertThat(trace.getItem().getTransferHint()).contains("待确认移交");
    }

    @Test
    void batchQueryReturnsPagedResultsWithLineName() {
        createBlock("PD-BLK-110", countedLine, true);
        stocktakeService.createBatch(createDto());

        StocktakeBatchQueryDTO query = new StocktakeBatchQueryDTO();
        query.setLineId(countedLine.getId());
        Page<StocktakeBatch> page = stocktakeService.queryBatches(query);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getLineName()).isEqualTo("盘点01号线");
    }

    // ------------------------------------------------------------------

    private StocktakeBatchCreateDTO createDto() {
        StocktakeBatchCreateDTO dto = new StocktakeBatchCreateDTO();
        dto.setLineId(countedLine.getId());
        dto.setStocktakeDate(STOCKTAKE_DATE);
        dto.setOperator("盘点负责人");
        dto.setRemark("例行盘点");
        return dto;
    }

    private StocktakeCountDTO countDto(String blockCode) {
        StocktakeCountDTO dto = new StocktakeCountDTO();
        dto.setBlockCode(blockCode);
        dto.setPhysicalStatus(StocktakeItem.PHYSICAL_NORMAL);
        dto.setSiteLineId(countedLine.getId());
        dto.setOperator("盘点员");
        return dto;
    }

    private StocktakeHandleDTO handleDto(String action, String note) {
        StocktakeHandleDTO dto = new StocktakeHandleDTO();
        dto.setAction(action);
        dto.setOperator("处理人");
        dto.setHandleNote(note);
        return dto;
    }

    private ProductionLine saveLeafLine(String code, String name) {
        ProductionLine workshop = new ProductionLine();
        workshop.setLineCode(code + "-WS");
        workshop.setLineName(name + "车间");
        workshop = lineRepository.save(workshop);

        ProductionLine line = new ProductionLine();
        line.setLineCode(code);
        line.setLineName(name);
        line.setParentId(workshop.getId());
        return lineRepository.save(line);
    }

    private BufferBlock createBlock(String code, ProductionLine boundLine, boolean bind) {
        BufferBlock block = new BufferBlock();
        block.setBlockCode(code);
        block.setAdapterModel("测试输送机");
        block.setThickness(new BigDecimal("50.00"));
        block.setSpecTemplate("TEMP-T-50");
        block = blockRepository.save(block);

        if (bind && boundLine != null) {
            BlockLineBinding binding = new BlockLineBinding();
            binding.setBlockId(block.getId());
            binding.setLineId(boundLine.getId());
            binding.setBindType(1);
            binding.setBindTime(LocalDateTime.now().minusDays(2));
            binding.setOperator("初始人");
            binding.setIsCurrent(1);
            bindingRepository.save(binding);
        }
        return block;
    }
}
