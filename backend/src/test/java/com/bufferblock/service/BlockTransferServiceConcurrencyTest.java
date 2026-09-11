package com.bufferblock.service;

import com.bufferblock.dto.TransferCreateDTO;
import com.bufferblock.entity.BlockLineBinding;
import com.bufferblock.entity.BlockTransfer;
import com.bufferblock.entity.BlockTransferSequence;
import com.bufferblock.entity.BufferBlock;
import com.bufferblock.entity.ProductionLine;
import com.bufferblock.entity.TransferFlowRecord;
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
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest
class BlockTransferServiceConcurrencyTest {

    private static final LocalDate CONCURRENT_DATE = LocalDate.of(2030, 1, 2);
    private static final LocalDate RESTART_DATE = LocalDate.of(2030, 1, 3);
    private static final Pattern TRANSFER_NO_SEQUENCE = Pattern.compile("TRF-\\d{8}-(\\d{3})");

    @Autowired
    private BlockTransferService transferService;
    @Autowired
    private BlockTransferRepository transferRepository;
    @Autowired
    private BufferBlockRepository blockRepository;
    @Autowired
    private ProductionLineRepository lineRepository;
    @Autowired
    private BlockLineBindingRepository bindingRepository;
    @Autowired
    private TransferFlowRecordRepository flowRecordRepository;
    @Autowired
    private BlockTransferSequenceRepository sequenceRepository;

    @SpyBean
    private TransferFlowRecordRepository spyFlowRecordRepository;

    private ProductionLine fromLine;
    private ProductionLine toLine;

    @BeforeEach
    void setUp() {
        flowRecordRepository.deleteAll();
        transferRepository.deleteAll();
        bindingRepository.deleteAll();
        blockRepository.deleteAll();
        sequenceRepository.deleteAll();
        lineRepository.deleteAll();
        sequenceRepository.save(new BlockTransferSequence("LOCK", 0));

        fromLine = saveLine("FROM-2030", "移出产线");
        toLine = saveLine("TO-2030", "移入产线");
    }

