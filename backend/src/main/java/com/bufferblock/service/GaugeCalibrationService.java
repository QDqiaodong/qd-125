package com.bufferblock.service;

import com.bufferblock.dto.GaugeBlockedVO;
import com.bufferblock.dto.GaugeCalibrationCreateDTO;
import com.bufferblock.dto.GaugeToolCreateDTO;
import com.bufferblock.dto.GaugeToolItemVO;
import com.bufferblock.dto.GaugeToolOverviewVO;
import com.bufferblock.dto.GaugeToolQueryDTO;
import com.bufferblock.entity.GaugeCalibration;
import com.bufferblock.entity.GaugeTool;
import com.bufferblock.exception.GaugeCalibrationBlockedException;
import com.bufferblock.repository.GaugeCalibrationRepository;
import com.bufferblock.repository.GaugeToolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 点检工装校准台业务：
 * <ul>
 *     <li>卡尺 / 塞尺 / 百分表建台账，登记工装编号、校准到期日与保管班组；</li>
 *     <li>每次校准登记结论（合格/不合格）、有效期与下次应校日；合格时把台账到期日同步为下次应校日；</li>
 *     <li>“到期未校准 / 最近校准结论不合格”的在期工装实时派生为拦截清单，
 *     班次点检打卡前统一校验，拦截并逐条列出超期工装编号；校准合格后同一班次再打卡即放行；</li>
 *     <li>校准台超期条数、拦截清单、点检打卡拦截全部由同一份已落库台账/校准记录实时派生，
 *     刷新后条数与编号一致。</li>
 * </ul>
 */
@Service
public class GaugeCalibrationService {

    /** 合格在期 */
    public static final String STATUS_NORMAL = "NORMAL";
    /** 到期未校准 */
    public static final String STATUS_OVERDUE = "OVERDUE";
    /** 最近校准结论不合格 */
    public static final String STATUS_FAIL = "FAIL";
    /** 从未校准（台账到期日尚未到） */
    public static final String STATUS_UNCALIBRATED = "UNCALIBRATED";
    /** 已停用（不参与拦截） */
    public static final String STATUS_DISABLED = "DISABLED";

    /** 拦截原因：到期未校准 */
    public static final String REASON_OVERDUE = "OVERDUE";
    /** 拦截原因：校准结论不合格 */
    public static final String REASON_FAIL = "FAIL";

    private final GaugeToolRepository toolRepository;
    private final GaugeCalibrationRepository calibrationRepository;

    public GaugeCalibrationService(GaugeToolRepository toolRepository,
                                   GaugeCalibrationRepository calibrationRepository) {
        this.toolRepository = toolRepository;
        this.calibrationRepository = calibrationRepository;
    }

    // ==================== 台账 ====================

    /** 校准台概览：台账清单 + 各口径条数，条数与清单、点检拦截同源于已落库数据 */
    @Transactional(readOnly = true)
    public GaugeToolOverviewVO getOverview(GaugeToolQueryDTO query) {
        String typeFilter = normalize(query == null ? null : query.getToolType());
        String statusFilter = normalize(query == null ? null : query.getStatus());
        String teamFilter = normalize(query == null ? null : query.getKeeperTeam());
        String keyword = query == null || query.getKeyword() == null
                ? null : query.getKeyword().trim().toLowerCase();

        List<GaugeTool> tools = toolRepository.findAll();
        Map<Long, GaugeCalibration> latestMap = latestCalibrationMap(tools);
        LocalDate today = LocalDate.now();

        List<GaugeToolItemVO> all = new ArrayList<>();
        for (GaugeTool tool : tools) {
            all.add(toItemVO(tool, latestMap.get(tool.getId()), today));
        }

        GaugeToolOverviewVO overview = new GaugeToolOverviewVO();

        // 拦截清单：在期（未停用）且到期未校准或结论不合格，不受列表筛选影响
        List<GaugeToolItemVO> blockedItems = all.stream().filter(GaugeToolItemVO::isBlocked)
                .sorted(Comparator.comparing(GaugeToolItemVO::getToolCode))
                .collect(Collectors.toCollection(ArrayList::new));
        overview.setBlockedItems(blockedItems);
        overview.setBlockedCount(blockedItems.size());

        for (GaugeToolItemVO vo : all) {
            switch (vo.getCalibrationStatus()) {
                case STATUS_NORMAL -> overview.setNormalCount(overview.getNormalCount() + 1);
                case STATUS_OVERDUE -> overview.setOverdueCount(overview.getOverdueCount() + 1);
                case STATUS_FAIL -> overview.setFailCount(overview.getFailCount() + 1);
                case STATUS_UNCALIBRATED ->
                        overview.setUncalibratedCount(overview.getUncalibratedCount() + 1);
                case STATUS_DISABLED -> overview.setDisabledCount(overview.getDisabledCount() + 1);
                default -> { }
            }
            if (matchesFilters(vo, typeFilter, statusFilter, teamFilter, keyword)) {
                overview.getItems().add(vo);
            }
        }
        overview.getItems().sort(this::tableComparator);
        overview.setTotalCount(overview.getItems().size());
        return overview;
    }

