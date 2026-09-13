package com.bufferblock.service;

import com.bufferblock.dto.InspectionCreateDTO;
import com.bufferblock.dto.InspectionItemVO;
import com.bufferblock.dto.InspectionOverviewVO;
import com.bufferblock.dto.InspectionQueryDTO;
import com.bufferblock.entity.BlockInspection;
import com.bufferblock.entity.BlockLineBinding;
import com.bufferblock.entity.BufferBlock;
import com.bufferblock.entity.ProductionLine;
import com.bufferblock.repository.BlockInspectionRepository;
import com.bufferblock.repository.BlockLineBindingRepository;
import com.bufferblock.repository.BufferBlockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 挡块班次点检业务：
 * <ul>
 *     <li>点检台账按产线（车间节点取其下全部产线）筛选当前绑定在产线上的在用挡块；</li>
 *     <li>每块挡块每个班次由点检人打卡“可用/不可用”，点检人、班次、结论与时刻全部落库；</li>
 *     <li>台账行与挡块档案展示的“最近一次点检结论”全部由已落库记录实时派生（同一份数据源），
 *     刷新或重进页面后条数与列表保持一致。</li>
 * </ul>
 */
@Service
public class InspectionService {

    private final BlockInspectionRepository inspectionRepository;
    private final BufferBlockRepository blockRepository;
    private final BlockLineBindingRepository bindingRepository;
    private final ProductionLineService productionLineService;

    public InspectionService(BlockInspectionRepository inspectionRepository,
                             BufferBlockRepository blockRepository,
                             BlockLineBindingRepository bindingRepository,
                             ProductionLineService productionLineService) {
        this.inspectionRepository = inspectionRepository;
        this.blockRepository = blockRepository;
        this.bindingRepository = bindingRepository;
        this.productionLineService = productionLineService;
    }

    /** 某挡块的完整点检记录（最近一次在前） */
    public List<BlockInspection> getHistory(Long blockId) {
        return inspectionRepository.findByBlockIdOrderByInspectionTimeDescIdDesc(blockId);
    }

    /** 某挡块最近一次点检记录（从未点检返回 null） */
    public BlockInspection getLatest(Long blockId) {
        return inspectionRepository.findFirstByBlockIdOrderByInspectionTimeDescIdDesc(blockId).orElse(null);
    }

    /**
     * 批量取多个挡块的最近一次点检记录，避免列表 N+1 查询。
     * 仓储已按点检时刻倒序返回，每个挡块只保留第一条。
     */
    public Map<Long, BlockInspection> latestMapOfBlocks(List<BufferBlock> blocks) {
        Map<Long, BlockInspection> latestMap = new HashMap<>();
        if (blocks == null || blocks.isEmpty()) {
            return latestMap;
        }
        List<Long> blockIds = blocks.stream().map(BufferBlock::getId).toList();
        for (BlockInspection inspection : inspectionRepository.findByBlockIdInOrderByInspectionTimeDescIdDesc(blockIds)) {
            latestMap.putIfAbsent(inspection.getBlockId(), inspection);
        }
        return latestMap;
    }

