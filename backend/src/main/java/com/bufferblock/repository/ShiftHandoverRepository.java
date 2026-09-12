package com.bufferblock.repository;

import com.bufferblock.entity.ShiftHandover;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftHandoverRepository extends JpaRepository<ShiftHandover, Long> {

    @Query("SELECT h FROM ShiftHandover h WHERE " +
           "(:status IS NULL OR h.status = :status) AND " +
           "(:handoverNo IS NULL OR :handoverNo = '' OR h.handoverNo LIKE CONCAT('%', :handoverNo, '%')) " +
           "ORDER BY h.createTime DESC, h.id DESC")
    Page<ShiftHandover> search(@Param("status") String status,
                               @Param("handoverNo") String handoverNo,
                               Pageable pageable);

    /** 当前进行中的交班（同一时刻只允许一个） */
    Optional<ShiftHandover> findFirstByStatusOrderByIdDesc(String status);

    /**
     * 交班登记/确认时加悲观锁读取进行中交班，串行化并发登记与逐条确认，
     * 避免并发下出现两个进行中交班或确认计数漂移。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM ShiftHandover h WHERE h.status = 'IN_PROGRESS'")
    List<ShiftHandover> findInProgressForUpdate();

    @Query("SELECT h FROM ShiftHandover h WHERE h.handoverNo LIKE CONCAT(:prefix, '%') " +
           "ORDER BY h.handoverNo DESC")
    List<ShiftHandover> findByHandoverNoPrefixOrderByHandoverNoDesc(@Param("prefix") String prefix,
                                                                    Pageable pageable);

    long countByStatus(String status);
}
