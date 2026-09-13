package com.bufferblock.dto;

import java.time.LocalDate;

/**
 * 点检工装台账建账/编辑入参：工装编号、类型（卡尺/塞尺/百分表）、
 * 建账校准到期日与保管班组。
 */
public class GaugeToolCreateDTO {

    /** 编辑时携带；建账时为空 */
    private Long id;

    private String toolCode;

    private String toolType;

    private String specModel;

    /** 校准到期日（建账登记，合格校准时按下次应校日自动刷新） */
    private LocalDate calibrationDueDate;

    private String keeperTeam;

    private String remark;

    /** 停用标记（编辑时可改）；停用工装不参与点检打卡拦截 */
    private Integer disabled;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getToolCode() { return toolCode; }
    public void setToolCode(String toolCode) { this.toolCode = toolCode; }

    public String getToolType() { return toolType; }
    public void setToolType(String toolType) { this.toolType = toolType; }

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
}
