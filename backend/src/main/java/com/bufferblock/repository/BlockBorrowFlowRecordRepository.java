package com.bufferblock.repository;

import com.bufferblock.entity.BlockBorrowFlowRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BlockBorrowFlowRecordRepository extends JpaRepository<BlockBorrowFlowRecord, Long> {

    List<BlockBorrowFlowRecord> findByReservationIdOrderByCreateTimeAscIdAsc(Long reservationId);
}
