package com.bufferblock.controller;

import com.bufferblock.dto.Result;
import com.bufferblock.entity.ProductionLine;
import com.bufferblock.service.ProductionLineService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lines")
public class ProductionLineController {

    private final ProductionLineService productionLineService;

    public ProductionLineController(ProductionLineService productionLineService) {
        this.productionLineService = productionLineService;
    }

    @GetMapping("/tree")
    public Result<List<ProductionLine>> getLineTree() {
        return Result.success(productionLineService.getLineTree());
    }

    @GetMapping("/leaf")
    public Result<List<ProductionLine>> getLeafLines() {
        return Result.success(productionLineService.getAllLeafLines());
    }

    @GetMapping
    public Result<List<ProductionLine>> getAllLines() {
        return Result.success(productionLineService.getAllLines());
    }

    @GetMapping("/{id}")
    public Result<ProductionLine> getById(@PathVariable Long id) {
        return Result.success(productionLineService.getById(id));
    }
}
