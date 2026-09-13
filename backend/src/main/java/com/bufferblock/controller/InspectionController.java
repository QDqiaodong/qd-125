package com.bufferblock.controller;

import com.bufferblock.dto.InspectionCreateDTO;
import com.bufferblock.dto.InspectionOverviewVO;
import com.bufferblock.dto.InspectionQueryDTO;
import com.bufferblock.dto.Result;
import com.bufferblock.entity.BlockInspection;
import com.bufferblock.service.InspectionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 挡块班次点检台账：按产线筛选在用挡块、班次点检打卡（点检人/班次/是否可用），
 * 最近一次点检结论随台账与挡块档案同源派生。
 */
@RestController
@RequestMapping("/api/inspections")
public class InspectionController {

    private final InspectionService inspectionService;

    public InspectionController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    /** 点检台账概览：按产线等条件筛选在用挡块清单及可用/不可用/未点检条数 */
    @PostMapping("/overview")
    public Result<InspectionOverviewVO> getOverview(@RequestBody(required = false) InspectionQueryDTO query) {
        return Result.success(inspectionService.getOverview(query));
    }

    /** 兼容 GET 方式查看全部产线点检台账 */
    @GetMapping("/overview")
    public Result<InspectionOverviewVO> getAllOverview() {
        return Result.success(inspectionService.getOverview(new InspectionQueryDTO()));
    }

    /** 某挡块的完整点检记录（最近一次在前） */
    @GetMapping("/block/{blockId}")
    public Result<List<BlockInspection>> getHistory(@PathVariable Long blockId) {
        return Result.success(inspectionService.getHistory(blockId));
    }

    /** 班次点检打卡：登记点检人、班次与是否可用 */
    @PostMapping
    public Result<BlockInspection> createInspection(@RequestBody InspectionCreateDTO dto) {
        return Result.success(inspectionService.createInspection(dto));
    }
}
