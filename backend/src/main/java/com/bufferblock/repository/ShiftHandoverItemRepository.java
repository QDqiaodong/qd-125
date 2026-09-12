package com.bufferblock.repository;

import com.bufferblock.entity.ShiftHandoverItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShiftHandoverItemRepository extends JpaRepository<ShiftHandoverItem, Long> {

    List<ShiftHandoverItem> findByHandoverIdOrderByIdAsc(Long handoverId);

    long countByHandoverIdAndStatus(Long handoverId, String status);
}
