package com.bufferblock.controller;

import com.bufferblock.dto.BufferBlockDTO;
import com.bufferblock.dto.Result;
import com.bufferblock.entity.BlockLineBinding;
import com.bufferblock.entity.BufferBlock;
import com.bufferblock.service.BufferBlockService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/blocks")
public class BufferBlockController {

    private final BufferBlockService bufferBlockService;

    public BufferBlockController(BufferBlockService bufferBlockService) {
        this.bufferBlockService = bufferBlockService;
    }

    @GetMapping
    public Result<List<BufferBlockDTO>> getAllBlocks() {
        return Result.success(bufferBlockService.getAllBlocks());
    }

    @GetMapping("/{id}")
    public Result<BufferBlockDTO> getById(@PathVariable Long id) {
        return Result.success(bufferBlockService.getById(id));
    }

    @GetMapping("/spec-templates")
    public Result<List<String>> getSpecTemplates() {
        return Result.success(bufferBlockService.getAllSpecTemplates());
    }

    @GetMapping("/spec-template/{template}")
    public Result<List<BufferBlock>> getBlocksBySpecTemplate(@PathVariable String template) {
        return Result.success(bufferBlockService.getBlocksBySpecTemplate(template));
    }

    @PostMapping
    public Result<BufferBlock> createBlock(@RequestBody BufferBlockDTO dto) {
        return Result.success(bufferBlockService.createBlock(dto));
    }

    @PutMapping("/{id}")
    public Result<BufferBlock> updateBlock(@PathVariable Long id, @RequestBody BufferBlockDTO dto) {
        return Result.success(bufferBlockService.updateBlock(id, dto));
    }

    @GetMapping("/{id}/bindings")
    public Result<List<BlockLineBinding>> getBindingHistory(@PathVariable Long id) {
        return Result.success(bufferBlockService.getBindingHistory(id));
    }

    @PostMapping("/by-line-ids")
    public Result<List<BufferBlockDTO>> getBlocksByLineIds(@RequestBody List<Long> lineIds) {
        return Result.success(bufferBlockService.getBlocksByLineIds(lineIds));
    }
}
