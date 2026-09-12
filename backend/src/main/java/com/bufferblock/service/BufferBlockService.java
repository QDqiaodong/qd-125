package com.bufferblock.service;

import com.bufferblock.dto.BufferBlockDTO;
import com.bufferblock.dto.CalibrationStatusVO;
import com.bufferblock.entity.BlockBorrowReservation;
import com.bufferblock.entity.BlockLineBinding;
import com.bufferblock.entity.BlockTransfer;
import com.bufferblock.entity.BufferBlock;
import com.bufferblock.entity.ProductionLine;
import com.bufferblock.repository.BlockLineBindingRepository;
import com.bufferblock.repository.BlockTransferRepository;
import com.bufferblock.repository.BufferBlockRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BufferBlockService {

    private final BufferBlockRepository bufferBlockRepository;
    private final BlockLineBindingRepository blockLineBindingRepository;
    private final BlockTransferRepository blockTransferRepository;
    private final ProductionLineService productionLineService;
    private final CalibrationService calibrationService;
    /**
     * 借用预约服务反向依赖本服务（锁档、查绑定），这里用 ObjectProvider 延迟解析，
     * 打破两个服务之间的构造器循环依赖。
     */
    private final ObjectProvider<BorrowReservationService> borrowReservationServiceProvider;

    public BufferBlockService(BufferBlockRepository bufferBlockRepository,
                              BlockLineBindingRepository blockLineBindingRepository,
                              BlockTransferRepository blockTransferRepository,
                              ProductionLineService productionLineService,
                              CalibrationService calibrationService,
                              ObjectProvider<BorrowReservationService> borrowReservationServiceProvider) {
        this.bufferBlockRepository = bufferBlockRepository;
        this.blockLineBindingRepository = blockLineBindingRepository;
        this.blockTransferRepository = blockTransferRepository;
        this.productionLineService = productionLineService;
        this.calibrationService = calibrationService;
        this.borrowReservationServiceProvider = borrowReservationServiceProvider;
    }

    public List<BufferBlockDTO> getAllBlocks() {
        List<BufferBlock> blocks = bufferBlockRepository.findAll();
        Map<Long, CalibrationStatusVO> statusMap = calibrationService.statusMapOfBlocks(blocks);
        Map<Long, String> pendingMap = pendingTransferNoMap();
        Map<Long, BlockBorrowReservation> borrowMap =
                borrowReservationServiceProvider.getObject().activeReservationMap();
        return blocks.stream()
                .map(block -> withBorrowMark(
                        withPendingTransfer(convertToDTO(block, statusMap.get(block.getId())), pendingMap),
                        borrowMap))
                .collect(Collectors.toList());
    }

    public BufferBlockDTO getById(Long id) {
        BufferBlock block = bufferBlockRepository.findById(id).orElse(null);
        if (block == null) {
            return null;
        }
        BufferBlockDTO dto = convertToDTO(block, calibrationService.statusOf(id));
        // 单挡块详情同样叠加借用占用标记（含空闲时显式置 false），保证档案与预约状态口径一致
        Map<Long, BlockBorrowReservation> borrowMap = new HashMap<>();
        BlockBorrowReservation reservation =
                borrowReservationServiceProvider.getObject().activeReservationMap().get(id);
        if (reservation != null) {
            borrowMap.put(id, reservation);
        }
        return withBorrowMark(dto, borrowMap);
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
        block.setCalibrationCycleMonths(dto.getCalibrationCycleMonths() != null
                ? dto.getCalibrationCycleMonths() : CalibrationService.DEFAULT_CYCLE_MONTHS);
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
        if (dto.getCalibrationCycleMonths() != null) {
            if (dto.getCalibrationCycleMonths() < 1 || dto.getCalibrationCycleMonths() > 120) {
                throw new RuntimeException("校准周期需在 1~120 个月之间");
            }
            block.setCalibrationCycleMonths(dto.getCalibrationCycleMonths());
        }
        // 挂起状态只能随校准结论变化，不允许档案编辑直接修改
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
        List<BufferBlock> blocks = new ArrayList<>();
        for (BlockLineBinding binding : bindings) {
            bufferBlockRepository.findById(binding.getBlockId()).ifPresent(blocks::add);
        }
        Map<Long, CalibrationStatusVO> statusMap = calibrationService.statusMapOfBlocks(blocks);
        Map<Long, String> pendingMap = pendingTransferNoMap();
        Map<Long, BlockBorrowReservation> borrowMap =
                borrowReservationServiceProvider.getObject().activeReservationMap();
        List<BufferBlockDTO> result = new ArrayList<>();
        for (BufferBlock block : blocks) {
            result.add(withBorrowMark(
                    withPendingTransfer(convertToDTO(block, statusMap.get(block.getId())), pendingMap),
                    borrowMap));
        }
        return result;
    }

    /**
     * 挡块是否存在待确认移交单（借用预约登记时排除这类挡块）。
     */
    public boolean hasPendingTransfer(Long blockId) {
        return !blockTransferRepository.findByBlockIdAndStatusOrderByCreateTimeDesc(
                blockId, BlockTransfer.STATUS_PENDING).isEmpty();
    }

    /**
     * 挡块是否处于借用占用中（已预约/已取走/逾时未取）。
     */
    public boolean hasActiveBorrow(Long blockId) {
        return borrowReservationServiceProvider.getObject().activeReservationMap().containsKey(blockId);
    }

    /**
     * 待确认移交单按挡块归集（同一挡块同时只允许一张待确认单），供列表/角标统一标记。
     */
    private Map<Long, String> pendingTransferNoMap() {
        Map<Long, String> map = new HashMap<>();
        for (BlockTransfer transfer : blockTransferRepository.findByStatus(BlockTransfer.STATUS_PENDING)) {
            map.putIfAbsent(transfer.getBlockId(), transfer.getTransferNo());
        }
        return map;
    }

    private BufferBlockDTO withPendingTransfer(BufferBlockDTO dto, Map<Long, String> pendingMap) {
        String transferNo = pendingMap.get(dto.getId());
        dto.setPendingTransfer(transferNo != null);
        dto.setPendingTransferNo(transferNo);
        return dto;
    }

    /**
     * 占用期间（已预约/已取走/逾时未取）在档案上叠加“已约出”标记与预约单号、
     * 约定取用时间、借用班组、归还点；已取走且过计划还期的同时标“超期未还”，
     * 与预约台列表同一实时派生口径；取消/归还后该标记自然消失。
     */
    private BufferBlockDTO withBorrowMark(BufferBlockDTO dto, Map<Long, BlockBorrowReservation> borrowMap) {
        BlockBorrowReservation reservation = borrowMap.get(dto.getId());
        dto.setBorrowedOut(reservation != null);
        if (reservation != null) {
            dto.setBorrowReservationNo(reservation.getReservationNo());
            dto.setBorrowStatus(reservation.getStatus());
            dto.setBorrowTeamName(reservation.getTeamName());
            dto.setBorrowPickupTime(reservation.getPickupTime());
            dto.setBorrowReturnPoint(reservation.getReturnPoint());
            dto.setBorrowPlannedReturnTime(reservation.getPlannedReturnTime());
            dto.setBorrowOverdueReturn(Boolean.TRUE.equals(reservation.getOverdueReturn()));
            dto.setBorrowOverdueReturnDuration(reservation.getOverdueReturnDuration());
        }
        return dto;
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

    private BufferBlockDTO convertToDTO(BufferBlock block, CalibrationStatusVO status) {
        BufferBlockDTO dto = new BufferBlockDTO();
        dto.setId(block.getId());
        dto.setBlockCode(block.getBlockCode());
        dto.setAdapterModel(block.getAdapterModel());
        dto.setThickness(block.getThickness());
        dto.setImageUrl(block.getImageUrl());
        dto.setSpecTemplate(block.getSpecTemplate());
        dto.setCalibrationCycleMonths(block.getCalibrationCycleMonths() != null
                ? block.getCalibrationCycleMonths() : CalibrationService.DEFAULT_CYCLE_MONTHS);
        dto.setServiceStatus(block.getServiceStatus());
        dto.setSuspendReason(block.getSuspendReason());
        dto.setSuspendTime(CalibrationService.formatTime(block.getSuspendTime()));

        BlockLineBinding binding = getCurrentBinding(block.getId());
        if (binding != null) {
            dto.setLineId(binding.getLineId());
            ProductionLine line = productionLineService.getById(binding.getLineId());
            if (line != null) {
                dto.setLineName(line.getLineName());
            }
        }

        if (status != null) {
            dto.setCalibrationStatus(status.getStatus());
            dto.setLastCalibrationResult(status.getLastResult());
            dto.setLastCalibrationDate(status.getLastCalibrationDate());
            dto.setLastCalibrator(status.getLastCalibrator());
            dto.setValidUntil(status.getValidUntil());
            dto.setNextDueDate(status.getNextDueDate());
            dto.setOverdueDays(status.getOverdueDays());
            dto.setDaysUntilDue(status.getDaysUntilDue());
        }
        return dto;
    }
}
