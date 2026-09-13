package com.bufferblock.service;

import com.bufferblock.entity.StocktakeBatch;
import com.bufferblock.entity.StocktakeItem;
import com.bufferblock.repository.StocktakeBatchRepository;
import com.bufferblock.repository.StocktakeItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 盘点结束（封账）支撑服务：
 * 结束盘点时“待盘应盘行转缺失 + 批次统计重算”在独立事务中先行提交，
 * 即使随后因仍存在待处理差异而拒绝封账，缺失标记与统计也不会随回滚丢失，
 * 刷新批次明细后缺失行可见、批次条数与明细口径一致。
 */
@Service
public class StocktakeClosingSupport {

    private final StocktakeBatchRepository batchRepository;
    private final StocktakeItemRepository itemRepository;

    public StocktakeClosingSupport(StocktakeBatchRepository batchRepository,
                                   StocktakeItemRepository itemRepository) {
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
    }

    /**
     * 把剩余“待盘”的应盘行统一标记为缺失差异（待处理），并重算批次冗余统计。
     * REQUIRES_NEW 独立提交：调用方随后无论封账还是拒绝，本步落库结果都保留。
     *
     * @return 本次新标记为缺失的应盘行数
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markWaitingAsMissing(Long batchId) {
        // markWaitingAsMissing 会清空一级缓存，其后全部实体需要重新查询，避免托管态与批量更新不一致
        int turnedMissing = itemRepository.markWaitingAsMissing(batchId);
        // 同类内部调用，直接复用当前 REQUIRES_NEW 事务，与标记同批提交
        recomputeCounters(batchId);
        return turnedMissing;
    }

    /**
     * 事务内复核并封账：批次仍处于盘点中且无待处理差异时才置为已完成，
     * 复核与状态翻转同事务，避免并发补录差异后误封账。
     */
    @Transactional
    public StocktakeBatch completeBatch(Long batchId) {
        StocktakeBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("盘点批次不存在"));
        if (!StocktakeBatch.STATUS_COUNTING.equals(batch.getStatus())) {
            throw new RuntimeException("盘点批次已完成封账，请重新打开后再操作");
        }
        if (batch.getPendingCount() > 0) {
            throw new RuntimeException("仍有 " + batch.getPendingCount() + " 条待处理差异，请全部确认或忽略后再结束盘点");
        }
        batch.setStatus(StocktakeBatch.STATUS_COMPLETED);
        batch.setFinishTime(LocalDateTime.now());
        return batchRepository.save(batch);
    }

    /**
     * 重算批次冗余统计（列表展示与闭环校验共用同一口径）：
     * totalCount-应盘数；countedCount-去重已盘/盘盈数；
     * discrepancyCount-差异数；pendingCount-待处理差异数。
     * 必须在调用方事务内执行，保证录入/处理与统计同事务落库。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recomputeCounters(Long batchId) {
        StocktakeBatch batch = batchRepository.findById(batchId).orElseThrow();
        List<StocktakeItem> items = itemRepository.findByBatchIdOrderByIsExtraAscIsCountedAscIdAsc(batchId);

        int total = 0;
        int counted = 0;
        int discrepancy = 0;
        int pending = 0;
        for (StocktakeItem item : items) {
            if (Integer.valueOf(0).equals(item.getIsExtra())) {
                total++;
            }
            if (Integer.valueOf(1).equals(item.getIsCounted())) {
                counted++;
            }
            boolean waiting = Integer.valueOf(0).equals(item.getIsCounted())
                    && StocktakeItem.DIFF_NONE.equals(item.getDiscrepancyType());
            if (!waiting && !StocktakeItem.DIFF_NONE.equals(item.getDiscrepancyType())) {
                discrepancy++;
                if (StocktakeItem.STATUS_PENDING.equals(item.getDiscrepancyStatus())) {
                    pending++;
                }
            }
        }
        batch.setTotalCount(total);
        batch.setCountedCount(counted);
        batch.setDiscrepancyCount(discrepancy);
        batch.setPendingCount(pending);
        batchRepository.save(batch);
    }
}
