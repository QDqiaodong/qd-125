package com.bufferblock.controller;

import com.bufferblock.dto.BorrowCancelDTO;
import com.bufferblock.dto.BorrowHandleDTO;
import com.bufferblock.dto.BorrowOverviewVO;
import com.bufferblock.dto.BorrowReservationCreateDTO;
import com.bufferblock.dto.BorrowReservationQueryDTO;
import com.bufferblock.dto.Result;
import com.bufferblock.entity.BlockBorrowFlowRecord;
import com.bufferblock.entity.BlockBorrowReservation;
import com.bufferblock.service.BorrowReservationService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 缓冲挡块借用预约。独立于挡块档案与移交划转：不改产线归属，
 * 只在占用期间于档案上叠加占用标记，取消必须写原因，到点未取由后台提醒。
 */
@RestController
@RequestMapping("/api/borrow-reservations")
public class BorrowReservationController {

    private final BorrowReservationService borrowReservationService;

    public BorrowReservationController(BorrowReservationService borrowReservationService) {
        this.borrowReservationService = borrowReservationService;
    }

    @PostMapping("/query")
    public Result<Page<BlockBorrowReservation>> query(@RequestBody BorrowReservationQueryDTO query) {
        return Result.success(borrowReservationService.query(query));
    }

    @GetMapping("/overview")
    public Result<BorrowOverviewVO> overview() {
        return Result.success(borrowReservationService.overview());
    }

    @GetMapping("/{id}")
    public Result<BlockBorrowReservation> getById(@PathVariable Long id) {
        return Result.success(borrowReservationService.getById(id));
    }

    @PostMapping
    public Result<BlockBorrowReservation> create(@RequestBody BorrowReservationCreateDTO dto) {
        return Result.success(borrowReservationService.create(dto));
    }

    /** 班组取走（逾时未取后也可补登记） */
    @PostMapping("/{id}/pickup")
    public Result<BlockBorrowReservation> pickup(@PathVariable Long id, @RequestBody(required = false) BorrowHandleDTO dto) {
        return Result.success(borrowReservationService.pickup(id, dto));
    }

    /** 归还到约定归还点，占用结束 */
    @PostMapping("/{id}/return")
    public Result<BlockBorrowReservation> giveBack(@PathVariable Long id, @RequestBody(required = false) BorrowHandleDTO dto) {
        return Result.success(borrowReservationService.giveBack(id, dto));
    }

    /** 取消预约（必须填写取消原因） */
    @PostMapping("/{id}/cancel")
    public Result<BlockBorrowReservation> cancel(@PathVariable Long id, @RequestBody BorrowCancelDTO dto) {
        return Result.success(borrowReservationService.cancel(id, dto));
    }

    /** 完整流转记录（含逾时提醒） */
    @GetMapping("/{id}/flow-records")
    public Result<List<BlockBorrowFlowRecord>> getFlowRecords(@PathVariable Long id) {
        return Result.success(borrowReservationService.getFlowRecords(id));
    }

    @GetMapping("/block/{blockId}")
    public Result<List<BlockBorrowReservation>> getByBlockId(@PathVariable Long blockId) {
        return Result.success(borrowReservationService.getByBlockId(blockId));
    }
}
