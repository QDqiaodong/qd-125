package com.bufferblock.service;

import com.bufferblock.entity.BlockTransferSequence;
import com.bufferblock.repository.BlockTransferSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 移交单号发号器：以业务日期为粒度，通过数据库行锁保证多实例并发唯一。
 */
@Service
public class TransferNumberService {

    static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String INIT_LOCK_DATE = "LOCK";

    private final BlockTransferSequenceRepository sequenceRepository;
    private final TransferSequenceInitializer sequenceInitializer;

    public TransferNumberService(BlockTransferSequenceRepository sequenceRepository,
                                 TransferSequenceInitializer sequenceInitializer) {
        this.sequenceRepository = sequenceRepository;
        this.sequenceInitializer = sequenceInitializer;
    }

    /**
     * 生成指定移交日期的单号。必须在登记事务内调用：事务回滚时序号同步回滚，不留空号。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextTransferNo(LocalDate transferDate) {
        String dateText = transferDate.format(DATE_FORMATTER);
        sequenceRepository.findBySequenceDateForUpdate(INIT_LOCK_DATE).orElseThrow(() ->
                new IllegalStateException("移交单号发号器初始化锁不存在，请检查 V3 数据库迁移"));
        BlockTransferSequence sequence = sequenceRepository.findBySequenceDateForUpdate(dateText)
                .orElse(null);
        if (sequence == null) {
            sequenceInitializer.initialize(dateText);
            sequence = sequenceRepository.findBySequenceDateForUpdate(dateText).orElseThrow(() ->
                    new IllegalStateException("移交单号计数器初始化失败：" + dateText));
        }
        if (sequence.getCurrentValue() >= 999) {
            throw new RuntimeException(dateText + " 的移交单号流水号已达当日上限，请联系管理员");
        }
        int nextValue = sequence.getCurrentValue() + 1;
        sequence.setCurrentValue(nextValue);
        return String.format("TRF-%s-%03d", dateText, nextValue);
    }
}
