package com.bufferblock.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 借用预约登记入参。班组从空闲挡块中选择挡块，约定取用时段与归还点。
 */
public class BorrowReservationCreateDTO {

    private Long blockId;
    /** 借用班组 */
    private String teamName;
    /** 约定取用时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime pickupTime;
    /** 计划归还时间（取用时段终点，可空） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime plannedReturnTime;
    /** 归还点（必填） */
    private String returnPoint;
    private String contactPerson;
    private String contactPhone;
    /** 借用用途 */
    private String purpose;
    private String remark;
    /** 预约登记人 */
    private String operator;

    public BorrowReservationCreateDTO() {
    }

    public Long getBlockId() { return blockId; }
    public void setBlockId(Long blockId) { this.blockId = blockId; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public LocalDateTime getPickupTime() { return pickupTime; }
    public void setPickupTime(LocalDateTime pickupTime) { this.pickupTime = pickupTime; }

    public LocalDateTime getPlannedReturnTime() { return plannedReturnTime; }
    public void setPlannedReturnTime(LocalDateTime plannedReturnTime) { this.plannedReturnTime = plannedReturnTime; }

    public String getReturnPoint() { return returnPoint; }
    public void setReturnPoint(String returnPoint) { this.returnPoint = returnPoint; }

    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
