package com.bufferblock.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 点检工装校准记录。每次校准登记一行：
 * 校准日期、校准结论（PASS 合格 / FAIL 不合格）、有效期至、下次应校日期、校准人与备注。
 * 合格校准时把工装台账 {@code calibration_due_date} 同步为本次下次应校日；
 * “到期未校准 / 结论不合格”的拦截口径全部以最近一次校准记录实时派生。
 */
@Entity
@Table(name = "gauge_calibration")
public class GaugeCalibration {

    public static final String RESULT_PASS = "PASS";
    public static final String RESULT_FAIL = "FAIL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tool_id", nullable = false)
    private Long toolId;

    @Column(name = "calibration_date", nullable = false)
    private LocalDate calibrationDate;

    @Column(name = "result", nullable = false, length = 20)
    private String result;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "next_due_date", nullable = false)
    private LocalDate nextDueDate;

    @Column(name = "calibrator", nullable = false, length = 50)
    private String calibrator;

    @Column(name = "note", length = 500)
    private String note;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    public GaugeCalibration() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getToolId() { return toolId; }
    public void setToolId(Long toolId) { this.toolId = toolId; }

    public LocalDate getCalibrationDate() { return calibrationDate; }
    public void setCalibrationDate(LocalDate calibrationDate) { this.calibrationDate = calibrationDate; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }

    public LocalDate getNextDueDate() { return nextDueDate; }
    public void setNextDueDate(LocalDate nextDueDate) { this.nextDueDate = nextDueDate; }

    public String getCalibrator() { return calibrator; }
    public void setCalibrator(String calibrator) { this.calibrator = calibrator; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