    /** 建账 */
    @Transactional
    public GaugeTool createTool(GaugeToolCreateDTO dto) {
        validateTool(dto);
        String code = dto.getToolCode().trim();
        if (toolRepository.existsByToolCode(code)) {
            throw new RuntimeException("工装编号 " + code + " 已存在，请更换编号");
        }
        GaugeTool tool = new GaugeTool();
        applyToolFields(tool, dto);
        tool.setToolCode(code);
        tool.setDisabled(dto.getDisabled() != null && dto.getDisabled() == 1 ? 1 : 0);
        return toolRepository.saveAndFlush(tool);
    }

    /** 编辑台账（编号/类型/到期日/保管班组/停用） */
    @Transactional
    public GaugeTool updateTool(GaugeToolCreateDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new RuntimeException("缺少要编辑的工装ID");
        }
        validateTool(dto);
        GaugeTool tool = toolRepository.findByIdForUpdate(dto.getId())
                .orElseThrow(() -> new RuntimeException("工装不存在或已被删除"));
        String code = dto.getToolCode().trim();
        toolRepository.findByToolCode(code).ifPresent(existing -> {
            if (!existing.getId().equals(tool.getId())) {
                throw new RuntimeException("工装编号 " + code + " 已被其他工装占用，请更换编号");
            }
        });
        tool.setToolCode(code);
        applyToolFields(tool, dto);
        if (dto.getDisabled() != null) {
            tool.setDisabled(dto.getDisabled() == 1 ? 1 : 0);
        }
        return toolRepository.saveAndFlush(tool);
    }

    /** 某工装完整校准记录（最近一次在前） */
    @Transactional(readOnly = true)
    public List<GaugeCalibration> getCalibrationHistory(Long toolId) {
        return calibrationRepository.findByToolIdOrderByCalibrationDateDescIdDesc(toolId);
    }

    // ==================== 校准登记 ====================

    /**
     * 登记校准结果。合格时把台账校准到期日同步为“下次应校日期”，工装随即移出拦截清单；
     * 不合格不延展到期日，工装进入“校准结论不合格”拦截清单，待复校合格后放行。
     */
    @Transactional
    public GaugeCalibration createCalibration(GaugeCalibrationCreateDTO dto) {
        validateCalibration(dto);

        GaugeTool tool = toolRepository.findByIdForUpdate(dto.getToolId())
                .orElseThrow(() -> new RuntimeException("工装不存在或已被删除"));
        if (tool.getDisabled() != null && tool.getDisabled() == 1) {
            throw new RuntimeException("该工装已停用，不能登记校准；如需校准请先在台账启用");
        }

        LocalDate calibrationDate = dto.getCalibrationDate();
        if (dto.getValidUntil().isBefore(calibrationDate)) {
            throw new RuntimeException("有效期至不能早于校准日期");
        }
        if (dto.getNextDueDate().isBefore(calibrationDate)) {
            throw new RuntimeException("下次应校日期不能早于校准日期");
        }

        GaugeCalibration calibration = new GaugeCalibration();
        calibration.setToolId(tool.getId());
        calibration.setCalibrationDate(calibrationDate);
        calibration.setResult(dto.getResult());
        calibration.setValidUntil(dto.getValidUntil());
        calibration.setNextDueDate(dto.getNextDueDate());
        calibration.setCalibrator(dto.getCalibrator().trim());
        calibration.setNote(dto.getNote() != null && !dto.getNote().isBlank() ? dto.getNote().trim() : null);
        calibration = calibrationRepository.saveAndFlush(calibration);

        if (GaugeCalibration.RESULT_PASS.equals(dto.getResult())) {
            // 合格：台账到期日同步为本次下次应校日，同一班次再打卡即放行
            tool.setCalibrationDueDate(dto.getNextDueDate());
            toolRepository.saveAndFlush(tool);
        }
        return calibration;
    }

    // ==================== 点检打卡闸门 ====================

    /** 当前是否存在拦截点检打卡的工装（到期未校准或结论不合格的在期工装） */
    @Transactional(readOnly = true)
    public GaugeBlockedVO getBlockedTools() {
        List<GaugeTool> tools = toolRepository.findAll();
        Map<Long, GaugeCalibration> latestMap = latestCalibrationMap(tools);
        LocalDate today = LocalDate.now();
        List<GaugeToolItemVO> blocked = new ArrayList<>();
        for (GaugeTool tool : tools) {
            GaugeToolItemVO vo = toItemVO(tool, latestMap.get(tool.getId()), today);
            if (vo.isBlocked()) {
                blocked.add(vo);
            }
        }
        // 编号排序固定，校准台拦截清单与打卡拦截弹窗逐条对得上
        blocked.sort(Comparator.comparing(GaugeToolItemVO::getToolCode));
        GaugeBlockedVO vo = new GaugeBlockedVO();
        vo.setCount(blocked.size());
        vo.setItems(blocked);
        vo.setCodes(blocked.stream().map(GaugeToolItemVO::getToolCode).toList());
        return vo;
    }

    /**
     * 班次点检打卡前置闸门：存在任一件到期未校准或校准结论不合格的在期工装即拒绝打卡，
     * 抛出携带编号清单的结构化异常；工装是全产线共用的点检前提，对普通打卡与复检打卡一视同仁。
     */
    public void assertNoBlockedTools() {
        GaugeBlockedVO blocked = getBlockedTools();
        if (blocked.getCount() > 0) {
            throw new GaugeCalibrationBlockedException(buildBlockedMessage(blocked), blocked);
        }
    }

    /**
     * 按工装ID批量取实时派生状态（与校准台台账、拦截清单同一 {@link #toItemVO} 派生口径），
     * 供班组交班事项同源展示工装当前是否仍拦截中；不存在的工装ID不出现在返回Map中。
     */
    @Transactional(readOnly = true)
    public Map<Long, GaugeToolItemVO> getToolStatusMap(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return Map.of();
        }
        List<GaugeTool> tools = toolRepository.findAllById(toolIds);
        Map<Long, GaugeCalibration> latestMap = latestCalibrationMap(tools);
        LocalDate today = LocalDate.now();
        Map<Long, GaugeToolItemVO> result = new HashMap<>();
        for (GaugeTool tool : tools) {
            result.put(tool.getId(), toItemVO(tool, latestMap.get(tool.getId()), today));
        }
        return result;
    }

    private String buildBlockedMessage(GaugeBlockedVO blocked) {
        List<String> overdueCodes = blocked.getItems().stream()
                .filter(i -> REASON_OVERDUE.equals(i.getBlockedReason()))
                .map(GaugeToolItemVO::getToolCode).toList();
        List<String> failCodes = blocked.getItems().stream()
                .filter(i -> REASON_FAIL.equals(i.getBlockedReason()))
                .map(GaugeToolItemVO::getToolCode).toList();
        StringBuilder sb = new StringBuilder();
        sb.append("点检工装有 ").append(blocked.getCount())
                .append(" 件到期未校准或校准结论不合格（")
                .append(String.join("、", blocked.getCodes()))
                .append("），请先到工装校准台完成校准合格后再打卡");
        if (!overdueCodes.isEmpty()) {
            sb.append("；到期未校准：").append(String.join("、", overdueCodes));
        }
        if (!failCodes.isEmpty()) {
            sb.append("；结论不合格：").append(String.join("、", failCodes));
        }
        return sb.toString();
    }

    // ==================== 派生与校验 ====================

    /**
     * 批量取多件工装的最近一次校准记录，避免 N+1；仓储按校准日期倒序，每件只留第一条。
     */
    private Map<Long, GaugeCalibration> latestCalibrationMap(List<GaugeTool> tools) {
        Map<Long, GaugeCalibration> latestMap = new HashMap<>();
        if (tools == null || tools.isEmpty()) {
            return latestMap;
        }
        List<Long> toolIds = tools.stream().map(GaugeTool::getId).toList();
        for (GaugeCalibration c : calibrationRepository.findByToolIdInOrderByCalibrationDateDescIdDesc(toolIds)) {
            latestMap.putIfAbsent(c.getToolId(), c);
        }
        return latestMap;
    }

    /**
     * 由台账到期日 + 最近一次校准结论实时派生工装校准状态（不把派生状态落库）：
     * 停用 → DISABLED；最近结论不合格 → FAIL；台账到期日早于今天 → OVERDUE；
     * 从未校准且未到期 → UNCALIBRATED；其余 → NORMAL。
     */
    private GaugeToolItemVO toItemVO(GaugeTool tool, GaugeCalibration latest, LocalDate today) {
        GaugeToolItemVO vo = new GaugeToolItemVO();
        vo.setId(tool.getId());
        vo.setToolCode(tool.getToolCode());
        vo.setToolType(tool.getToolType());
        vo.setToolTypeName(typeName(tool.getToolType()));
        vo.setSpecModel(tool.getSpecModel());
        vo.setCalibrationDueDate(tool.getCalibrationDueDate());
        vo.setKeeperTeam(tool.getKeeperTeam());
        vo.setRemark(tool.getRemark());
        vo.setDisabled(tool.getDisabled());

        if (latest != null) {
            vo.setLastCalibrationDate(latest.getCalibrationDate());
            vo.setLastResult(latest.getResult());
            vo.setLastCalibrator(latest.getCalibrator());
            vo.setLastValidUntil(latest.getValidUntil());
            vo.setLastNextDueDate(latest.getNextDueDate());
            vo.setLastNote(latest.getNote());
        }

        boolean disabled = tool.getDisabled() != null && tool.getDisabled() == 1;
        if (disabled) {
            vo.setCalibrationStatus(STATUS_DISABLED);
        } else if (latest != null && GaugeCalibration.RESULT_FAIL.equals(latest.getResult())) {
            vo.setCalibrationStatus(STATUS_FAIL);
            vo.setBlocked(true);
            vo.setBlockedReason(REASON_FAIL);
        } else if (tool.getCalibrationDueDate() != null && tool.getCalibrationDueDate().isBefore(today)) {
            vo.setCalibrationStatus(STATUS_OVERDUE);
            vo.setBlocked(true);
            vo.setBlockedReason(REASON_OVERDUE);
            vo.setOverdueDays(ChronoUnit.DAYS.between(tool.getCalibrationDueDate(), today));
        } else if (latest == null) {
            vo.setCalibrationStatus(STATUS_UNCALIBRATED);
        } else {
            vo.setCalibrationStatus(STATUS_NORMAL);
        }
        return vo;
    }

    private boolean matchesFilters(GaugeToolItemVO vo, String typeFilter, String statusFilter,
                                   String teamFilter, String keyword) {
        if (typeFilter != null && !typeFilter.equals(vo.getToolType())) {
            return false;
        }
        if (statusFilter != null) {
            if ("BLOCKED".equals(statusFilter)) {
                if (!vo.isBlocked()) {
                    return false;
                }
            } else if (!statusFilter.equals(vo.getCalibrationStatus())) {
                return false;
            }
        }
        if (teamFilter != null && !teamFilter.equals(vo.getKeeperTeam())) {
            return false;
        }
        if (keyword != null && !keyword.isEmpty()) {
            boolean codeHit = vo.getToolCode() != null
                    && vo.getToolCode().toLowerCase().contains(keyword);
            boolean specHit = vo.getSpecModel() != null
                    && vo.getSpecModel().toLowerCase().contains(keyword);
            if (!codeHit && !specHit) {
                return false;
            }
        }
        return true;
    }

    /** 台账表排序：拦截工装在前（到期优先于不合格），再按到期日升序、编号升序 */
    private int tableComparator(GaugeToolItemVO a, GaugeToolItemVO b) {
        if (a.isBlocked() != b.isBlocked()) {
            return a.isBlocked() ? -1 : 1;
        }
        int ra = reasonOrder(a.getBlockedReason());
        int rb = reasonOrder(b.getBlockedReason());
        if (ra != rb) {
            return Integer.compare(ra, rb);
        }
        LocalDate da = a.getCalibrationDueDate();
        LocalDate db = b.getCalibrationDueDate();
        if (da != null && db != null && !da.equals(db)) {
            return da.compareTo(db);
        }
        if (da == null && db != null) {
            return 1;
        }
        if (da != null && db == null) {
            return -1;
        }
        return a.getToolCode().compareTo(b.getToolCode());
    }

    private int reasonOrder(String reason) {
        if (REASON_OVERDUE.equals(reason)) {
            return 0;
        }
        if (REASON_FAIL.equals(reason)) {
            return 1;
        }
        return 2;
    }

    private void applyToolFields(GaugeTool tool, GaugeToolCreateDTO dto) {
        tool.setToolType(dto.getToolType());
        tool.setSpecModel(blankToNull(dto.getSpecModel()));
        tool.setCalibrationDueDate(dto.getCalibrationDueDate());
        tool.setKeeperTeam(dto.getKeeperTeam().trim());
        tool.setRemark(blankToNull(dto.getRemark()));
    }

    private void validateTool(GaugeToolCreateDTO dto) {
        if (dto == null) {
            throw new RuntimeException("工装台账参数不能为空");
        }
        if (dto.getToolCode() == null || dto.getToolCode().isBlank()) {
            throw new RuntimeException("请填写工装编号");
        }
        if (dto.getToolCode().trim().length() > 50) {
            throw new RuntimeException("工装编号不能超过 50 个字符");
        }
        if (!isValidType(dto.getToolType())) {
            throw new RuntimeException("请选择工装类型（卡尺/塞尺/百分表）");
        }
        if (dto.getCalibrationDueDate() == null) {
            throw new RuntimeException("请登记校准到期日");
        }
        if (dto.getKeeperTeam() == null || dto.getKeeperTeam().isBlank()) {
            throw new RuntimeException("请填写保管班组");
        }
        if (dto.getKeeperTeam().trim().length() > 100) {
            throw new RuntimeException("保管班组名称不能超过 100 个字");
        }
    }

    private void validateCalibration(GaugeCalibrationCreateDTO dto) {
        if (dto == null) {
            throw new RuntimeException("校准参数不能为空");
        }
        if (dto.getToolId() == null) {
            throw new RuntimeException("请选择要校准的工装");
        }
        if (dto.getCalibrationDate() == null) {
            throw new RuntimeException("请选择校准日期");
        }
        if (!GaugeCalibration.RESULT_PASS.equals(dto.getResult())
                && !GaugeCalibration.RESULT_FAIL.equals(dto.getResult())) {
            throw new RuntimeException("请选择校准结论（合格/不合格）");
        }
        if (dto.getValidUntil() == null) {
            throw new RuntimeException("请填写本次校准有效期至");
        }
        if (dto.getNextDueDate() == null) {
            throw new RuntimeException("请填写下次应校日期");
        }
        if (dto.getCalibrator() == null || dto.getCalibrator().isBlank()) {
            throw new RuntimeException("请填写校准人");
        }
        if (dto.getCalibrator().trim().length() > 50) {
            throw new RuntimeException("校准人姓名不能超过 50 个字");
        }
    }

    private boolean isValidType(String type) {
        return GaugeTool.TYPE_CALIPER.equals(type)
                || GaugeTool.TYPE_FEELER.equals(type)
                || GaugeTool.TYPE_DIAL_INDICATOR.equals(type);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 工装类型编码转中文名 */
    public static String typeName(String type) {
        return switch (type == null ? "" : type) {
            case GaugeTool.TYPE_CALIPER -> "卡尺";
            case GaugeTool.TYPE_FEELER -> "塞尺";
            case GaugeTool.TYPE_DIAL_INDICATOR -> "百分表";
            default -> type;
        };
    }

    /** 拦截原因编码转中文名 */
    public static String reasonText(String reason) {
        return switch (reason == null ? "" : reason) {
            case REASON_OVERDUE -> "到期未校准";
            case REASON_FAIL -> "校准结论不合格";
            default -> reason == null ? "-" : reason;
        };
    }
}
