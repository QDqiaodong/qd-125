package com.bufferblock.repository;

import com.bufferblock.entity.BlockTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BlockTransferRepository extends JpaRepository<BlockTransfer, Long> {

    @Query("SELECT t FROM BlockTransfer t WHERE " +
           "(:startDate IS NULL OR t.transferDate >= :startDate) AND " +
           "(:endDate IS NULL OR t.transferDate <= :endDate) AND " +
           "(:fromLineId IS NULL OR t.fromLineId = :fromLineId) AND " +
           "(:toLineId IS NULL OR t.toLineId = :toLineId) " +
           "ORDER BY t.transferDate DESC, t.createTime DESC")
    Page<BlockTransfer> findByConditions(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("fromLineId") Long fromLineId,
            @Param("toLineId") Long toLineId,
            Pageable pageable);

    List<BlockTransfer> findByBlockIdOrderByTransferDateDesc(Long blockId);

    @Query("SELECT t FROM BlockTransfer t WHERE " +
           "(:startDate IS NULL OR t.transferDate >= :startDate) AND " +
           "(:endDate IS NULL OR t.transferDate <= :endDate) " +
           "ORDER BY t.transferDate DESC, t.createTime DESC")
    List<BlockTransfer> findByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