    /**
     * 点检台账概览：按产线筛选在用挡块，逐块带出最近一次点检结论，
     * 并从同一份清单统计可用/不可用/未点检条数。
     */
    public InspectionOverviewVO getOverview(InspectionQueryDTO query) {
        Set<Long> targetLineIds = resolveTargetLineIds(query == null ? null : query.getLineId());
        String shiftFilter = query == null ? null : normalize(query.getShiftCode());
        String resultFilter = query == null ? null : normalize(query.getResult());
        String keyword = query == null || query.getKeyword() == null
                ? null : query.getKeyword().trim().toLowerCase();

        List<BlockLineBinding> bindings = targetLineIds.isEmpty()
                ? List.of()
                : bindingRepository.findByLineIdInAndIsCurrent(new ArrayList<>(targetLineIds), 1);
        List<BufferBlock> blocks = new ArrayList<>();
        Map<Long, BlockLineBinding> bindingByBlock = new HashMap<>();
        for (BlockLineBinding binding : bindings) {
            // 同一挡块只应有一条当前绑定；过滤挂起待修，只保留在用挡块
            blockRepository.findById(binding.getBlockId()).ifPresent(block -> {
                if (BufferBlock.STATUS_IN_SERVICE.equals(block.getServiceStatus())) {
                    blocks.add(block);
                    bindingByBlock.put(block.getId(), binding);
                }
            });
        }
        Map<Long, BlockInspection> latestMap = latestMapOfBlocks(blocks);

        InspectionOverviewVO overview = new InspectionOverviewVO();
        for (BufferBlock block : blocks) {
            BlockInspection latest = latestMap.get(block.getId());
            if (!matchesFilters(latest, shiftFilter, resultFilter, keyword, block)) {
                continue;
            }
            overview.getItems().add(toItemVO(block, bindingByBlock.get(block.getId()), latest));
        }
        overview.getItems().sort((a, b) -> {
            String lineA = a.getLineName() == null ? "" : a.getLineName();
            String lineB = b.getLineName() == null ? "" : b.getLineName();
            int cmp = lineA.compareTo(lineB);
            if (cmp != 0) {
                return cmp;
            }
            return a.getBlockCode().compareTo(b.getBlockCode());
        });

        overview.setTotalCount(overview.getItems().size());
        for (InspectionItemVO item : overview.getItems()) {
            if (item.isNeverInspected()) {
                overview.setNeverInspectedCount(overview.getNeverInspectedCount() + 1);
            } else if (BlockInspection.RESULT_USABLE.equals(item.getLastResult())) {
                overview.setUsableCount(overview.getUsableCount() + 1);
            } else if (BlockInspection.RESULT_UNUSABLE.equals(item.getLastResult())) {
                overview.setUnusableCount(overview.getUnusableCount() + 1);
            }
        }
        return overview;
    }

    /**
     * 班次点检打卡。挡块必须在用且当前绑定在产线上；点检人、班次、结论同事务落库。
     */
    @Transactional
    public BlockInspection createInspection(InspectionCreateDTO dto) {
        validate(dto);

        BufferBlock block = blockRepository.findByIdForUpdate(dto.getBlockId())
                .orElseThrow(() -> new RuntimeException("挡块不存在"));
        if (!BufferBlock.STATUS_IN_SERVICE.equals(block.getServiceStatus())) {
            throw new RuntimeException("该挡块为挂起待修状态，不在在用范围内，不能进行班次点检打卡");
        }
        BlockLineBinding binding = bindingRepository.findByBlockIdAndIsCurrent(dto.getBlockId(), 1).orElse(null);
        if (binding == null) {
            throw new RuntimeException("该挡块尚未绑定产线，绑定上线后才能进行班次点检打卡");
        }

        BlockInspection inspection = new BlockInspection();
        inspection.setBlockId(block.getId());
        inspection.setInspectionTime(dto.getInspectionTime() != null ? dto.getInspectionTime() : LocalDateTime.now());
        inspection.setShiftCode(dto.getShiftCode());
        inspection.setInspector(dto.getInspector().trim());
        inspection.setResult(dto.getResult());
        inspection.setNote(dto.getNote() != null && !dto.getNote().isBlank() ? dto.getNote().trim() : null);
        return inspectionRepository.saveAndFlush(inspection);
    }

    private void validate(InspectionCreateDTO dto) {
        if (dto == null) {
            throw new RuntimeException("点检参数不能为空");
        }
        if (dto.getBlockId() == null) {
            throw new RuntimeException("请选择要点检打卡的挡块");
        }
        if (!isValidShift(dto.getShiftCode())) {
            throw new RuntimeException("请选择点检班次（早班/中班/晚班）");
        }
        if (!BlockInspection.RESULT_USABLE.equals(dto.getResult())
                && !BlockInspection.RESULT_UNUSABLE.equals(dto.getResult())) {
            throw new RuntimeException("请选择本次点检结论（可用/不可用）");
        }
        if (dto.getInspector() == null || dto.getInspector().isBlank()) {
            throw new RuntimeException("请填写点检人");
        }
        if (dto.getInspector().trim().length() > 50) {
            throw new RuntimeException("点检人姓名不能超过 50 个字");
        }
    }

