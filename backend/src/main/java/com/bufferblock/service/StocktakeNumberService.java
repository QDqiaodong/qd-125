package com.bufferblock.service;

import com.bufferblock.entity.StocktakeSequence;
import com.bufferblock.repository.StocktakeBatchRepository;
import com.bufferblock.repository.StocktakeSequenceRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 盘点批次号发号器：以业务日期为粒度（PD-yyyyMMdd-NNN），数据库行锁保证并发唯一。
 */
@Service
public class StocktakeNumberService {

    static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String INIT_LOCK_DATE = "LOCK";

    private final StocktakeSequenceRepository sequenceRepository;
    private final StocktakeBatchRepository batchRepository;

    public StocktakeNumberService(StocktakeSequenceRepository sequenceRepository,
                                  StocktakeBatchRepository batchRepository) {
        this.sequenceRepository = sequenceRepository;
        this.batchRepository = batchRepository;
    }

    /**
     * 生成指定盘点日期的批次号。必须在创建批次的事务内调用：事务回滚时序号同步回滚，不留空号。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextBatchNo(LocalDate stocktakeDate) {
        String dateText = stocktakeDate.format(DATE_FORMATTER);
        sequenceRepository.findBySequenceDateForUpdate(INIT_LOCK_DATE).orElseThrow(() ->
                new IllegalStateException("盘点批次号发号器初始化锁不存在，请检查 V4 数据库迁移"));
        StocktakeSequence sequence = sequenceRepository.findBySequenceDateForUpdate(dateText).orElse(null);
        if (sequence == null) {
            sequence = new StocktakeSequence(dateText, findCurrentMaximum(dateText));
            sequenceRepository.saveAndFlush(sequence);
        }
        if (sequence.getCurrentValue() >= 999) {
            throw new RuntimeException(dateText + " 的盘点批次号流水号已达当日上限，请联系管理员");
        }
        int nextValue = sequence.getCurrentValue() + 1;
        sequence.setCurrentValue(nextValue);
        return String.format("PD-%s-%03d", dateText, nextValue);
    }

    /**
     * 计数器取该日期历史批次号的最大三位序号，而不是记录数量，避免历史空号后重号。
     */
    private Integer findCurrentMaximum(String dateText) {
        String prefix = String.format("PD-%s-", dateText);
        return batchRepository.findByBatchNoPrefix(prefix, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(batch -> Integer.parseInt(batch.getBatchNo().substring(prefix.length())))
                .orElse(0);
    }
}
