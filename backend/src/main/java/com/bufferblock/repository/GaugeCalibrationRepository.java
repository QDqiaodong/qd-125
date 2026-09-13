package com.bufferblock.repository;

import com.bufferblock.entity.GaugeCalibration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GaugeCalibrationRepository extends JpaRepository<GaugeCalibration, Long> {

    List<GaugeCalibration> findByToolIdOrderByCalibrationDateDescIdDesc(Long toolId);

    Optional<GaugeCalibration> findFirstByToolIdOrderByCalibrationDateDescIdDesc(Long toolId);

    List<GaugeCalibration> findByToolIdInOrderByCalibrationDateDescIdDesc(List<Long> toolIds);
}
