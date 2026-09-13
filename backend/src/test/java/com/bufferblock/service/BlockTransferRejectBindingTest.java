package com.bufferblock.service;

import com.bufferblock.dto.TransferCreateDTO;
import com.bufferblock.dto.TransferHandleDTO;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 接收方驳回后，移交台账只登记“已驳回”结果，不能新增或切换挡块产线绑定。
 */
@SpringBootTest
class BlockTransferRejectBindingTest {

    @Autowired
    private BlockTransferService transferService;
    @Autowired
    private BufferBlockService bufferBlockService;
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

    private ProductionLine fromLine;
    private ProductionLine toLine;
    private BufferBlock block;

    @BeforeEach
    void setUp() {
        flowRecordRepository.deleteAll();
        transferRepository.deleteAll();
        bindingRepository.deleteAll();
        blockRepository.deleteAll();
        lineRepository.deleteAll();
        sequenceRepository.deleteAll();
        sequenceRepository.save(new BlockTransferSequence("LOCK", 0));

        fromLine = saveLine("REJECT-FROM", "驳回移出线");
        toLine = saveLine("REJECT-TO", "驳回目标线");
        block = saveBlock("BLK-REJECT-001");
        bufferBlockService.bindBlockToLine(block.getId(), fromLine.getId(), "建档员", 1);
    }

    @Test
    void rejectKeepsCurrentBindingOnSourceLineAndMarksLedgerRejected() {
        BlockTransfer pending = transferService.createTransfer(createDto());
        BlockLineBinding originalBinding =
                bindingRepository.findByBlockIdAndIsCurrent(block.getId(), 1).orElseThrow();
        long bindingCountBeforeReject = bindingRepository.count();

        transferService.rejectTransfer(pending.getId(), handle("实物规格不符，驳回"));

        BlockLineBinding currentBinding = bufferBlockService.getCurrentBinding(block.getId());
        assertThat(currentBinding.getId()).isEqualTo(originalBinding.getId());
        assertThat(currentBinding.getLineId()).isEqualTo(fromLine.getId());
        assertThat(currentBinding.getLineId()).isNotEqualTo(toLine.getId());
        assertThat(bindingRepository.count()).isEqualTo(bindingCountBeforeReject);
        assertThat(bindingRepository.findAll())
                .singleElement()
                .extracting(BlockLineBinding::getLineId, BlockLineBinding::getIsCurrent)
                .containsExactly(fromLine.getId(), 1);

        BlockTransfer ledger = transferRepository.findById(pending.getId()).orElseThrow();
        assertThat(ledger.getStatus()).isEqualTo(BlockTransfer.STATUS_REJECTED);
        assertThat(ledger.getFromLineId()).isEqualTo(fromLine.getId());
        assertThat(ledger.getToLineId()).isEqualTo(toLine.getId());
        assertThat(ledger.getHandleTime()).isNotNull();

        assertThat(bufferBlockService.getById(block.getId()).getLineId()).isEqualTo(fromLine.getId());
        assertThat(transferService.getTransfersByBlockId(block.getId()))
                .singleElement()
                .extracting(BlockTransfer::getTransferNo, BlockTransfer::getStatus,
                        BlockTransfer::getFromLineId, BlockTransfer::getToLineId)
                .containsExactly(pending.getTransferNo(), BlockTransfer.STATUS_REJECTED,
                        fromLine.getId(), toLine.getId());
    }

