package com.bufferblock.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 班组交班主表：交班时一次性快照登记当班未还预约、待确认移交、待处理盘点差异，
 * 接班人须逐条确认，全部确认后交班才完成；交班未完成期间禁止新开借用预约。
 * 全部状态落库，关闭页面再打开未确认条数与清单内容保持一致。
 */
@Entity
@Table(name = "shift_handover")
public class ShiftHandover {

    /** 交班中：已登记事项清单，等待接班人逐条确认 */
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 已完成：全部事项均已由接班人确认 */
    public static final String STATUS_COMPLETED = "COMPLETED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handover_no", nullable = false, unique = true, length = 50)
    private String handoverNo;

    @Column(name = "from_team", nullable = false, length = 100)
    private String fromTeam;

    @Column(name = "to_team", nullable = false, length = 100)
    private String toTeam;

    @Column(name = "handover_operator", nullable = false, length = 50)
    private String handoverOperator;

    @Column(name = "receive_operator", nullable = false, length = 50)
    private String receiveOperator;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_IN_PROGRESS;

    /** 交班登记时快照的事项总条数 */
    @Column(name = "total_count", nullable = false)
    private Integer totalCount = 0;

    /** 接班人已确认条数（逐条确认时回写，与明细落库同事务） */
    @Column(name = "confirmed_count", nullable = false)
    private Integer confirmedCount = 0;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "finish_time")
    private LocalDateTime finishTime;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /** 未确认条数（派生，不落库） */
    @Transient
    private Integer unconfirmedCount;

    public ShiftHandover() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getHandoverNo() { return handoverNo; }
    public void setHandoverNo(String handoverNo) { this.handoverNo = handoverNo; }

    public String getFromTeam() { return fromTeam; }
    public void setFromTeam(String fromTeam) { this.fromTeam = fromTeam; }

    public String getToTeam() { return toTeam; }
    public void setToTeam(String toTeam) { this.toTeam = toTeam; }

    public String getHandoverOperator() { return handoverOperator; }
    public void setHandoverOperator(String handoverOperator) { this.handoverOperator = handoverOperator; }

    public String getReceiveOperator() { return receiveOperator; }
    public void setReceiveOperator(String receiveOperator) { this.receiveOperator = receiveOperator; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }

    public Integer getConfirmedCount() { return confirmedCount; }
    public void setConfirmedCount(Integer confirmedCount) { this.confirmedCount = confirmedCount; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getFinishTime() { return finishTime; }
    public void setFinishTime(LocalDateTime finishTime) { this.finishTime = finishTime; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Integer getUnconfirmedCount() { return unconfirmedCount; }
    public void setUnconfirmedCount(Integer unconfirmedCount) { this.unconfirmedCount = unconfirmedCount; }
}
