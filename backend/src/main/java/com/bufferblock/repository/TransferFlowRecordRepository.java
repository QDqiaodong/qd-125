package com.bufferblock.repository;

import com.bufferblock.entity.TransferFlowRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransferFlowRecordRepository extends JpaRepository<TransferFlowRecord, Long> {

    List<TransferFlowRecord> findByTransferIdOrderByCreateTimeAscIdAsc(Long transferId);
}