    @Test
    void concurrentCreatesSameDateProduceStableUniqueContiguousNumbers() throws Exception {
        int total = 12;
        List<Long> blockIds = createBlocks(total);

        ExecutorService executor = Executors.newFixedThreadPool(total);
        try {
            List<Future<BlockTransfer>> futures = new ArrayList<>();
            for (Long blockId : blockIds) {
                futures.add(executor.submit(() -> transferService.createTransfer(
                        createDto(blockId, CONCURRENT_DATE, "移交人" + blockId))));
            }

            Set<String> transferNos = collectSuccessfulTransferNos(futures);

            assertThat(transferNos).hasSize(total);
            assertThat(extractSequences(transferNos))
                    .containsExactlyElementsOf(IntStream.rangeClosed(1, total).boxed().collect(Collectors.toList()));
            assertThat(transferRepository.count()).isEqualTo(total);
            assertThat(flowRecordRepository.count()).isEqualTo(total);
            assertThat(sequenceRepository.findById(dateKey(CONCURRENT_DATE)).orElseThrow().getCurrentValue())
                    .isEqualTo(total);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void missingDailyCounterAfterRestartIsRebuiltAndNextNumberContinues() {
        BlockTransfer existing = transferRepository.save(
                transferWithoutId("TRF-20300103-007", 101L));
        BlockTransfer gapRecord = transferRepository.save(
                transferWithoutId("TRF-20300103-003", 102L));
        sequenceRepository.deleteAll();
        sequenceRepository.save(new BlockTransferSequence("LOCK", 0));
        Long blockId = createBlocks(1).get(0);

        BlockTransfer created = transferService.createTransfer(createDto(blockId, RESTART_DATE, "重启后移交人"));

        assertThat(created.getTransferNo()).isEqualTo("TRF-20300103-008");
        assertThat(transferRepository.existsById(existing.getId())).isTrue();
        assertThat(transferRepository.existsById(gapRecord.getId())).isTrue();
        assertThat(sequenceRepository.findById(dateKey(RESTART_DATE)).orElseThrow().getCurrentValue()).isEqualTo(8);
    }

    @Test
    void duplicateRequestForSameBlockLeavesOnlyOnePendingTransferAndOneFlowRecord() throws Exception {
        Long blockId = createBlocks(1).get(0);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<BlockTransfer> task = () -> transferService.createTransfer(
                    createDto(blockId, LocalDate.of(2030, 1, 4), "重复点击移交人"));
            Future<BlockTransfer> first = executor.submit(task);
            Future<BlockTransfer> second = executor.submit(task);

            int success = 0;
            int failure = 0;
            for (Future<BlockTransfer> future : List.of(first, second)) {
                try {
                    future.get();
                    success++;
                } catch (Exception e) {
                    failure++;
                }
            }

            assertThat(success).isEqualTo(1);
            assertThat(failure).isEqualTo(1);
            assertThat(transferRepository.count()).isEqualTo(1);
            assertThat(flowRecordRepository.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void databaseFailureAfterTransferInsertRollsBackTransferNumberAndFlowRecord() {
        LocalDate transferDate = LocalDate.of(2030, 1, 5);
        Long blockId = createBlocks(1).get(0);
        doThrow(new RuntimeException("模拟流转记录落库失败"))
                .when(spyFlowRecordRepository).saveAndFlush(any(TransferFlowRecord.class));

        try {
            assertThatThrownBy(() -> transferService.createTransfer(
                    createDto(blockId, transferDate, "失败移交人")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("模拟流转记录落库失败");

            assertThat(transferRepository.count()).isZero();
            assertThat(flowRecordRepository.count()).isZero();
        } finally {
            reset(spyFlowRecordRepository);
        }

        BlockTransfer retry = transferService.createTransfer(createDto(blockId, transferDate, "重试移交人"));

        assertThat(retry.getTransferNo()).isEqualTo("TRF-20300105-001");
        assertThat(sequenceRepository.findById(dateKey(transferDate)).orElseThrow().getCurrentValue())
                .isEqualTo(1);
    }

    private Set<String> collectSuccessfulTransferNos(List<Future<BlockTransfer>> futures) throws Exception {
        Set<String> numbers = new java.util.HashSet<>();
        for (Future<BlockTransfer> future : futures) {
            numbers.add(future.get().getTransferNo());
        }
        return numbers;
    }

    private List<Integer> extractSequences(Set<String> transferNos) {
        return transferNos.stream()
                .map(TRANSFER_NO_SEQUENCE::matcher)
                .filter(Matcher::matches)
                .map(matcher -> Integer.parseInt(matcher.group(1)))
                .sorted()
                .collect(Collectors.toList());
    }

    private List<Long> createBlocks(int count) {
        List<Long> blockIds = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            BufferBlock block = new BufferBlock();
            block.setBlockCode("BLK-2030-" + String.format("%03d", i));
            block.setAdapterModel("测试输送机");
            block.setThickness(BigDecimal.TEN);
            block = blockRepository.save(block);

            BlockLineBinding binding = new BlockLineBinding();
            binding.setBlockId(block.getId());
            binding.setLineId(fromLine.getId());
            binding.setBindType(1);
            binding.setBindTime(java.time.LocalDateTime.now());
            binding.setIsCurrent(1);
            bindingRepository.save(binding);
            blockIds.add(block.getId());
        }
        return blockIds;
    }

    private ProductionLine saveLine(String code, String name) {
        ProductionLine line = new ProductionLine();
        line.setLineCode(code);
        line.setLineName(name);
        return lineRepository.save(line);
    }

    private TransferCreateDTO createDto(Long blockId, LocalDate date, String operator) {
        TransferCreateDTO dto = new TransferCreateDTO();
        dto.setBlockId(blockId);
        dto.setFromLineId(fromLine.getId());
        dto.setToLineId(toLine.getId());
        dto.setTransferDate(date);
        dto.setTransferOperator(operator);
        return dto;
    }

    private BlockTransfer transferWithoutId(String transferNo, Long blockId) {
        BlockTransfer transfer = new BlockTransfer();
        transfer.setTransferNo(transferNo);
        transfer.setBlockId(blockId);
        transfer.setFromLineId(fromLine.getId());
        transfer.setToLineId(toLine.getId());
        transfer.setTransferDate(RESTART_DATE);
        transfer.setTransferOperator("历史移交人");
        transfer.setStatus(BlockTransfer.STATUS_CONFIRMED);
        transfer.setPrintCount(0);
        transfer.setReceiptPrintCount(0);
        return transfer;
    }

    private String dateKey(LocalDate date) {
        return date.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
    }
}