    private boolean matchesFilters(BlockInspection latest, String shiftFilter, String resultFilter,
                                   String keyword, BufferBlock block) {
        if (shiftFilter != null && (latest == null || !shiftFilter.equals(latest.getShiftCode()))) {
            return false;
        }
        if (resultFilter != null) {
            switch (resultFilter) {
                case BlockInspection.RESULT_USABLE, BlockInspection.RESULT_UNUSABLE -> {
                    if (latest == null || !resultFilter.equals(latest.getResult())) {
                        return false;
                    }
                }
                case "NEVER" -> {
                    if (latest != null) {
                        return false;
                    }
                }
                default -> {
                    // 未知筛选值不拦截
                }
            }
        }
        if (keyword != null && !keyword.isEmpty()) {
            boolean codeHit = block.getBlockCode() != null
                    && block.getBlockCode().toLowerCase().contains(keyword);
            boolean modelHit = block.getAdapterModel() != null
                    && block.getAdapterModel().toLowerCase().contains(keyword);
            if (!codeHit && !modelHit) {
                return false;
            }
        }
        return true;
    }

    private InspectionItemVO toItemVO(BufferBlock block, BlockLineBinding binding, BlockInspection latest) {
        InspectionItemVO vo = new InspectionItemVO();
        vo.setBlockId(block.getId());
        vo.setBlockCode(block.getBlockCode());
        vo.setAdapterModel(block.getAdapterModel());
        vo.setThickness(block.getThickness());
        vo.setSpecTemplate(block.getSpecTemplate());
        if (binding != null) {
            vo.setLineId(binding.getLineId());
            ProductionLine line = productionLineService.getById(binding.getLineId());
            if (line != null) {
                vo.setLineName(line.getLineName());
            }
        }
        if (latest != null) {
            vo.setLastInspectionTime(latest.getInspectionTime());
            vo.setLastShiftCode(latest.getShiftCode());
            vo.setLastInspector(latest.getInspector());
            vo.setLastResult(latest.getResult());
            vo.setLastNote(latest.getNote());
        } else {
            vo.setNeverInspected(true);
        }
        return vo;
    }

    /**
     * 解析筛选产线范围：
     * 不选 = 全部叶子产线；选中车间节点 = 其树下全部产线；选中具体产线 = 该产线。
     */
    private Set<Long> resolveTargetLineIds(Long selectedLineId) {
        List<ProductionLine> allLines = productionLineService.getAllLines();
        Set<Long> ids = new HashSet<>();
        if (selectedLineId == null) {
            for (ProductionLine line : allLines) {
                if (line.getParentId() != null) {
                    ids.add(line.getId());
                }
            }
            return ids;
        }
        ProductionLine selected = allLines.stream()
                .filter(line -> line.getId().equals(selectedLineId)).findFirst().orElse(null);
        if (selected == null) {
            throw new RuntimeException("所选产线不存在，请刷新后重试");
        }
        if (selected.getParentId() == null) {
            // 车间节点：展开其树下全部产线（本地构建父子关系，避免改动缓存的树对象）
            Map<Long, List<ProductionLine>> childrenMap = new HashMap<>();
            for (ProductionLine line : allLines) {
                if (line.getParentId() != null) {
                    childrenMap.computeIfAbsent(line.getParentId(), k -> new ArrayList<>()).add(line);
                }
            }
            List<ProductionLine> stack = new ArrayList<>(childrenMap.getOrDefault(selected.getId(), List.of()));
            while (!stack.isEmpty()) {
                ProductionLine node = stack.remove(stack.size() - 1);
                ids.add(node.getId());
                stack.addAll(childrenMap.getOrDefault(node.getId(), List.of()));
            }
        } else {
            ids.add(selected.getId());
        }
        return ids;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isValidShift(String shiftCode) {
        return BlockInspection.SHIFT_MORNING.equals(shiftCode)
                || BlockInspection.SHIFT_AFTERNOON.equals(shiftCode)
                || BlockInspection.SHIFT_NIGHT.equals(shiftCode);
    }
}
