package com.bufferblock.controller;

import com.bufferblock.dto.GaugeCalibrationCreateDTO;
import com.bufferblock.dto.GaugeToolCreateDTO;
import com.bufferblock.dto.GaugeToolOverviewVO;
import com.bufferblock.dto.GaugeToolQueryDTO;
import com.bufferblock.dto.Result;
import com.bufferblock.entity.GaugeCalibration;
import com.bufferblock.entity.GaugeTool;
import com.bufferblock.service.GaugeCalibrationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 点检工装校准台：卡尺 / 塞尺 / 百分表台账（编号、校准到期日、保管班组），
 * 校准登记（合格/不合格、下次应校日），以及班次点检打卡所用的超期/不合格拦截清单。
 */
@RestController
@RequestMapping("/api/gauge-tools")
public class GaugeCalibrationController {

    private final GaugeCalibrationService gaugeCalibrationService;

    public GaugeCalibrationController(GaugeCalibrationService gaugeCalibrationService) {
        this.gaugeCalibrationService = gaugeCalibrationService;
    }

    /** 校准台概览：台账清单与各口径条数（超期条数 = 拦截清单条数，同源实时派生） */
    @PostMapping("/overview")
    public Result<GaugeToolOverviewVO> getOverview(@RequestBody(required = false) GaugeToolQueryDTO query) {
        return Result.success(gaugeCalibrationService.getOverview(query));
    }

    @GetMapping("/overview")
    public Result<GaugeToolOverviewVO> getAllOverview() {
        return Result.success(gaugeCalibrationService.getOverview(new GaugeToolQueryDTO()));
    }

    /** 建账：登记工装编号、类型、校准到期日与保管班组 */
    @PostMapping
    public Result<GaugeTool> createTool(@RequestBody GaugeToolCreateDTO dto) {
        return Result.success(gaugeCalibrationService.createTool(dto));
    }

    /** 编辑台账（编号/类型/到期日/保管班组/停用） */
    @PutMapping
    public Result<GaugeTool> updateTool(@RequestBody GaugeToolCreateDTO dto) {
        return Result.success(gaugeCalibrationService.updateTool(dto));
    }

    /** 某工装的完整校准记录（最近一次在前） */
    @GetMapping("/{toolId}/calibrations")
    public Result<List<GaugeCalibration>> getCalibrationHistory(@PathVariable Long toolId) {
        return Result.success(gaugeCalibrationService.getCalibrationHistory(toolId));
    }

    /** 校准登记：合格时台账到期日同步为下次应校日，工装移出拦截清单 */
    @PostMapping("/calibrations")
    public Result<GaugeCalibration> createCalibration(@RequestBody GaugeCalibrationCreateDTO dto) {
        return Result.success(gaugeCalibrationService.createCalibration(dto));
    }
}
