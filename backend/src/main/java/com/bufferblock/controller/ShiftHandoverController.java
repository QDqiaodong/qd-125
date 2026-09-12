package com.bufferblock.controller;

import com.bufferblock.dto.HandoverCreateDTO;
import com.bufferblock.dto.HandoverItemConfirmDTO;
import com.bufferblock.dto.HandoverOverviewVO;
import com.bufferblock.dto.HandoverPreviewVO;
import com.bufferblock.dto.HandoverQueryDTO;
import com.bufferblock.dto.Result;
import com.bufferblock.entity.ShiftHandover;
import com.bufferblock.entity.ShiftHandoverItem;
import com.bufferblock.service.ShiftHandoverService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 班组交班：交班时一次性登记当班未还预约、待确认移交与待处理盘点差异，
 * 接班人逐条确认后交班才完成；交班未完成期间禁止新开借用预约。
 */
@RestController
@RequestMapping("/api/shift-handovers")
public class ShiftHandoverController {

    private final ShiftHandoverService shiftHandoverService;

    public ShiftHandoverController(ShiftHandoverService shiftHandoverService) {
        this.shiftHandoverService = shiftHandoverService;
    }

    @PostMapping("/query")
    public Result<Page<ShiftHandover>> query(@RequestBody HandoverQueryDTO query) {
        return Result.success(shiftHandoverService.query(query));
    }

    /** 当前进行中交班概览（未确认条数等），无进行中交班时 inProgress=false */
    @GetMapping("/overview")
    public Result<HandoverOverviewVO> overview() {
        return Result.success(shiftHandoverService.overview());
    }

    /** 登记前预览：当前将被一次性登记的三类未结事项 */
    @GetMapping("/preview")
    public Result<HandoverPreviewVO> preview() {
        return Result.success(shiftHandoverService.preview());
    }

    /** 登记交班：一次性快照全部未结事项，同一时刻只允许一个交班中单 */
    @PostMapping
    public Result<ShiftHandover> create(@RequestBody HandoverCreateDTO dto) {
        return Result.success(shiftHandoverService.create(dto));
    }

    @GetMapping("/{id}")
    public Result<ShiftHandover> getById(@PathVariable Long id) {
        return Result.success(shiftHandoverService.getById(id));
    }

    /** 交班事项清单（登记时快照 + 源单据当前状态） */
    @GetMapping("/{id}/items")
    public Result<List<ShiftHandoverItem>> getItems(@PathVariable Long id) {
        return Result.success(shiftHandoverService.getItems(id));
    }

    /** 接班人逐条确认；全部确认后交班自动完成 */
    @PostMapping("/{id}/items/{itemId}/confirm")
    public Result<ShiftHandover> confirmItem(@PathVariable Long id, @PathVariable Long itemId,
                                             @RequestBody(required = false) HandoverItemConfirmDTO dto) {
        return Result.success(shiftHandoverService.confirmItem(id, itemId, dto));
    }
}
