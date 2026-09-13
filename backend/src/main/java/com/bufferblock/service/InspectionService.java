package com.bufferblock.service;

import com.bufferblock.dto.InspectionCreateDTO;
import com.bufferblock.dto.InspectionItemVO;
import com.bufferblock.dto.InspectionOverviewVO;
import com.bufferblock.dto.InspectionPendingRecheckItemVO;
import com.bufferblock.dto.InspectionPendingRecheckVO;
import com.bufferblock.dto.InspectionQueryDTO;
import com.bufferblock.entity.BlockInspection;
import com.bufferblock.entity.BlockLineBinding;
import com.bufferblock.entity.BufferBlock;
import com.bufferblock.entity.ProductionLine;
import com.bufferblock.exception.InspectionPendingRecheckException;
import com.bufferblock.repository.BlockInspectionRepository;
import com.bufferblock.repository.BlockLineBindingRepository;
import com.bufferblock.repository.BufferBlockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
 *     刷新或重进页面后条数与列表保持一致；</li>
 *     <li>提交拦截：本产线同一班次已有“不可用且至今未复检通过”的其他挡块时，普通点检提交被拦截，
 *     响应逐条列出待复检挡块编号与条数；对这些挡块自身的复检打卡先落库（复检动作不被彼此阻塞，
 *     避免多块不可用时谁都无法复检），再返回仍未复检通过的挡块清单；复检全部通过后提交恢复正常。</li>
 * </ul>
 */
@Service
public class InspectionService {

    private final BlockInspectionRepository inspectionRepository;
    private final BufferBlockRepository blockRepository;
    private final BlockLineBindingRepository bindingRepository;
    private final ProductionLineService productionLineService;
    private final GaugeCalibrationService gaugeCalibrationService;

