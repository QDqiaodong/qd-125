package com.bufferblock.service;

import com.bufferblock.entity.BlockBorrowSequence;
import com.bufferblock.repository.BlockBorrowReservationRepository;
import com.bufferblock.repository.BlockBorrowSequenceRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 借用预约单号发号器：以业务日期为粒度，通过数据库行锁保证多实例并发唯一。
 */
@Service
public class BorrowNumberService {

    static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String INIT_LOCK_DATE = "LOCK";

    private final BlockBorrowSequenceRepository sequenceRepository;
    private final BlockBorrowReservationRepository reservationRepository;

    public BorrowNumberService(BlockBorrowSequenceRepository sequenceRepository,
                               BlockBorrowReservationRepository reservationRepository) {
        this.sequenceRepository = sequenceRepository;
        this.reservationRepository = reservationRepository;
    }

    /**
     * 生成指定取用日期的预约单号。必须在登记事务内调用：事务回滚时序号同步回滚，不留空号。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextReservationNo(LocalDate pickupDate) {
        String dateText = pickupDate.format(DATE_FORMATTER);
        sequenceRepository.findBySequenceDateForUpdate(INIT_LOCK_DATE).orElseThrow(() ->
                new IllegalStateException("借用预约发号器初始化锁不存在，请检查 V5 数据库迁移"));
        BlockBorrowSequence sequence = sequenceRepository.findBySequenceDateForUpdate(dateText)
                .orElse(null);
        if (sequence == null) {
            sequenceRepository.saveAndFlush(
                    new BlockBorrowSequence(dateText, findCurrentMaximum(dateText)));
            sequence = sequenceRepository.findBySequenceDateForUpdate(dateText).orElseThrow(() ->
                    new IllegalStateException("借用预约计数器初始化失败：" + dateText));
        }
        if (sequence.getCurrentValue() >= 999) {
            throw new RuntimeException(dateText + " 的借用预约单号流水号已达当日上限，请联系管理员");
        }
        int nextValue = sequence.getCurrentValue() + 1;
        sequence.setCurrentValue(nextValue);
        return String.format("BR-%s-%03d", dateText, nextValue);
    }

    /**
     * 计数器取该日期历史单号的最大三位序号，而不是记录数量，避免历史空号后重号。
     */
    private Integer findCurrentMaximum(String dateText) {
        String prefix = String.format("BR-%s-", dateText);
        return reservationRepository
                .findByReservationNoPrefixOrderByReservationNoDesc(prefix, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(r -> Integer.parseInt(r.getReservationNo().substring(prefix.length())))
                .orElse(0);
    }
}
