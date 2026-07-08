package com.bufferblock.repository;

import com.bufferblock.entity.BufferBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BufferBlockRepository extends JpaRepository<BufferBlock, Long> {

    Optional<BufferBlock> findByBlockCode(String blockCode);

    List<BufferBlock> findBySpecTemplate(String specTemplate);

    @Query("SELECT DISTINCT b.specTemplate FROM BufferBlock b WHERE b.specTemplate IS NOT NULL")
    List<String> findAllSpecTemplates();

    boolean existsByBlockCode(String blockCode);
}
