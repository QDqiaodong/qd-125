package com.bufferblock.service;

import com.bufferblock.entity.ShiftHandoverSequence;
import com.bufferblock.repository.ShiftHandoverRepository;
import com.bufferblock.repository.ShiftHandoverSequenceRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 班组交班单号发号器：以业务日期为粒度，通过数据库行锁保证多实例并发唯一。
 */
@Service
public class HandoverNumberService {

    static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String INIT_LOCK_DATE = "LOCK";

    private final ShiftHandoverSequenceRepository sequenceRepository;
    private final ShiftHandoverRepository handoverRepository;

    public HandoverNumberService(ShiftHandoverSequenceRepository sequenceRepository,
                                 ShiftHandoverRepository handoverRepository) {
        this.sequenceRepository = sequenceRepository;
        this.handoverRepository = handoverRepository;
    }

    /**
     * 生成指定日期的交班单号。必须在登记事务内调用：事务回滚时序号同步回滚，不留空号。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextHandoverNo(LocalDate handoverDate) {
        String dateText = handoverDate.format(DATE_FORMATTER);
        sequenceRepository.findBySequenceDateForUpdate(INIT_LOCK_DATE).orElseThrow(() ->
                new IllegalStateException("班组交班发号器初始化锁不存在，请检查 V6 数据库迁移"));
        ShiftHandoverSequence sequence = sequenceRepository.findBySequenceDateForUpdate(dateText)
                .orElse(null);
        if (sequence == null) {
            sequenceRepository.saveAndFlush(
                    new ShiftHandoverSequence(dateText, findCurrentMaximum(dateText)));
            sequence = sequenceRepository.findBySequenceDateForUpdate(dateText).orElseThrow(() ->
                    new IllegalStateException("班组交班计数器初始化失败：" + dateText));
        }
        if (sequence.getCurrentValue() >= 999) {
            throw new RuntimeException(dateText + " 的班组交班单号流水号已达当日上限，请联系管理员");
        }
        int nextValue = sequence.getCurrentValue() + 1;
        sequence.setCurrentValue(nextValue);
        return String.format("HO-%s-%03d", dateText, nextValue);
    }

    /**
     * 计数器取该日期历史单号的最大三位序号，而不是记录数量，避免历史空号后重号。
     */
    private Integer findCurrentMaximum(String dateText) {
        String prefix = String.format("HO-%s-", dateText);
        return handoverRepository
                .findByHandoverNoPrefixOrderByHandoverNoDesc(prefix, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(h -> Integer.parseInt(h.getHandoverNo().substring(prefix.length())))
                .orElse(0);
    }
}
