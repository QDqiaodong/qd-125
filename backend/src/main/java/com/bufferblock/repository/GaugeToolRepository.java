package com.bufferblock.repository;

import com.bufferblock.entity.GaugeTool;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GaugeToolRepository extends JpaRepository<GaugeTool, Long> {

    Optional<GaugeTool> findByToolCode(String toolCode);

    boolean existsByToolCode(String toolCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM GaugeTool t WHERE t.id = :id")
    Optional<GaugeTool> findByIdForUpdate(@Param("id") Long id);
}
