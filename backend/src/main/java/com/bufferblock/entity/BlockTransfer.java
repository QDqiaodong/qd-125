package com.bufferblock.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "block_transfer")
public class BlockTransfer {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_no", nullable = false, unique = true, length = 50)
    private String transferNo;

    @Column(name = "block_id", nullable = false)
    private Long blockId;

    @Column(name = "from_line_id", nullable = false)
    private Long fromLineId;

    @Column(name = "to_line_id", nullable = false)
    private Long toLineId;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

    @Column(name = "transfer_reason", length = 500)
    private String transferReason;

    @Column(name = "transfer_operator", nullable = false, length = 50)
    private String transferOperator;

    @Column(name = "receive_operator", length = 50)
    private String receiveOperator;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING;

    @Column(name = "handle_note", length = 500)
    private String handleNote;

    @Column(name = "handle_time")
    private LocalDateTime handleTime;

    @Column(name = "print_count")
    private Integer printCount = 0;

    @Column(name = "last_print_time")
    private LocalDateTime lastPrintTime;

    @Column(name = "receipt_print_count")
    private Integer receiptPrintCount = 0;

    @Column(name = "last_receipt_print_time")
    private LocalDateTime lastReceiptPrintTime;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Transient
    private String blockCode;

    @Transient
    private String adapterModel;

    @Transient
    private BigDecimal thickness;

    @Transient
    private String specTemplate;

    @Transient
    private String fromLineName;

    @Transient
    private String toLineName;

    @Transient
    private String waitingDuration;

    public BlockTransfer() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTransferNo() { return transferNo; }
    public void setTransferNo(String transferNo) { this.transferNo = transferNo; }

    public Long getBlockId() { return blockId; }
    public void setBlockId(Long blockId) { this.blockId = blockId; }

    public Long getFromLineId() { return fromLineId; }
    public void setFromLineId(Long fromLineId) { this.fromLineId = fromLineId; }

    public Long getToLineId() { return toLineId; }
    public void setToLineId(Long toLineId) { this.toLineId = toLineId; }

    public LocalDate getTransferDate() { return transferDate; }
    public void setTransferDate(LocalDate transferDate) { this.transferDate = transferDate; }

    public String getTransferReason() { return transferReason; }
    public void setTransferReason(String transferReason) { this.transferReason = transferReason; }

    public String getTransferOperator() { return transferOperator; }
    public void setTransferOperator(String transferOperator) { this.transferOperator = transferOperator; }

    public String getReceiveOperator() { return receiveOperator; }
    public void setReceiveOperator(String receiveOperator) { this.receiveOperator = receiveOperator; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getHandleNote() { return handleNote; }
    public void setHandleNote(String handleNote) { this.handleNote = handleNote; }

    public LocalDateTime getHandleTime() { return handleTime; }
    public void setHandleTime(LocalDateTime handleTime) { this.handleTime = handleTime; }

    public Integer getPrintCount() { return printCount; }
    public void setPrintCount(Integer printCount) { this.printCount = printCount; }

    public LocalDateTime getLastPrintTime() { return lastPrintTime; }
    public void setLastPrintTime(LocalDateTime lastPrintTime) { this.lastPrintTime = lastPrintTime; }

    public Integer getReceiptPrintCount() { return receiptPrintCount; }
    public void setReceiptPrintCount(Integer receiptPrintCount) { this.receiptPrintCount = receiptPrintCount; }

    public LocalDateTime getLastReceiptPrintTime() { return lastReceiptPrintTime; }
    public void setLastReceiptPrintTime(LocalDateTime lastReceiptPrintTime) { this.lastReceiptPrintTime = lastReceiptPrintTime; }

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

    public String getFromLineName() { return fromLineName; }
    public void setFromLineName(String fromLineName) { this.fromLineName = fromLineName; }

    public String getToLineName() { return toLineName; }
    public void setToLineName(String toLineName) { this.toLineName = toLineName; }

    public String getWaitingDuration() { return waitingDuration; }
    public void setWaitingDuration(String waitingDuration) { this.waitingDuration = waitingDuration; }
}
