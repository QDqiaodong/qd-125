package com.bufferblock.repository;

import com.bufferblock.entity.ProductionLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductionLineRepository extends JpaRepository<ProductionLine, Long> {

    List<ProductionLine> findByParentIdIsNullOrderBySortOrder();

    List<ProductionLine> findByParentIdOrderBySortOrder(Long parentId);

    Optional<ProductionLine> findByLineCode(String lineCode);

    @Query("SELECT p FROM ProductionLine p ORDER BY p.sortOrder")
    List<ProductionLine> findAllOrderBySortOrder();
}
