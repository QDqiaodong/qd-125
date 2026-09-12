package com.bufferblock.repository;

import com.bufferblock.entity.BlockBorrowReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BlockBorrowReservationRepository extends JpaRepository<BlockBorrowReservation, Long> {

    String QUERY_FILTER = "SELECT r FROM BlockBorrowReservation r WHERE " +
            "(:status IS NULL OR r.status = :status) AND " +
            "(:teamName IS NULL OR :teamName = '' OR r.teamName LIKE CONCAT('%', :teamName, '%')) AND " +
            "(:blockCode IS NULL OR :blockCode = '' OR EXISTS (" +
            "    SELECT 1 FROM BufferBlock b WHERE b.id = r.blockId " +
            "    AND b.blockCode LIKE CONCAT('%', :blockCode, '%'))) ";

    @Query(QUERY_FILTER + "ORDER BY r.createTime DESC, r.id DESC")
    Page<BlockBorrowReservation> queryReservations(
            @Param("status") String status,
            @Param("teamName") String teamName,
            @Param("blockCode") String blockCode,
            Pageable pageable);

    List<BlockBorrowReservation> findByBlockIdOrderByCreateTimeDesc(Long blockId);

    /** 同一挡块同时只允许一张占用中（已预约/已取走）的预约单 */
    List<BlockBorrowReservation> findByBlockIdAndStatusIn(Long blockId, List<String> statuses);

    @Query("SELECT r FROM BlockBorrowReservation r WHERE r.status IN :statuses")
    List<BlockBorrowReservation> findByStatusIn(@Param("statuses") List<String> statuses);

    /**
     * 到点未取扫描：仍处已预约且约定取用时间已过（宽限期由服务层统一口径）。
     * 加悲观锁，避免定时任务与人工取走/取消并发。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM BlockBorrowReservation r WHERE r.status = 'RESERVED' AND r.pickupTime <= :deadline")
    List<BlockBorrowReservation> findDueReservationsForUpdate(@Param("deadline") LocalDateTime deadline);

    @Query("SELECT r FROM BlockBorrowReservation r WHERE r.reservationNo LIKE CONCAT(:prefix, '%') " +
           "ORDER BY r.reservationNo DESC")
    List<BlockBorrowReservation> findByReservationNoPrefixOrderByReservationNoDesc(
            @Param("prefix") String prefix, Pageable pageable);

    long countByStatus(String status);
}
