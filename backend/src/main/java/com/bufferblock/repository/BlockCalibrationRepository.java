package com.bufferblock.repository;

import com.bufferblock.entity.BlockCalibration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlockCalibrationRepository extends JpaRepository<BlockCalibration, Long> {

    List<BlockCalibration> findByBlockIdOrderByCalibrationDateDescIdDesc(Long blockId);

    Optional<BlockCalibration> findFirstByBlockIdOrderByCalibrationDateDescIdDesc(Long blockId);

    List<BlockCalibration> findByBlockIdInOrderByCalibrationDateDescIdDesc(List<Long> blockIds);
}
