package com.bufferblock.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 点检工装台账（卡尺 / 塞尺 / 百分表）。
 * 每件工装登记唯一编号、工装类型、校准到期日与保管班组；
 * 是否“超期未校准 / 校准结论不合格”由最近一次 {@link GaugeCalibration} 校准记录实时派生，
 * 不把派生状态落库，刷新后口径一致。
 */
@Entity
@Table(name = "gauge_tool")
public class GaugeTool {

    /** 卡尺 */
    public static final String TYPE_CALIPER = "CALIPER";
    /** 塞尺 */
    public static final String TYPE_FEELER = "FEELER";
    /** 百分表 */
    public static final String TYPE_DIAL_INDICATOR = "DIAL_INDICATOR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 工装编号（业务唯一，如 KC-001） */
    @Column(name = "tool_code", nullable = false, unique = true, length = 50)
    private String toolCode;

    /** 工装类型：CALIPER 卡尺 / FEELER 塞尺 / DIAL_INDICATOR 百分表 */
    @Column(name = "tool_type", nullable = false, length = 30)
    private String toolType;

    /** 规格型号（可选，如 0-150mm） */
    @Column(name = "spec_model", length = 100)
    private String specModel;

    /** 校准到期日：建账登记，每次合格校准时由校准记录的下次应校日同步刷新 */
    @Column(name = "calibration_due_date", nullable = false)
    private LocalDate calibrationDueDate;

    /** 保管班组 */
    @Column(name = "keeper_team", nullable = false, length = 100)
    private String keeperTeam;

    /** 备注 */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 停用标记：停用工装不再参与点检打卡拦截，但台账保留 */
    @Column(name = "disabled", nullable = false)
    private Integer disabled = 0;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    public GaugeTool() {
    }

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

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