    public InspectionService(BlockInspectionRepository inspectionRepository,
                             BufferBlockRepository blockRepository,
                             BlockLineBindingRepository bindingRepository,
                             ProductionLineService productionLineService,
                             GaugeCalibrationService gaugeCalibrationService) {
        this.inspectionRepository = inspectionRepository;
        this.blockRepository = blockRepository;
        this.bindingRepository = bindingRepository;
        this.productionLineService = productionLineService;
        this.gaugeCalibrationService = gaugeCalibrationService;
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
     * “待复检通过”清单只按产线范围派生（不受班次/结论/关键字筛选影响），
     * 与提交拦截同源于已落库点检记录，刷新后条数与编号对得上。
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
        // 待复检清单：产线范围内、最近一次点检结论仍为“不可用”的在用挡块（不受列表筛选条件影响）
        List<InspectionPendingRecheckItemVO> pendingItems = new ArrayList<>();
        for (BufferBlock block : blocks) {
            BlockInspection latest = latestMap.get(block.getId());
            if (latest != null && BlockInspection.RESULT_UNUSABLE.equals(latest.getResult())) {
                BlockLineBinding binding = bindingByBlock.get(block.getId());
                InspectionPendingRecheckItemVO pending = new InspectionPendingRecheckItemVO();
                pending.setBlockId(block.getId());
                pending.setBlockCode(block.getBlockCode());
                pending.setShiftCode(latest.getShiftCode());
                pending.setShiftName(shiftName(latest.getShiftCode()));
                if (binding != null) {
                    pending.setLineId(binding.getLineId());
                    ProductionLine line = productionLineService.getById(binding.getLineId());
                    if (line != null) {
                        pending.setLineName(line.getLineName());
                    }
                }
                pendingItems.add(pending);
            }
        }
        pendingItems.sort(Comparator
                .comparing((InspectionPendingRecheckItemVO p) -> shiftOrder(p.getShiftCode()))
                .thenComparing(p -> p.getLineName() == null ? "" : p.getLineName())
                .thenComparing(InspectionPendingRecheckItemVO::getBlockCode));
        overview.setPendingRecheckItems(pendingItems);
        overview.setPendingRecheckCount(pendingItems.size());

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
     * <p>
     * 提交拦截口径：本产线同一班次已存在“最近一次结论仍为不可用”的其他在用挡块时——
     * <ul>
     *     <li>普通点检（本挡块当前不在待复检集合）直接拦截，事务回滚，返回待复检挡块编号清单；</li>
     *     <li>对不可用挡块自身的复检允许落库（避免多块不可用时互相锁死），
     *     复检为“可用”后该挡块即移出待复检集合，全部复检通过后再提交不再拦截；
     *     返回结果同时携带本产线该班次仍未复检通过的挡块清单供前端提示。</li>
     * </ul>
     */
    @Transactional
    public InspectionCheckInResult createInspection(InspectionCreateDTO dto) {
        validate(dto);

        // 点检工装闸门（全产线共用前提）：存在到期未校准或校准结论不合格的在期工装时，
        // 普通打卡与复检打卡一律先拦住并列出超期工装编号，不落任何点检记录；
        // 工装校准合格后同一班次再打卡即可通过。
        gaugeCalibrationService.assertNoBlockedTools();

        BufferBlock block = blockRepository.findByIdForUpdate(dto.getBlockId())
                .orElseThrow(() -> new RuntimeException("挡块不存在"));
        if (!BufferBlock.STATUS_IN_SERVICE.equals(block.getServiceStatus())) {
            throw new RuntimeException("该挡块为挂起待修状态，不在在用范围内，不能进行班次点检打卡");
        }
        BlockLineBinding binding = bindingRepository.findByBlockIdAndIsCurrent(dto.getBlockId(), 1).orElse(null);
        if (binding == null) {
            throw new RuntimeException("该挡块尚未绑定产线，绑定上线后才能进行班次点检打卡");
        }

        // 本次提交是否属于“不可用挡块自身的复检”：其最近一次结论为本班次不可用
        BlockInspection before = getLatest(block.getId());
        boolean recheckOfPending = before != null
                && BlockInspection.RESULT_UNUSABLE.equals(before.getResult())
                && dto.getShiftCode().equals(before.getShiftCode());

        BlockInspection inspection = new BlockInspection();
        inspection.setBlockId(block.getId());
        inspection.setInspectionTime(dto.getInspectionTime() != null ? dto.getInspectionTime() : LocalDateTime.now());
        inspection.setShiftCode(dto.getShiftCode());
        inspection.setInspector(dto.getInspector().trim());
        inspection.setResult(dto.getResult());
        inspection.setNote(dto.getNote() != null && !dto.getNote().isBlank() ? dto.getNote().trim() : null);
        inspection = inspectionRepository.saveAndFlush(inspection);

        // 以落库后的最新状态重新计算本产线该班次仍未复检通过的挡块
        InspectionPendingRecheckVO pending = pendingOnLine(binding.getLineId(), dto.getShiftCode());
        if (pending.getCount() > 0 && !recheckOfPending) {
            // 普通点检提交被拦截：回滚本次落库，返回结构化明细
            throw new InspectionPendingRecheckException(buildBlockMessage(pending), pending);
        }

        InspectionCheckInResult result = new InspectionCheckInResult();
        result.setInspection(inspection);
        result.setPendingRecheck(pending.getCount() > 0 ? pending : null);
        return result;
    }

    /**
     * 统计某产线某班次“不可用且尚未复检通过”的在用挡块：
     * 当前绑定在该产线、在用、且最近一次点检为本班次不可用。编号按挡块编号排序。
     */
    private InspectionPendingRecheckVO pendingOnLine(Long lineId, String shiftCode) {
        List<BlockLineBinding> bindings = bindingRepository.findByLineIdAndIsCurrent(lineId, 1);
        List<BufferBlock> peerBlocks = new ArrayList<>();
        for (BlockLineBinding peerBinding : bindings) {
            blockRepository.findById(peerBinding.getBlockId()).ifPresent(peer -> {
                if (BufferBlock.STATUS_IN_SERVICE.equals(peer.getServiceStatus())) {
                    peerBlocks.add(peer);
                }
            });
        }
        Map<Long, BlockInspection> latestMap = latestMapOfBlocks(peerBlocks);
        List<String> codes = new ArrayList<>();
        for (BufferBlock peer : peerBlocks) {
            BlockInspection latest = latestMap.get(peer.getId());
            if (latest != null
                    && BlockInspection.RESULT_UNUSABLE.equals(latest.getResult())
                    && shiftCode.equals(latest.getShiftCode())) {
                codes.add(peer.getBlockCode());
            }
        }
        codes.sort(String::compareTo);

        InspectionPendingRecheckVO vo = new InspectionPendingRecheckVO();
        vo.setCount(codes.size());
        vo.setShiftCode(shiftCode);
        vo.setShiftName(shiftName(shiftCode));
        vo.setLineId(lineId);
        ProductionLine line = productionLineService.getById(lineId);
        if (line != null) {
            vo.setLineName(line.getLineName());
        }
        vo.setBlockCodes(codes);
        return vo;
    }

    private String buildBlockMessage(InspectionPendingRecheckVO pending) {
        String lineText = pending.getLineName() != null ? pending.getLineName() : "本产线";
        return String.format(
                "%1$s%2$s已有 %3$d 块挡块点检结论为不可用且尚未复检通过（%4$s），"
                        + "请先对这些挡块复检通过后再提交其他点检。",
                lineText, pending.getShiftName(), pending.getCount(), String.join("、", pending.getBlockCodes()));
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
            vo.setPendingRecheck(BlockInspection.RESULT_UNUSABLE.equals(latest.getResult()));
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

    /** 班次编码转中文名 */
    public static String shiftName(String shiftCode) {
        return switch (shiftCode == null ? "" : shiftCode) {
            case BlockInspection.SHIFT_MORNING -> "早班";
            case BlockInspection.SHIFT_AFTERNOON -> "中班";
            case BlockInspection.SHIFT_NIGHT -> "晚班";
            default -> shiftCode;
        };
    }

    /** 班次排序：早 → 中 → 晚，未知班次排最后 */
    private static int shiftOrder(String shiftCode) {
        return switch (shiftCode == null ? "" : shiftCode) {
            case BlockInspection.SHIFT_MORNING -> 0;
            case BlockInspection.SHIFT_AFTERNOON -> 1;
            case BlockInspection.SHIFT_NIGHT -> 2;
            default -> 3;
        };
    }

    /** 点检打卡结果：落库记录 + 本产线该班次仍未复检通过的挡块清单（无则 null） */
    public static class InspectionCheckInResult {
        private BlockInspection inspection;
        private InspectionPendingRecheckVO pendingRecheck;

        public BlockInspection getInspection() { return inspection; }
        public void setInspection(BlockInspection inspection) { this.inspection = inspection; }

        public InspectionPendingRecheckVO getPendingRecheck() { return pendingRecheck; }
        public void setPendingRecheck(InspectionPendingRecheckVO pendingRecheck) {
            this.pendingRecheck = pendingRecheck;
        }
    }
}
