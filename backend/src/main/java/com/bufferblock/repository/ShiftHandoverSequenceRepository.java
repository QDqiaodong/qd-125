package com.bufferblock.repository;

import com.bufferblock.entity.ShiftHandoverSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShiftHandoverSequenceRepository extends JpaRepository<ShiftHandoverSequence, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ShiftHandoverSequence s WHERE s.sequenceDate = :sequenceDate")
    Optional<ShiftHandoverSequence> findBySequenceDateForUpdate(@Param("sequenceDate") String sequenceDate);
}
