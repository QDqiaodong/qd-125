package com.bufferblock.service;

import com.bufferblock.entity.BlockTransferSequence;
import com.bufferblock.repository.BlockTransferRepository;
import com.bufferblock.repository.BlockTransferSequenceRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 发号器日期行初始化。调用方必须先持有 LOCK 行，同一日期的首次建行因此被串行化。
 */
@Service
public class TransferSequenceInitializer {

    private final BlockTransferSequenceRepository sequenceRepository;
    private final BlockTransferRepository transferRepository;

    public TransferSequenceInitializer(BlockTransferSequenceRepository sequenceRepository,
                                       BlockTransferRepository transferRepository) {
        this.sequenceRepository = sequenceRepository;
        this.transferRepository = transferRepository;
    }

    /**
     * 在当前登记事务中补齐日期计数器。
     * 计数器取该日期历史单号的最大三位序号，而不是记录数量，避免历史空号后重号。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void initialize(String dateText) {
        sequenceRepository.saveAndFlush(
                new BlockTransferSequence(dateText, findCurrentMaximum(dateText)));
    }

    private Integer findCurrentMaximum(String dateText) {
        String prefix = String.format("TRF-%s-", dateText);
        return transferRepository.findByTransferNoPrefixOrderByTransferNoDesc(prefix, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(transfer -> Integer.parseInt(transfer.getTransferNo().substring(prefix.length())))
                .orElse(0);
    }
}
