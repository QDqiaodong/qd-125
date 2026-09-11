package com.bufferblock.service;

import com.bufferblock.entity.StocktakeBatch;
import com.bufferblock.entity.StocktakeSequence;
import com.bufferblock.repository.StocktakeBatchRepository;
import com.bufferblock.repository.StocktakeSequenceRepository;
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
class StocktakeNumberServiceTest {

    @Autowired
    private StocktakeNumberService stocktakeNumberService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private StocktakeBatchRepository batchRepository;
    @Autowired
    private StocktakeSequenceRepository sequenceRepository;

    @BeforeEach
    void setUp() {
        batchRepository.deleteAll();
        sequenceRepository.deleteAll();
        sequenceRepository.save(new StocktakeSequence("LOCK", 0));
    }

    @Test
    void firstNumberOfDateIsOne() {
        String no = transactionTemplate.execute(status ->
                stocktakeNumberService.nextBatchNo(LocalDate.of(2033, 3, 1)));
        assertThat(no).isEqualTo("PD-20330301-001");
    }

    @Test
    void requiresTransaction() {
        assertThatThrownBy(() -> stocktakeNumberService.nextBatchNo(LocalDate.of(2033, 3, 2)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(sequenceRepository.findById("20330302")).isEmpty();
    }

    @Test
    void rebuildsFromHistoricalMaximumWhenSequenceRowMissing() {
        batchRepository.save(batch("PD-20330303-004"));
        batchRepository.save(batch("PD-20330303-011"));
        sequenceRepository.deleteAll();
        sequenceRepository.save(new StocktakeSequence("LOCK", 0));

        String no = transactionTemplate.execute(status ->
                stocktakeNumberService.nextBatchNo(LocalDate.of(2033, 3, 3)));

        assertThat(no).isEqualTo("PD-20330303-012");
    }

    private StocktakeBatch batch(String batchNo) {
        StocktakeBatch batch = new StocktakeBatch();
        batch.setBatchNo(batchNo);
        batch.setLineId(999L);
        batch.setStocktakeDate(LocalDate.of(2033, 3, 3));
        batch.setOperator("历史盘点人");
        batch.setStatus(StocktakeBatch.STATUS_COMPLETED);
        return batch;
    }
}
