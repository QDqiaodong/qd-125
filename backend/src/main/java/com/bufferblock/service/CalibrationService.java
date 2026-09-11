package com.bufferblock.service;

import com.bufferblock.dto.CalibrationCreateDTO;
import com.bufferblock.dto.CalibrationStatusVO;
import com.bufferblock.entity.BlockCalibration;
import com.bufferblock.entity.BlockLineBinding;
import com.bufferblock.entity.BufferBlock;
import com.bufferblock.repository.BlockCalibrationRepository;
import com.bufferblock.repository.BlockLineBindingRepository;
import com.bufferblock.repository.BufferBlockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 挡块校准业务：
 * <ul>
 *     <li>校准员录入校准结论、有效期、下次应校日期与校准周期；</li>
 *     <li>结论不合格（FAIL）必须挂起待修，合格（PASS）复校通过才解除挂起重新上线；</li>
 *     <li>挂起待修或校准逾期的挡块不允许办理移交（不改动产线绑定）；</li>
 *     <li>到期标记/挂起状态全部由已落库的校准记录与挡块状态实时派生，刷新后保持一致。</li>
 * </ul>
 */
@Service
public class CalibrationService {

    public static final Integer DEFAULT_CYCLE_MONTHS = 12;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BlockCalibrationRepository calibrationRepository;
    private final BufferBlockRepository blockRepository;
    private final BlockLineBindingRepository bindingRepository;

    public CalibrationService(BlockCalibrationRepository calibrationRepository,
                              BufferBlockRepository blockRepository,
                              BlockLineBindingRepository bindingRepository) {
        this.calibrationRepository = calibrationRepository;
        this.blockRepository = blockRepository;
        this.bindingRepository = bindingRepository;
    }

    public List<BlockCalibration> getHistory(Long blockId) {
        return calibrationRepository.findByBlockIdOrderByCalibrationDateDescIdDesc(blockId);
    }

    public BlockCalibration getLatest(Long blockId) {
        return calibrationRepository.findFirstByBlockIdOrderByCalibrationDateDescIdDesc(blockId).orElse(null);
    }

    /**
     * 录入校准结果。挡块必须已绑定产线；FAIL 挂起待修，PASS 解除挂起。
     */
    @Transactional
    public BlockCalibration createCalibration(CalibrationCreateDTO dto) {
        validate(dto);

        BufferBlock block = blockRepository.findByIdForUpdate(dto.getBlockId())
                .orElseThrow(() -> new RuntimeException("挡块不存在"));

        BlockLineBinding binding = bindingRepository
                .findByBlockIdAndIsCurrent(dto.getBlockId(), 1).orElse(null);
        if (binding == null) {
            throw new RuntimeException("该挡块尚未绑定产线，绑定后才能录入校准结果");
        }

        Integer cycle = dto.getCycleMonths() != null ? dto.getCycleMonths()
                : (block.getCalibrationCycleMonths() != null ? block.getCalibrationCycleMonths() : DEFAULT_CYCLE_MONTHS);

        LocalDate calibrationDate = dto.getCalibrationDate();
        if (dto.getValidUntil().isBefore(calibrationDate)) {
            throw new RuntimeException("有效期至不能早于校准日期");
        }
        if (dto.getNextDueDate().isBefore(calibrationDate)) {
            throw new RuntimeException("下次应校日期不能早于校准日期");
        }

        BlockCalibration calibration = new BlockCalibration();
        calibration.setBlockId(block.getId());
        calibration.setCalibrationDate(calibrationDate);
        calibration.setResult(dto.getResult());
        calibration.setValidUntil(dto.getValidUntil());
        calibration.setNextDueDate(dto.getNextDueDate());
        calibration.setCycleMonths(cycle);
        calibration.setCalibrator(dto.getCalibrator().trim());
        calibration.setNote(dto.getNote());
        calibration = calibrationRepository.saveAndFlush(calibration);

        // 结论不合格必须挂起待修；修好复校合格才解除挂起，状态与校准记录同事务落库
        if (BlockCalibration.RESULT_FAIL.equals(dto.getResult())) {
            block.setServiceStatus(BufferBlock.STATUS_SUSPENDED);
            String reason = "校准不合格，挂起待修（" + calibrationDate + "，校准人："
                    + calibration.getCalibrator()
                    + (dto.getNote() != null && !dto.getNote().isBlank() ? "；" + dto.getNote().trim() : "")
                    + "），修好复校合格后方可重新上线";
            block.setSuspendReason(reason);
            block.setSuspendTime(LocalDateTime.now());
        } else {
            block.setServiceStatus(BufferBlock.STATUS_IN_SERVICE);
            block.setSuspendReason(null);
            block.setSuspendTime(null);
        }
        if (dto.getCycleMonths() != null) {
            block.setCalibrationCycleMonths(dto.getCycleMonths());
        }
        blockRepository.save(block);

        return calibration;
    }

