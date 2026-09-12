package com.bufferblock.repository;

import com.bufferblock.entity.BlockTransfer;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BlockTransferRepository extends JpaRepository<BlockTransfer, Long> {

    /**
     * 确认/驳回处理前锁定移交单，串行化并发处理，避免确认先改绑定后驳回又覆盖状态。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM BlockTransfer t WHERE t.id = :id")
    Optional<BlockTransfer> findByIdForUpdate(@Param("id") Long id);

    /** 移交确认列表共用的筛选条件 */
    String CONFIRM_FILTER = "SELECT t FROM BlockTransfer t WHERE " +
            "(:startDate IS NULL OR t.transferDate >= :startDate) AND " +
            "(:endDate IS NULL OR t.transferDate <= :endDate) AND " +
            "(:fromLineId IS NULL OR t.fromLineId = :fromLineId) AND " +
            "(:toLineId IS NULL OR t.toLineId = :toLineId) AND " +
            "(:lineId IS NULL OR t.fromLineId = :lineId OR t.toLineId = :lineId) AND " +
            "(:status IS NULL OR t.status = :status) AND " +
            "(:blockCode IS NULL OR :blockCode = '' OR EXISTS (" +
            "    SELECT 1 FROM BufferBlock b WHERE b.id = t.blockId " +
            "    AND b.blockCode LIKE CONCAT('%', :blockCode, '%'))) ";

    /**
     * 等待时长排序键（分钟），与 BlockTransferService 的等待时长口径一致：
     * 待确认单为登记时刻到当前时刻，已确认/已驳回单定格在办理时刻（handleTime）
     */
    String WAIT_DURATION_MINUTES =
            "timestampdiff(minute, t.createTime, coalesce(t.handleTime, current_timestamp))";

    @Query(CONFIRM_FILTER + "ORDER BY t.transferDate DESC, t.createTime DESC")
    Page<BlockTransfer> findForConfirm(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("fromLineId") Long fromLineId,
            @Param("toLineId") Long toLineId,
            @Param("lineId") Long lineId,
            @Param("status") String status,
            @Param("blockCode") String blockCode,
            Pageable pageable);

    /** 等待最长优先（压得越久的单越靠前），相同分钟按登记先后（id）稳定排序 */
    @Query(CONFIRM_FILTER + "ORDER BY " + WAIT_DURATION_MINUTES + " DESC, t.id ASC")
    Page<BlockTransfer> findForConfirmWaitLongestFirst(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("fromLineId") Long fromLineId,
            @Param("toLineId") Long toLineId,
            @Param("lineId") Long lineId,
            @Param("status") String status,
            @Param("blockCode") String blockCode,
            Pageable pageable);

    /** 等待最短优先，相同分钟按登记先后（id）稳定排序 */
    @Query(CONFIRM_FILTER + "ORDER BY " + WAIT_DURATION_MINUTES + " ASC, t.id ASC")
    Page<BlockTransfer> findForConfirmWaitShortestFirst(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("fromLineId") Long fromLineId,
            @Param("toLineId") Long toLineId,
            @Param("lineId") Long lineId,
            @Param("status") String status,
            @Param("blockCode") String blockCode,
            Pageable pageable);

    List<BlockTransfer> findByBlockIdOrderByTransferDateDesc(Long blockId);

    List<BlockTransfer> findByStatus(String status);

    List<BlockTransfer> findByBlockIdAndStatusOrderByCreateTimeDesc(Long blockId, String status);

    @Query("SELECT t FROM BlockTransfer t WHERE t.transferNo LIKE CONCAT(:prefix, '%') " +
           "ORDER BY t.transferNo DESC")
    List<BlockTransfer> findByTransferNoPrefixOrderByTransferNoDesc(@Param("prefix") String prefix, Pageable pageable);

    @Query("SELECT t FROM BlockTransfer t WHERE " +
           "(:startDate IS NULL OR t.transferDate >= :startDate) AND " +
           "(:endDate IS NULL OR t.transferDate <= :endDate) " +
           "ORDER BY t.transferDate DESC, t.createTime DESC")
    List<BlockTransfer> findByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
