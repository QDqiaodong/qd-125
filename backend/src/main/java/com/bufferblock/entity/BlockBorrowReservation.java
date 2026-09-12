package com.bufferblock.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 缓冲挡块借用预约。借用独立于移交划转：不改产线归属，
 * 仅在占用期间（已预约/已取走）于挡块档案上叠加占用标记。
 */
@Entity
@Table(name = "block_borrow_reservation")
public class BlockBorrowReservation {

    /** 已预约：等待班组按约定时间取用 */
    public static final String STATUS_RESERVED = "RESERVED";
    /** 已取走：班组已登记取走，实物占用中 */
    public static final String STATUS_PICKED_UP = "PICKED_UP";
    /** 已归还：实物归还到约定归还点，占用结束 */
    public static final String STATUS_RETURNED = "RETURNED";
    /** 已取消：预约作废（必须填写取消原因），占用解除 */
    public static final String STATUS_CANCELLED = "CANCELLED";
    /** 逾时未取：过了约定取用时间仍未取走（已提醒，仍可正常取走/取消） */
    public static final String STATUS_OVERDUE = "OVERDUE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_no", nullable = false, unique = true, length = 50)
    private String reservationNo;

    @Column(name = "block_id", nullable = false)
    private Long blockId;

    @Column(name = "team_name", nullable = false, length = 100)
    private String teamName;

    @Column(name = "pickup_time", nullable = false)
    private LocalDateTime pickupTime;

    @Column(name = "planned_return_time")
    private LocalDateTime plannedReturnTime;

    @Column(name = "return_point", nullable = false, length = 200)
    private String returnPoint;

    @Column(name = "contact_person", length = 50)
    private String contactPerson;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "purpose", length = 500)
    private String purpose;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_RESERVED;

    @Column(name = "pickup_operator", length = 50)
    private String pickupOperator;

    @Column(name = "actual_pickup_time")
    private LocalDateTime actualPickupTime;

    @Column(name = "return_operator", length = 50)
    private String returnOperator;

    @Column(name = "actual_return_time")
    private LocalDateTime actualReturnTime;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancel_operator", length = 50)
    private String cancelOperator;

    @Column(name = "cancel_time")
    private LocalDateTime cancelTime;

    @Column(name = "remind_count", nullable = false)
    private Integer remindCount = 0;

    @Column(name = "last_remind_time")
    private LocalDateTime lastRemindTime;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    // ---- 档案/展示用派生字段（不落库） ----

    @Transient
    private String blockCode;

    @Transient
    private String adapterModel;

    @Transient
    private BigDecimal thickness;

    @Transient
    private String specTemplate;

    @Transient
    private String currentLineName;

    @Transient
    private String serviceStatus;

    /** 逾时未取的超时时长（口径见 BorrowReservationService.OVERDUE_GRACE_MINUTES） */
    @Transient
    private String overdueDuration;

    public BlockBorrowReservation() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getReservationNo() { return reservationNo; }
    public void setReservationNo(String reservationNo) { this.reservationNo = reservationNo; }

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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPickupOperator() { return pickupOperator; }
    public void setPickupOperator(String pickupOperator) { this.pickupOperator = pickupOperator; }

    public LocalDateTime getActualPickupTime() { return actualPickupTime; }
    public void setActualPickupTime(LocalDateTime actualPickupTime) { this.actualPickupTime = actualPickupTime; }

    public String getReturnOperator() { return returnOperator; }
    public void setReturnOperator(String returnOperator) { this.returnOperator = returnOperator; }

    public LocalDateTime getActualReturnTime() { return actualReturnTime; }
    public void setActualReturnTime(LocalDateTime actualReturnTime) { this.actualReturnTime = actualReturnTime; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    public String getCancelOperator() { return cancelOperator; }
    public void setCancelOperator(String cancelOperator) { this.cancelOperator = cancelOperator; }

    public LocalDateTime getCancelTime() { return cancelTime; }
    public void setCancelTime(LocalDateTime cancelTime) { this.cancelTime = cancelTime; }

    public Integer getRemindCount() { return remindCount; }
    public void setRemindCount(Integer remindCount) { this.remindCount = remindCount; }

    public LocalDateTime getLastRemindTime() { return lastRemindTime; }
    public void setLastRemindTime(LocalDateTime lastRemindTime) { this.lastRemindTime = lastRemindTime; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public String getBlockCode() { return blockCode; }
    public void setBlockCode(String blockCode) { this.blockCode = blockCode; }

    public String getAdapterModel() { return adapterModel; }
    public void setAdapterModel(String adapterModel) { this.adapterModel = adapterModel; }

    public BigDecimal getThickness() { return thickness; }
    public void setThickness(BigDecimal thickness) { this.thickness = thickness; }

    public String getSpecTemplate() { return specTemplate; }
    public void setSpecTemplate(String specTemplate) { this.specTemplate = specTemplate; }

    public String getCurrentLineName() { return currentLineName; }
    public void setCurrentLineName(String currentLineName) { this.currentLineName = currentLineName; }

    public String getServiceStatus() { return serviceStatus; }
    public void setServiceStatus(String serviceStatus) { this.serviceStatus = serviceStatus; }

    public String getOverdueDuration() { return overdueDuration; }
    public void setOverdueDuration(String overdueDuration) { this.overdueDuration = overdueDuration; }
}
