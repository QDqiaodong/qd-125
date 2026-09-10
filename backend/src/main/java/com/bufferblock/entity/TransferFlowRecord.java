package com.bufferblock.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "transfer_flow_record")
public class TransferFlowRecord {

    /** 登记移交 */
    public static final String ACTION_REGISTER = "REGISTER";
    /** 确认接收 */
    public static final String ACTION_CONFIRM = "CONFIRM";
    /** 驳回 */
    public static final String ACTION_REJECT = "REJECT";
    /** 打印回执 */
    public static final String ACTION_PRINT_RECEIPT = "PRINT_RECEIPT";
    /** 打印移交单 */
    public static final String ACTION_PRINT_ORDER = "PRINT_ORDER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_id", nullable = false)
    private Long transferId;

    @Column(name = "action", nullable = false, length = 30)
    private String action;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", length = 20)
    private String toStatus;

    @Column(name = "operator", length = 50)
    private String operator;

    @Column(name = "note", length = 500)
    private String note;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    public TransferFlowRecord() {
    }

    public TransferFlowRecord(Long transferId, String action, String fromStatus, String toStatus,
                              String operator, String note) {
        this.transferId = transferId;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.operator = operator;
        this.note = note;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTransferId() { return transferId; }
    public void setTransferId(Long transferId) { this.transferId = transferId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }

    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
