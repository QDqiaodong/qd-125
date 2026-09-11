package com.bufferblock.repository;

import com.bufferblock.entity.BlockTransferSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BlockTransferSequenceRepository extends JpaRepository<BlockTransferSequence, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM BlockTransferSequence s WHERE s.sequenceDate = :sequenceDate")
    Optional<BlockTransferSequence> findBySequenceDateForUpdate(@Param("sequenceDate") String sequenceDate);
}
