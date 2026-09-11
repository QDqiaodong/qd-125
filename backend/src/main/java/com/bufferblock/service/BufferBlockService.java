package com.bufferblock.service;

import com.bufferblock.dto.BufferBlockDTO;
import com.bufferblock.entity.BlockLineBinding;
import com.bufferblock.entity.BufferBlock;
import com.bufferblock.entity.ProductionLine;
import com.bufferblock.repository.BlockLineBindingRepository;
import com.bufferblock.repository.BufferBlockRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BufferBlockService {

    private final BufferBlockRepository bufferBlockRepository;
    private final BlockLineBindingRepository blockLineBindingRepository;
    private final ProductionLineService productionLineService;

    public BufferBlockService(BufferBlockRepository bufferBlockRepository,
                              BlockLineBindingRepository blockLineBindingRepository,
                              ProductionLineService productionLineService) {
        this.bufferBlockRepository = bufferBlockRepository;
        this.blockLineBindingRepository = blockLineBindingRepository;
        this.productionLineService = productionLineService;
    }

    public List<BufferBlockDTO> getAllBlocks() {
        List<BufferBlock> blocks = bufferBlockRepository.findAll();
        return blocks.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public BufferBlockDTO getById(Long id) {
        BufferBlock block = bufferBlockRepository.findById(id).orElse(null);
        return block != null ? convertToDTO(block) : null;
    }

    public BufferBlock getEntityById(Long id) {
        return bufferBlockRepository.findById(id).orElse(null);
    }

    /**
     * 锁定挡块主数据，串行化同一挡块的并发登记，避免重复生成待确认移交单。
     */
    @Transactional(readOnly = true)
    public BufferBlock lockEntityById(Long id) {
        return bufferBlockRepository.findByIdForUpdate(id).orElse(null);
    }

    @Cacheable(value = "specTemplates", key = "'all'")
    public List<String> getAllSpecTemplates() {
        return bufferBlockRepository.findAllSpecTemplates();
    }

    @Cacheable(value = "specTemplateBlocks", key = "#template")
    public List<BufferBlock> getBlocksBySpecTemplate(String template) {
        return bufferBlockRepository.findBySpecTemplate(template);
    }

    @Transactional
    @CacheEvict(value = {"specTemplates", "specTemplateBlocks"}, allEntries = true)
    public BufferBlock createBlock(BufferBlockDTO dto) {
        if (bufferBlockRepository.existsByBlockCode(dto.getBlockCode())) {
            throw new RuntimeException("挡块编号已存在");
        }

        BufferBlock block = new BufferBlock();
        block.setBlockCode(dto.getBlockCode());
        block.setAdapterModel(dto.getAdapterModel());
        block.setThickness(dto.getThickness());
        block.setImageUrl(dto.getImageUrl());
        block.setSpecTemplate(dto.getSpecTemplate());
        block = bufferBlockRepository.save(block);

        if (dto.getLineId() != null) {
            bindBlockToLine(block.getId(), dto.getLineId(), dto.getBlockCode(), 1);
        }

        return block;
    }

    @Transactional
    @CacheEvict(value = {"specTemplates", "specTemplateBlocks"}, allEntries = true)
    public BufferBlock updateBlock(Long id, BufferBlockDTO dto) {
        BufferBlock block = bufferBlockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("挡块不存在"));

        if (!block.getBlockCode().equals(dto.getBlockCode()) && bufferBlockRepository.existsByBlockCode(dto.getBlockCode())) {
            throw new RuntimeException("挡块编号已存在");
        }

        block.setBlockCode(dto.getBlockCode());
        block.setAdapterModel(dto.getAdapterModel());
        block.setThickness(dto.getThickness());
        block.setImageUrl(dto.getImageUrl());
        block.setSpecTemplate(dto.getSpecTemplate());
        return bufferBlockRepository.save(block);
    }

    public void bindBlockToLine(Long blockId, Long lineId, String operator, Integer bindType) {
        blockLineBindingRepository.findByBlockIdAndIsCurrent(blockId, 1)
                .ifPresent(binding -> {
                    binding.setIsCurrent(0);
                    blockLineBindingRepository.save(binding);
                });

        BlockLineBinding binding = new BlockLineBinding();
        binding.setBlockId(blockId);
        binding.setLineId(lineId);
        binding.setBindType(bindType);
        binding.setBindTime(LocalDateTime.now());
        binding.setOperator(operator);
        binding.setIsCurrent(1);
        blockLineBindingRepository.save(binding);
    }

    public List<BufferBlockDTO> getBlocksByLineIds(List<Long> lineIds) {
        List<BlockLineBinding> bindings = blockLineBindingRepository.findByLineIdInAndIsCurrent(lineIds, 1);
        List<BufferBlockDTO> result = new ArrayList<>();
        for (BlockLineBinding binding : bindings) {
            BufferBlock block = bufferBlockRepository.findById(binding.getBlockId()).orElse(null);
            if (block != null) {
                result.add(convertToDTO(block));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public BlockLineBinding getCurrentBinding(Long blockId) {
        return blockLineBindingRepository.findByBlockIdAndIsCurrent(blockId, 1).orElse(null);
    }

    public List<BlockLineBinding> getBindingHistory(Long blockId) {
        List<BlockLineBinding> bindings = blockLineBindingRepository.findByBlockIdOrderByBindTimeDesc(blockId);
        for (BlockLineBinding binding : bindings) {
            ProductionLine line = productionLineService.getById(binding.getLineId());
            if (line != null) {
                binding.setLineCode(line.getLineCode());
                binding.setLineName(line.getLineName());
            }
            BufferBlock block = bufferBlockRepository.findById(binding.getBlockId()).orElse(null);
            if (block != null) {
                binding.setBlockCode(block.getBlockCode());
            }
        }
        return bindings;
    }

    private BufferBlockDTO convertToDTO(BufferBlock block) {
        BufferBlockDTO dto = new BufferBlockDTO();
        dto.setId(block.getId());
        dto.setBlockCode(block.getBlockCode());
        dto.setAdapterModel(block.getAdapterModel());
        dto.setThickness(block.getThickness());
        dto.setImageUrl(block.getImageUrl());
        dto.setSpecTemplate(block.getSpecTemplate());

        BlockLineBinding binding = getCurrentBinding(block.getId());
        if (binding != null) {
            dto.setLineId(binding.getLineId());
            ProductionLine line = productionLineService.getById(binding.getLineId());
            if (line != null) {
                dto.setLineName(line.getLineName());
            }
        }
        return dto;
    }
}
