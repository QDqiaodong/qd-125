package com.bufferblock.repository;

import com.bufferblock.entity.StocktakeItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StocktakeItemRepository extends JpaRepository<StocktakeItem, Long> {

    /**
     * 盘点分明细分页查询。
     *
     * <p>有效差异类型 MISSING 同时覆盖“已标记的缺失记录”与“应盘未盘（未录入、无差异类型）”的行；
     * 有效处理状态 WAITING 对应“待盘”行（未录入且无差异），已盘但无差异的行有效状态为 NONE。</p>
     */
    @Query("SELECT i FROM StocktakeItem i WHERE i.batchId = :batchId " +
           "AND (:discrepancyType IS NULL OR " +
           "     ((:discrepancyType = 'MISSING' AND (i.discrepancyType = 'MISSING' OR (i.isCounted = 0 AND i.discrepancyType = 'NONE'))) " +
           "      OR (:discrepancyType <> 'MISSING' AND i.discrepancyType = :discrepancyType))) " +
           "AND (:status IS NULL OR " +
           "     ((:status = 'WAITING' AND i.isCounted = 0 AND i.discrepancyType = 'NONE') " +
           "      OR (:status <> 'WAITING' AND NOT (i.isCounted = 0 AND i.discrepancyType = 'NONE') " +
           "          AND COALESCE(i.discrepancyStatus, 'NONE') = :status))) " +
           "AND (:blockCode IS NULL OR :blockCode = '' OR i.blockCode LIKE CONCAT('%', :blockCode, '%')) " +
           "ORDER BY i.isExtra ASC, i.isCounted ASC, i.id ASC")
    Page<StocktakeItem> searchItems(@Param("batchId") Long batchId,
                                    @Param("discrepancyType") String discrepancyType,
                                    @Param("status") String status,
                                    @Param("blockCode") String blockCode,
                                    Pageable pageable);

    List<StocktakeItem> findByBatchIdOrderByIsExtraAscIsCountedAscIdAsc(Long batchId);

    Optional<StocktakeItem> findByBatchIdAndBlockId(Long batchId, Long blockId);

    /** 结束盘点时把仍“待盘”的应盘行一次性置为缺失差异；clearAutomatically 防止批量更新与一级缓存状态不一致 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE StocktakeItem i SET i.discrepancyType = 'MISSING', i.discrepancyStatus = 'PENDING' " +
           "WHERE i.batchId = :batchId AND i.isCounted = 0 AND i.isExtra = 0 AND i.discrepancyType = 'NONE'")
    int markWaitingAsMissing(@Param("batchId") Long batchId);
}
