package com.bufferblock.dto;

import java.time.LocalDate;

/**
 * 点检工装台账行：建账字段 + 由最近一次校准记录实时派生的校准状态。
 * blocked=true 的工装即点检打卡拦截清单来源。
 */
public class GaugeToolItemVO {

    private Long id;

    private String toolCode;

    private String toolType;

    private String toolTypeName;

    private String specModel;

    private LocalDate calibrationDueDate;

    private String keeperTeam;

    private String remark;

    private Integer disabled;

    /**
     * 派生校准状态：
     * NORMAL 合格在期 / OVERDUE 到期未校准 / FAIL 最近校准不合格 / UNCALIBRATED 从未校准 / DISABLED 已停用
     */
    private String calibrationStatus;

    /** 是否拦截点检打卡（在期合格=false；到期或不合格=true；停用不拦截） */
    private boolean blocked;

    /** 拦截原因：OVERDUE 到期未校准 / FAIL 校准结论不合格 */
    private String blockedReason;

    private Long overdueDays;

    // ---- 最近一次校准记录（同源展示） ----

    private LocalDate lastCalibrationDate;

    private String lastResult;

    private String lastCalibrator;

    private LocalDate lastValidUntil;

    private LocalDate lastNextDueDate;

    private String lastNote;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getToolCode() { return toolCode; }
    public void setToolCode(String toolCode) { this.toolCode = toolCode; }

    public String getToolType() { return toolType; }
    public void setToolType(String toolType) { this.toolType = toolType; }

    public String getToolTypeName() { return toolTypeName; }
    public void setToolTypeName(String toolTypeName) { this.toolTypeName = toolTypeName; }

    public String getSpecModel() { return specModel; }
    public void setSpecModel(String specModel) { this.specModel = specModel; }

    public LocalDate getCalibrationDueDate() { return calibrationDueDate; }
    public void setCalibrationDueDate(LocalDate calibrationDueDate) { this.calibrationDueDate = calibrationDueDate; }

    public String getKeeperTeam() { return keeperTeam; }
    public void setKeeperTeam(String keeperTeam) { this.keeperTeam = keeperTeam; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Integer getDisabled() { return disabled; }
    public void setDisabled(Integer disabled) { this.disabled = disabled; }

    public String getCalibrationStatus() { return calibrationStatus; }
    public void setCalibrationStatus(String calibrationStatus) { this.calibrationStatus = calibrationStatus; }

    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }

    public String getBlockedReason() { return blockedReason; }
    public void setBlockedReason(String blockedReason) { this.blockedReason = blockedReason; }

    public Long getOverdueDays() { return overdueDays; }
    public void setOverdueDays(Long overdueDays) { this.overdueDays = overdueDays; }

    public LocalDate getLastCalibrationDate() { return lastCalibrationDate; }
    public void setLastCalibrationDate(LocalDate lastCalibrationDate) { this.lastCalibrationDate = lastCalibrationDate; }

    public String getLastResult() { return lastResult; }
    public void setLastResult(String lastResult) { this.lastResult = lastResult; }

    public String getLastCalibrator() { return lastCalibrator; }
    public void setLastCalibrator(String lastCalibrator) { this.lastCalibrator = lastCalibrator; }

    public LocalDate getLastValidUntil() { return lastValidUntil; }
    public void setLastValidUntil(LocalDate lastValidUntil) { this.lastValidUntil = lastValidUntil; }

    public LocalDate getLastNextDueDate() { return lastNextDueDate; }
    public void setLastNextDueDate(LocalDate lastNextDueDate) { this.lastNextDueDate = lastNextDueDate; }

    public String getLastNote() { return lastNote; }
    public void setLastNote(String lastNote) { this.lastNote = lastNote; }
}
