package com.bufferblock.dto;

import java.time.LocalDate;

/**
 * 挡块校准状态视图（派生，不入库）：
 * NORMAL-正常 / DUE_SOON-临期 / OVERDUE-校准逾期 / SUSPENDED-挂起待修 / UNCALIBRATED-未校准。
 * 挂起待修优先于一切到期派生，不合格挂起的挡块不会进入临期待办。
 */
public class CalibrationStatusVO {

    public static final String NORMAL = "NORMAL";
    public static final String DUE_SOON = "DUE_SOON";
    public static final String OVERDUE = "OVERDUE";
    public static final String UNCALIBRATED = "UNCALIBRATED";
    public static final String SUSPENDED = "SUSPENDED";

    private Long blockId;
    private String status;
    private String lastResult;
    private LocalDate lastCalibrationDate;
    private LocalDate validUntil;
    private LocalDate nextDueDate;
    private Integer overdueDays;
    /** 距下次应校日期剩余天数（仅临期状态有值） */
    private Integer daysUntilDue;
    private String suspendReason;
    private String lastCalibrator;

    public CalibrationStatusVO() {
    }

    public Long getBlockId() { return blockId; }
    public void setBlockId(Long blockId) { this.blockId = blockId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getLastResult() { return lastResult; }
    public void setLastResult(String lastResult) { this.lastResult = lastResult; }

    public LocalDate getLastCalibrationDate() { return lastCalibrationDate; }
    public void setLastCalibrationDate(LocalDate lastCalibrationDate) { this.lastCalibrationDate = lastCalibrationDate; }

    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }

    public LocalDate getNextDueDate() { return nextDueDate; }
    public void setNextDueDate(LocalDate nextDueDate) { this.nextDueDate = nextDueDate; }

    public Integer getOverdueDays() { return overdueDays; }
    public void setOverdueDays(Integer overdueDays) { this.overdueDays = overdueDays; }

    public Integer getDaysUntilDue() { return daysUntilDue; }
    public void setDaysUntilDue(Integer daysUntilDue) { this.daysUntilDue = daysUntilDue; }

    public String getSuspendReason() { return suspendReason; }
    public void setSuspendReason(String suspendReason) { this.suspendReason = suspendReason; }

    public String getLastCalibrator() { return lastCalibrator; }
    public void setLastCalibrator(String lastCalibrator) { this.lastCalibrator = lastCalibrator; }
}
