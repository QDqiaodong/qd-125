package com.bufferblock.service;

import com.bufferblock.entity.ProductionLine;
import com.bufferblock.repository.ProductionLineRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductionLineService {

    private final ProductionLineRepository productionLineRepository;

    public ProductionLineService(ProductionLineRepository productionLineRepository) {
        this.productionLineRepository = productionLineRepository;
    }

    @Cacheable(value = "productionLines", key = "'tree'")
    public List<ProductionLine> getLineTree() {
        List<ProductionLine> allLines = productionLineRepository.findAllOrderBySortOrder();
        Map<Long, ProductionLine> lineMap = allLines.stream()
                .collect(Collectors.toMap(ProductionLine::getId, line -> {
                    line.setChildren(new ArrayList<>());
                    return line;
                }));

        List<ProductionLine> roots = new ArrayList<>();
        for (ProductionLine line : allLines) {
            if (line.getParentId() == null) {
                roots.add(line);
            } else {
                ProductionLine parent = lineMap.get(line.getParentId());
                if (parent != null) {
                    parent.getChildren().add(line);
                }
            }
        }
        return roots;
    }

    public List<ProductionLine> getAllLeafLines() {
        return productionLineRepository.findAllOrderBySortOrder().stream()
                .filter(line -> line.getParentId() != null)
                .collect(Collectors.toList());
    }

    public ProductionLine getById(Long id) {
        return productionLineRepository.findById(id).orElse(null);
    }

    public List<ProductionLine> getAllLines() {
        return productionLineRepository.findAllOrderBySortOrder();
    }
}
