package com.bufferblock.repository;

import com.bufferblock.entity.StocktakeSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StocktakeSequenceRepository extends JpaRepository<StocktakeSequence, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StocktakeSequence s WHERE s.sequenceDate = :sequenceDate")
    Optional<StocktakeSequence> findBySequenceDateForUpdate(@Param("sequenceDate") String sequenceDate);
}
