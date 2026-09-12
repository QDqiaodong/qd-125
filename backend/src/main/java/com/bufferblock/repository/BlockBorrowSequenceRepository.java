package com.bufferblock.repository;

import com.bufferblock.entity.BlockBorrowSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BlockBorrowSequenceRepository extends JpaRepository<BlockBorrowSequence, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM BlockBorrowSequence s WHERE s.sequenceDate = :sequenceDate")
    Optional<BlockBorrowSequence> findBySequenceDateForUpdate(@Param("sequenceDate") String sequenceDate);
}
