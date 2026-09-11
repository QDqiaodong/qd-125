package com.bufferblock.controller;

import com.bufferblock.dto.CalibrationCreateDTO;
import com.bufferblock.dto.CalibrationStatusVO;
import com.bufferblock.dto.Result;
import com.bufferblock.entity.BlockCalibration;
import com.bufferblock.service.CalibrationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/calibrations")
public class CalibrationController {

    private final CalibrationService calibrationService;

    public CalibrationController(CalibrationService calibrationService) {
        this.calibrationService = calibrationService;
    }

    /** 某挡块的完整校准记录（最近一次在前） */
    @GetMapping("/block/{blockId}")
    public Result<List<BlockCalibration>> getHistory(@PathVariable Long blockId) {
        return Result.success(calibrationService.getHistory(blockId));
    }

    /** 某挡块当前校准状态（到期标记/挂起原因，均为派生数据） */
    @GetMapping("/block/{blockId}/status")
    public Result<CalibrationStatusVO> getStatus(@PathVariable Long blockId) {
        return Result.success(calibrationService.statusOf(blockId));
    }

    /** 校准员录入本次校准结果、有效期与下次应校日期 */
    @PostMapping
    public Result<BlockCalibration> createCalibration(@RequestBody CalibrationCreateDTO dto) {
        return Result.success(calibrationService.createCalibration(dto));
    }
}
