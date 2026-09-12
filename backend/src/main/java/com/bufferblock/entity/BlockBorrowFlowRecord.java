package com.bufferblock.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "block_borrow_flow_record")
public class BlockBorrowFlowRecord {

    /** 预约登记 */
    public static final String ACTION_BOOK = "BOOK";
    /** 班组取走 */
    public static final String ACTION_PICKUP = "PICKUP";
    /** 归还 */
    public static final String ACTION_RETURN = "RETURN";
    /** 取消（必须带原因） */
    public static final String ACTION_CANCEL = "CANCEL";
    /** 逾时未取提醒 */
    public static final String ACTION_OVERDUE_REMIND = "OVERDUE_REMIND";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

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

    public BlockBorrowFlowRecord() {
    }

    public BlockBorrowFlowRecord(Long reservationId, String action, String fromStatus, String toStatus,
                                 String operator, String note) {
        this.reservationId = reservationId;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.operator = operator;
        this.note = note;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }

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
