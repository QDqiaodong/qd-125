package com.bufferblock.dto;

import java.time.LocalDate;

/**
 * 挡块校准状态视图（派生，不入库）：
 * NORMAL-正常 / OVERDUE-校准逾期 / SUSPENDED-挂起待修 / UNCALIBRATED-未校准。
 */
public class CalibrationStatusVO {

    public static final String NORMAL = "NORMAL";
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

    public String getSuspendReason() { return suspendReason; }
    public void setSuspendReason(String suspendReason) { this.suspendReason = suspendReason; }

    public String getLastCalibrator() { return lastCalibrator; }
    public void setLastCalibrator(String lastCalibrator) { this.lastCalibrator = lastCalibrator; }
}
