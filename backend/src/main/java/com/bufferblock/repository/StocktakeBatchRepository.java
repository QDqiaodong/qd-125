package com.bufferblock.repository;

import com.bufferblock.entity.StocktakeBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StocktakeBatchRepository extends JpaRepository<StocktakeBatch, Long> {

    @Query("SELECT b FROM StocktakeBatch b WHERE " +
           "(:lineId IS NULL OR b.lineId = :lineId) AND " +
           "(:status IS NULL OR b.status = :status) AND " +
           "(:startDate IS NULL OR b.stocktakeDate >= :startDate) AND " +
           "(:endDate IS NULL OR b.stocktakeDate <= :endDate) AND " +
           "(:batchNo IS NULL OR :batchNo = '' OR b.batchNo LIKE CONCAT('%', :batchNo, '%')) " +
           "ORDER BY b.stocktakeDate DESC, b.id DESC")
    Page<StocktakeBatch> search(@Param("lineId") Long lineId,
                                @Param("status") String status,
                                @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate,
                                @Param("batchNo") String batchNo,
                                Pageable pageable);

    /** 同一产线同时只允许一个盘点中的批次，避免快照与现场口径冲突 */
    Optional<StocktakeBatch> findFirstByLineIdAndStatusOrderByIdDesc(Long lineId, String status);

    long countByStatus(String status);

    @Query("SELECT COALESCE(SUM(b.pendingCount), 0) FROM StocktakeBatch b WHERE b.status = :status")
    long sumPendingCountByStatus(@Param("status") String status);

    @Query("SELECT b FROM StocktakeBatch b WHERE b.batchNo LIKE CONCAT(:prefix, '%') ORDER BY b.batchNo DESC")
    List<StocktakeBatch> findByBatchNoPrefix(@Param("prefix") String prefix, Pageable pageable);
}
