package com.bufferblock.dto;

import java.time.LocalDate;

/**
 * 校准员录入校准结果请求。
 * result=PASS 合格后解除挂起；result=FAIL 不合格必须挂起待修。
 */
public class CalibrationCreateDTO {

    private Long blockId;
    private LocalDate calibrationDate;
    /** PASS-合格 / FAIL-不合格 */
    private String result;
    /** 本次校准有效期至 */
    private LocalDate validUntil;
    /** 下次应校日期 */
    private LocalDate nextDueDate;
    /** 本次所用校准周期（月），可选；缺省取挡块档案上配置的校准周期 */
    private Integer cycleMonths;
    private String calibrator;
    private String note;

    public CalibrationCreateDTO() {
    }

    public Long getBlockId() { return blockId; }
    public void setBlockId(Long blockId) { this.blockId = blockId; }

    public LocalDate getCalibrationDate() { return calibrationDate; }
    public void setCalibrationDate(LocalDate calibrationDate) { this.calibrationDate = calibrationDate; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }

    public LocalDate getNextDueDate() { return nextDueDate; }
    public void setNextDueDate(LocalDate nextDueDate) { this.nextDueDate = nextDueDate; }

    public Integer getCycleMonths() { return cycleMonths; }
    public void setCycleMonths(Integer cycleMonths) { this.cycleMonths = cycleMonths; }

    public String getCalibrator() { return calibrator; }
    public void setCalibrator(String calibrator) { this.calibrator = calibrator; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
