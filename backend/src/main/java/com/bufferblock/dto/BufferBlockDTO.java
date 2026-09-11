package com.bufferblock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    /** 派生校准状态：NORMAL-正常 / OVERDUE-逾期 / SUSPENDED-挂起待修 / UNCALIBRATED-未校准 */
    private String calibrationStatus;
    /** 逾期天数（未逾期为 null） */
    private Integer overdueDays;

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
}
