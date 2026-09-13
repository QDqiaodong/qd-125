package com.bufferblock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class BufferBlockDTO {
    private Long id;
    private String blockCode;
    private String adapterModel;
    private BigDecimal thickness;
    private String imageUrl;
    private String specTemplate;
    private Long lineId;
    private String lineName;

    /** 校准周期（月） */
    private Integer calibrationCycleMonths;
    /** 在用状态：IN_SERVICE-在用, SUSPENDED-挂起待修 */
    private String serviceStatus;
    private String suspendReason;
    private String suspendTime;

    /** 最近一次校准结论：PASS-合格 / FAIL-不合格（未校准为 null） */
    private String lastCalibrationResult;
    private LocalDate lastCalibrationDate;
    private String lastCalibrator;
    /** 本次校准有效期至 */
    private LocalDate validUntil;
    /** 下次应校日期 */
    private LocalDate nextDueDate;
    /** 派生校准状态：NORMAL-正常 / DUE_SOON-临期 / OVERDUE-逾期 / SUSPENDED-挂起待修 / UNCALIBRATED-未校准 */
    private String calibrationStatus;
    /** 逾期天数（未逾期为 null） */
    private Integer overdueDays;
    /** 距下次应校日期剩余天数（仅临期状态有值） */
    private Integer daysUntilDue;

    /** 是否存在待确认移交单 */
    private Boolean pendingTransfer;
    /** 待确认移交单号 */
    private String pendingTransferNo;

    /** 是否处于借用占用中（已预约/已取走/逾时未取），档案上显示“已约出” */
    private Boolean borrowedOut;
    /** 占用中的借用预约单号 */
    private String borrowReservationNo;
    /** 借用预约状态：RESERVED/PICKED_UP/OVERDUE */
    private String borrowStatus;
    /** 借用班组 */
    private String borrowTeamName;
    /** 约定取用时间 */
    private LocalDateTime borrowPickupTime;
    /** 约定归还点 */
    private String borrowReturnPoint;
    /** 计划归还时间 */
    private LocalDateTime borrowPlannedReturnTime;
    /** 已取走但超过计划还期仍未归还（与预约台“超期未还”同一派生口径） */
    private Boolean borrowOverdueReturn;
    /** 超期未还时长（派生展示） */
    private String borrowOverdueReturnDuration;

    /** 最近一次班次点检时刻（从未点检为 null），从 block_inspection 实时派生 */
    private LocalDateTime lastInspectionTime;
    /** 最近一次点检班次：MORNING-早班 / AFTERNOON-中班 / NIGHT-晚班 */
    private String lastInspectionShift;
    /** 最近一次点检人 */
    private String lastInspector;
    /** 最近一次点检结论：USABLE-可用 / UNUSABLE-不可用 */
    private String lastInspectionResult;
    /** 最近一次点检备注 */
    private String lastInspectionNote;

    public BufferBlockDTO() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBlockCode() { return blockCode; }
    public void setBlockCode(String blockCode) { this.blockCode = blockCode; }

    public String getAdapterModel() { return adapterModel; }
    public void setAdapterModel(String adapterModel) { this.adapterModel = adapterModel; }

    public BigDecimal getThickness() { return thickness; }
    public void setThickness(BigDecimal thickness) { this.thickness = thickness; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getSpecTemplate() { return specTemplate; }
    public void setSpecTemplate(String specTemplate) { this.specTemplate = specTemplate; }

    public Long getLineId() { return lineId; }
    public void setLineId(Long lineId) { this.lineId = lineId; }

    public String getLineName() { return lineName; }
    public void setLineName(String lineName) { this.lineName = lineName; }

    public Integer getCalibrationCycleMonths() { return calibrationCycleMonths; }
    public void setCalibrationCycleMonths(Integer calibrationCycleMonths) { this.calibrationCycleMonths = calibrationCycleMonths; }

    public String getServiceStatus() { return serviceStatus; }
    public void setServiceStatus(String serviceStatus) { this.serviceStatus = serviceStatus; }

    public String getSuspendReason() { return suspendReason; }
    public void setSuspendReason(String suspendReason) { this.suspendReason = suspendReason; }

    public String getSuspendTime() { return suspendTime; }
    public void setSuspendTime(String suspendTime) { this.suspendTime = suspendTime; }

    public String getLastCalibrationResult() { return lastCalibrationResult; }
    public void setLastCalibrationResult(String lastCalibrationResult) { this.lastCalibrationResult = lastCalibrationResult; }

    public LocalDate getLastCalibrationDate() { return lastCalibrationDate; }
    public void setLastCalibrationDate(LocalDate lastCalibrationDate) { this.lastCalibrationDate = lastCalibrationDate; }

    public String getLastCalibrator() { return lastCalibrator; }
    public void setLastCalibrator(String lastCalibrator) { this.lastCalibrator = lastCalibrator; }

    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }

    public LocalDate getNextDueDate() { return nextDueDate; }
    public void setNextDueDate(LocalDate nextDueDate) { this.nextDueDate = nextDueDate; }

    public String getCalibrationStatus() { return calibrationStatus; }
    public void setCalibrationStatus(String calibrationStatus) { this.calibrationStatus = calibrationStatus; }

    public Integer getOverdueDays() { return overdueDays; }
    public void setOverdueDays(Integer overdueDays) { this.overdueDays = overdueDays; }

    public Integer getDaysUntilDue() { return daysUntilDue; }
    public void setDaysUntilDue(Integer daysUntilDue) { this.daysUntilDue = daysUntilDue; }

    public Boolean getPendingTransfer() { return pendingTransfer; }
    public void setPendingTransfer(Boolean pendingTransfer) { this.pendingTransfer = pendingTransfer; }

    public String getPendingTransferNo() { return pendingTransferNo; }
    public void setPendingTransferNo(String pendingTransferNo) { this.pendingTransferNo = pendingTransferNo; }

    public Boolean getBorrowedOut() { return borrowedOut; }
    public void setBorrowedOut(Boolean borrowedOut) { this.borrowedOut = borrowedOut; }

    public String getBorrowReservationNo() { return borrowReservationNo; }
    public void setBorrowReservationNo(String borrowReservationNo) { this.borrowReservationNo = borrowReservationNo; }

    public String getBorrowStatus() { return borrowStatus; }
    public void setBorrowStatus(String borrowStatus) { this.borrowStatus = borrowStatus; }

    public String getBorrowTeamName() { return borrowTeamName; }
    public void setBorrowTeamName(String borrowTeamName) { this.borrowTeamName = borrowTeamName; }

    public LocalDateTime getBorrowPickupTime() { return borrowPickupTime; }
    public void setBorrowPickupTime(LocalDateTime borrowPickupTime) { this.borrowPickupTime = borrowPickupTime; }

    public String getBorrowReturnPoint() { return borrowReturnPoint; }
    public void setBorrowReturnPoint(String borrowReturnPoint) { this.borrowReturnPoint = borrowReturnPoint; }

    public LocalDateTime getBorrowPlannedReturnTime() { return borrowPlannedReturnTime; }
    public void setBorrowPlannedReturnTime(LocalDateTime borrowPlannedReturnTime) { this.borrowPlannedReturnTime = borrowPlannedReturnTime; }

    public Boolean getBorrowOverdueReturn() { return borrowOverdueReturn; }
    public void setBorrowOverdueReturn(Boolean borrowOverdueReturn) { this.borrowOverdueReturn = borrowOverdueReturn; }

    public String getBorrowOverdueReturnDuration() { return borrowOverdueReturnDuration; }
    public void setBorrowOverdueReturnDuration(String borrowOverdueReturnDuration) { this.borrowOverdueReturnDuration = borrowOverdueReturnDuration; }

    public LocalDateTime getLastInspectionTime() { return lastInspectionTime; }
    public void setLastInspectionTime(LocalDateTime lastInspectionTime) { this.lastInspectionTime = lastInspectionTime; }

    public String getLastInspectionShift() { return lastInspectionShift; }
    public void setLastInspectionShift(String lastInspectionShift) { this.lastInspectionShift = lastInspectionShift; }

    public String getLastInspector() { return lastInspector; }
    public void setLastInspector(String lastInspector) { this.lastInspector = lastInspector; }

    public String getLastInspectionResult() { return lastInspectionResult; }
    public void setLastInspectionResult(String lastInspectionResult) { this.lastInspectionResult = lastInspectionResult; }

    public String getLastInspectionNote() { return lastInspectionNote; }
    public void setLastInspectionNote(String lastInspectionNote) { this.lastInspectionNote = lastInspectionNote; }
}
