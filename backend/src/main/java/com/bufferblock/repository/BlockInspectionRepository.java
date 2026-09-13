package com.bufferblock.repository;

import com.bufferblock.entity.BlockInspection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlockInspectionRepository extends JpaRepository<BlockInspection, Long> {

    List<BlockInspection> findByBlockIdOrderByInspectionTimeDescIdDesc(Long blockId);

    Optional<BlockInspection> findFirstByBlockIdOrderByInspectionTimeDescIdDesc(Long blockId);

    List<BlockInspection> findByBlockIdInOrderByInspectionTimeDescIdDesc(List<Long> blockIds);
}
