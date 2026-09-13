package com.bufferblock.dto;

import java.time.LocalDate;

/**
 * 点检工装校准登记入参：校准日期、结论（合格/不合格）、有效期至、下次应校日期、校准人。
 * 合格校准时台账校准到期日同步为 nextDueDate；不合格不刷新到期日，工装进入不合格拦截清单。
 */
public class GaugeCalibrationCreateDTO {

    private Long toolId;

    private LocalDate calibrationDate;

    /** PASS 合格 / FAIL 不合格 */
    private String result;

    private LocalDate validUntil;

    private LocalDate nextDueDate;

    private String calibrator;

    private String note;

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
}