    private void validate(CalibrationCreateDTO dto) {
        if (dto == null) {
            throw new RuntimeException("校准参数不能为空");
        }
        if (dto.getBlockId() == null) {
            throw new RuntimeException("请选择挡块");
        }
        if (dto.getCalibrationDate() == null) {
            throw new RuntimeException("请选择校准日期");
        }
        if (!BlockCalibration.RESULT_PASS.equals(dto.getResult())
                && !BlockCalibration.RESULT_FAIL.equals(dto.getResult())) {
            throw new RuntimeException("请选择校准结论（合格/不合格）");
        }
        if (dto.getValidUntil() == null) {
            throw new RuntimeException("请填写本次校准有效期");
        }
        if (dto.getNextDueDate() == null) {
            throw new RuntimeException("请填写下次应校日期");
        }
        if (dto.getCalibrator() == null || dto.getCalibrator().isBlank()) {
            throw new RuntimeException("请填写校准人");
        }
        if (dto.getCycleMonths() != null && (dto.getCycleMonths() < 1 || dto.getCycleMonths() > 120)) {
            throw new RuntimeException("校准周期需在 1~120 个月之间");
        }
    }

    /**
     * 计算单个挡块的校准状态（挂起优先于逾期，未校准单列）。
     */
    public CalibrationStatusVO statusOf(Long blockId) {
        BufferBlock block = blockRepository.findById(blockId).orElse(null);
        if (block == null) {
            return null;
        }
        return buildStatus(block, getLatest(blockId), LocalDate.now());
    }

    /**
     * 批量取多个挡块的校准状态，避免列表 N+1 查询。
     */
    public Map<Long, CalibrationStatusVO> statusMapOfBlocks(List<BufferBlock> blocks) {
        Map<Long, CalibrationStatusVO> result = new HashMap<>();
        if (blocks == null || blocks.isEmpty()) {
            return result;
        }
        List<Long> blockIds = blocks.stream().map(BufferBlock::getId).toList();
        Map<Long, BlockCalibration> latestMap = new HashMap<>();
        for (BlockCalibration c : calibrationRepository.findByBlockIdInOrderByCalibrationDateDescIdDesc(blockIds)) {
            // 已按校准日期倒序，每个挡块只保留第一条（最近一次）
            latestMap.putIfAbsent(c.getBlockId(), c);
        }
        LocalDate today = LocalDate.now();
        for (BufferBlock block : blocks) {
            result.put(block.getId(), buildStatus(block, latestMap.get(block.getId()), today));
        }
        return result;
    }

    private CalibrationStatusVO buildStatus(BufferBlock block, BlockCalibration latest, LocalDate today) {
        CalibrationStatusVO vo = new CalibrationStatusVO();
        vo.setBlockId(block.getId());
        if (BufferBlock.STATUS_SUSPENDED.equals(block.getServiceStatus())) {
            vo.setStatus(CalibrationStatusVO.SUSPENDED);
            vo.setSuspendReason(block.getSuspendReason());
        } else if (latest == null) {
            vo.setStatus(CalibrationStatusVO.UNCALIBRATED);
        } else if (latest.getNextDueDate().isBefore(today)) {
            vo.setStatus(CalibrationStatusVO.OVERDUE);
            vo.setOverdueDays((int) ChronoUnit.DAYS.between(latest.getNextDueDate(), today));
        } else {
            vo.setStatus(CalibrationStatusVO.NORMAL);
        }
        if (latest != null) {
            vo.setLastResult(latest.getResult());
            vo.setLastCalibrationDate(latest.getCalibrationDate());
            vo.setValidUntil(latest.getValidUntil());
            vo.setNextDueDate(latest.getNextDueDate());
            vo.setLastCalibrator(latest.getCalibrator());
        }
        return vo;
    }

    /**
     * 移交前置校验：挂起待修或校准逾期的挡块不得移交（不能改绑定）。
     * 未校准的挡块不阻断移交，但建议尽快补校。
     */
    public void assertTransferable(Long blockId) {
        CalibrationStatusVO status = statusOf(blockId);
        if (status == null) {
            return;
        }
        if (CalibrationStatusVO.SUSPENDED.equals(status.getStatus())) {
            throw new RuntimeException("该挡块校准不合格已挂起待修，修好复校合格前不能办理移交；挂起原因："
                    + status.getSuspendReason());
        }
        if (CalibrationStatusVO.OVERDUE.equals(status.getStatus())) {
            throw new RuntimeException("该挡块校准已逾期（下次应校日期 " + status.getNextDueDate()
                    + "，逾期 " + status.getOverdueDays() + " 天），须先完成校准并合格后才能办理移交，产线绑定不予变更");
        }
    }

    public static String formatTime(LocalDateTime time) {
        return time == null ? null : time.format(TIME_FORMATTER);
    }
}
