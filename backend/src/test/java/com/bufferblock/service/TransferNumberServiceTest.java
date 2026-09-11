package com.bufferblock.service;

import com.bufferblock.entity.BlockTransfer;
import com.bufferblock.entity.BlockTransferSequence;
import com.bufferblock.repository.BlockTransferRepository;
import com.bufferblock.repository.BlockTransferSequenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TransferNumberServiceTest {

    @Autowired
    private TransferNumberService transferNumberService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private BlockTransferRepository transferRepository;
    @Autowired
    private BlockTransferSequenceRepository sequenceRepository;

    @BeforeEach
    void setUp() {
        transferRepository.deleteAll();
        sequenceRepository.deleteAll();
        sequenceRepository.save(new BlockTransferSequence("LOCK", 0));
    }

    @Test
    void selectedTransferDateDeterminesNumberDate() {
        LocalDate transferDate = LocalDate.of(2031, 2, 3);

        String transferNo = transactionTemplate.execute(status ->
                transferNumberService.nextTransferNo(transferDate));

        assertThat(transferNo).isEqualTo("TRF-20310203-001");
    }

    @Test
    void requiresTransactionSoNumberCannotBeConsumedOutsideRegistration() {
        assertThatThrownBy(() -> transferNumberService.nextTransferNo(LocalDate.of(2031, 2, 4)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(sequenceRepository.findById("20310204")).isEmpty();
    }

    @Test
    void initializationUsesMaximumHistoricalSequenceWhenNumbersAreNotContiguous() {
        LocalDate transferDate = LocalDate.of(2031, 2, 5);
        transferRepository.save(transfer("TRF-20310205-002"));
        transferRepository.save(transfer("TRF-20310205-009"));
        sequenceRepository.deleteAll();
        sequenceRepository.save(new BlockTransferSequence("LOCK", 0));

        String transferNo = transactionTemplate.execute(status ->
                transferNumberService.nextTransferNo(transferDate));

        assertThat(transferNo).isEqualTo("TRF-20310205-010");
    }

    private BlockTransfer transfer(String transferNo) {
        BlockTransfer transfer = new BlockTransfer();
        transfer.setTransferNo(transferNo);
        transfer.setBlockId(1L);
        transfer.setFromLineId(2L);
        transfer.setToLineId(3L);
        transfer.setTransferDate(LocalDate.of(2031, 2, 5));
        transfer.setTransferOperator("历史操作人");
        transfer.setStatus(BlockTransfer.STATUS_CONFIRMED);
        transfer.setPrintCount(0);
        transfer.setReceiptPrintCount(0);
        return transfer;
    }
}
