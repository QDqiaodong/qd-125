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
           "(:toLineId IS NULL OR t.toLineId = :toLineId) AND " +
           "(:lineId IS NULL OR t.fromLineId = :lineId OR t.toLineId = :lineId) AND " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:blockCode IS NULL OR :blockCode = '' OR EXISTS (" +
           "    SELECT 1 FROM BufferBlock b WHERE b.id = t.blockId " +
           "    AND b.blockCode LIKE CONCAT('%', :blockCode, '%'))) " +
           "ORDER BY t.transferDate DESC, t.createTime DESC")
    Page<BlockTransfer> findForConfirm(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("fromLineId") Long fromLineId,
            @Param("toLineId") Long toLineId,
            @Param("lineId") Long lineId,
            @Param("status") String status,
            @Param("blockCode") String blockCode,
            Pageable pageable);

    List<BlockTransfer> findByBlockIdOrderByTransferDateDesc(Long blockId);

    List<BlockTransfer> findByBlockIdAndStatusOrderByCreateTimeDesc(Long blockId, String status);

    @Query("SELECT t FROM BlockTransfer t WHERE " +
           "(:startDate IS NULL OR t.transferDate >= :startDate) AND " +
           "(:endDate IS NULL OR t.transferDate <= :endDate) " +
           "ORDER BY t.transferDate DESC, t.createTime DESC")
    List<BlockTransfer> findByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * 查询当日移交单号 TRF-yyyyMMdd-xxx 已用的最大流水序号；
     * 无记录返回 null。用于替代进程内计数器，避免应用重启后序号归零撞唯一键。
     * 末尾序号为定长补零数字串，加 0 做数值化（MySQL 隐式转换），MAX 取当日最大序号。
     */
    @Query(value = "SELECT MAX(SUBSTRING(transfer_no, CHAR_LENGTH(:prefix) + 1) + 0) " +
           "FROM block_transfer WHERE transfer_no LIKE CONCAT(:prefix, '%')",
           nativeQuery = true)
    Integer findMaxDailySequence(@Param("prefix") String prefix);
}