    @Test
    void concurrentConfirmAndRejectCannotLeaveRejectedLedgerWithTargetBinding() throws Exception {
        BlockTransfer pending = transferService.createTransfer(createDto());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<String> reject = () -> transferService.rejectTransfer(
                    pending.getId(), handle("实物不符，并发驳回")).getStatus();
            Callable<String> confirm = () -> transferService.confirmTransfer(
                    pending.getId(), handle("核对无误，并发确认")).getStatus();

            Future<String> first = executor.submit(reject);
            Future<String> second = executor.submit(confirm);

            int succeeded = 0;
            int rejectedByStatus = 0;
            for (Future<String> future : List.of(first, second)) {
                try {
                    String status = future.get();
                    succeeded++;
                    if (BlockTransfer.STATUS_REJECTED.equals(status)) {
                        rejectedByStatus++;
                    }
                } catch (Exception expected) {
                    // 后拿到单据行锁的请求必须看到已办结状态并失败，不能再覆盖结果或绑定
                }
            }

            assertThat(succeeded).isEqualTo(1);
            BlockTransfer ledger = transferRepository.findById(pending.getId()).orElseThrow();
            Long currentLineId = bufferBlockService.getCurrentBinding(block.getId()).getLineId();
            if (BlockTransfer.STATUS_REJECTED.equals(ledger.getStatus())) {
                assertThat(rejectedByStatus).isEqualTo(1);
                assertThat(currentLineId).isEqualTo(fromLine.getId());
                assertThat(flowRecordRepository.findByTransferIdOrderByCreateTimeAscIdAsc(pending.getId()))
                        .last()
                        .extracting(TransferFlowRecord::getAction)
                        .isEqualTo(TransferFlowRecord.ACTION_REJECT);
            } else {
                assertThat(ledger.getStatus()).isEqualTo(BlockTransfer.STATUS_CONFIRMED);
                assertThat(currentLineId).isEqualTo(toLine.getId());
                assertThat(flowRecordRepository.findByTransferIdOrderByCreateTimeAscIdAsc(pending.getId()))
                        .last()
                        .extracting(TransferFlowRecord::getAction)
                        .isEqualTo(TransferFlowRecord.ACTION_CONFIRM);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectRecordsRejectFlowWithoutTransferBindingRecord() {
        BlockTransfer pending = transferService.createTransfer(createDto());

        transferService.rejectTransfer(pending.getId(), handle("驳回原因已核对"));

        List<TransferFlowRecord> records =
                flowRecordRepository.findByTransferIdOrderByCreateTimeAscIdAsc(pending.getId());
        assertThat(records).extracting(TransferFlowRecord::getAction)
                .containsExactly(TransferFlowRecord.ACTION_REGISTER, TransferFlowRecord.ACTION_REJECT);
        assertThat(records.get(1))
                .extracting(TransferFlowRecord::getFromStatus, TransferFlowRecord::getToStatus,
                        TransferFlowRecord::getOperator, TransferFlowRecord::getNote)
                .containsExactly(BlockTransfer.STATUS_PENDING, BlockTransfer.STATUS_REJECTED,
                        "接收员", "驳回原因已核对");
        assertThat(bindingRepository.findAll())
                .singleElement()
                .extracting(BlockLineBinding::getLineId, BlockLineBinding::getBindType)
                .containsExactly(fromLine.getId(), 1);
    }

    private ProductionLine saveLine(String code, String name) {
        ProductionLine line = new ProductionLine();
        line.setLineCode(code);
        line.setLineName(name);
        return lineRepository.save(line);
    }

    private BufferBlock saveBlock(String code) {
        BufferBlock savedBlock = new BufferBlock();
        savedBlock.setBlockCode(code);
        savedBlock.setAdapterModel("驳回测试输送机");
        savedBlock.setThickness(BigDecimal.TEN);
        return blockRepository.save(savedBlock);
    }

    private TransferHandleDTO handle(String note) {
        TransferHandleDTO handle = new TransferHandleDTO();
        handle.setReceiveOperator("接收员");
        handle.setHandleNote(note);
        return handle;
    }

    private TransferCreateDTO createDto() {
        TransferCreateDTO dto = new TransferCreateDTO();
        dto.setBlockId(block.getId());
        dto.setFromLineId(fromLine.getId());
        dto.setToLineId(toLine.getId());
        dto.setTransferDate(LocalDate.now());
        dto.setTransferOperator("移交员");
        dto.setReceiveOperator("接收员");
        dto.setTransferReason("驳回绑定回归测试");
        return dto;
    }
}
