package com.bufferblock.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 班组交班事项：交班登记瞬间对四类未结事项（未还预约/待确认移交/待处理盘点差异/拦截中点检工装）
 * 做的快照，清单内容随后续业务变化保持不变（可审计）；接班人逐条确认，
 * 确认人/确认时间随状态落库，关闭页面再打开清单与未确认条数保持一致。
 * 拦截中工装事项与校准台拦截清单同一派生口径：交班中新拦截的工装自动补入清单，
 * 仍拦截中的工装须校准合格移出拦截后才能由接班人确认。
 */
@Entity
@Table(name = "shift_handover_item")
public class ShiftHandoverItem {

    /** 事项类型：当班未还预约（占用中：已预约/已取走/逾时未取） */
    public static final String TYPE_BORROW_UNRETURNED = "BORROW_UNRETURNED";
    /** 事项类型：待确认移交 */
    public static final String TYPE_TRANSFER_PENDING = "TRANSFER_PENDING";
    /** 事项类型：待处理盘点差异 */
    public static final String TYPE_STOCKTAKE_PENDING = "STOCKTAKE_PENDING";
    /** 事项类型：拦截中点检工装（到期未校准/校准结论不合格，与校准台拦截清单同源） */
    public static final String TYPE_GAUGE_BLOCKED = "GAUGE_BLOCKED";

    /** 待接班确认 */
    public static final String STATUS_PENDING = "PENDING";
    /** 接班人已确认 */
    public static final String STATUS_CONFIRMED = "CONFIRMED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handover_id", nullable = false)
    private Long handoverId;

    @Column(name = "item_type", nullable = false, length = 30)
    private String itemType;

    /** 源单据ID快照：预约单ID / 移交单ID / 盘点明细ID / 工装台账ID */
    @Column(name = "ref_id")
    private Long refId;

    /** 源单号快照：BR-... / TRF-... / PD-... / 工装编号 */
    @Column(name = "ref_no", length = 50)
    private String refNo;

    @Column(name = "block_id")
    private Long blockId;

    @Column(name = "block_code", length = 50)
    private String blockCode;

    /** 事项摘要快照（班组/产线/差异类型等关键信息） */
    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING;

    @Column(name = "confirm_operator", length = 50)
    private String confirmOperator;

    @Column(name = "confirm_time")
    private LocalDateTime confirmTime;

    @Column(name = "confirm_note", length = 500)
    private String confirmNote;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    // ---- 读取时实时派生（不落库）：源单据当前状态，供接班人判断事项是否仍未结 ----

    /** 源单据当前状态文本（如 已取走/待确认/待处理/已归还 等） */
    @Transient
    private String sourceStatusText;

    /** 源单据当前是否仍未结（仍占用/仍待确认/仍待处理） */
    @Transient
    private Boolean sourceStillOpen;

    public ShiftHandoverItem() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getHandoverId() { return handoverId; }
    public void setHandoverId(Long handoverId) { this.handoverId = handoverId; }

    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }

    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }

    public String getRefNo() { return refNo; }
    public void setRefNo(String refNo) { this.refNo = refNo; }

    public Long getBlockId() { return blockId; }
    public void setBlockId(Long blockId) { this.blockId = blockId; }

    public String getBlockCode() { return blockCode; }
    public void setBlockCode(String blockCode) { this.blockCode = blockCode; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getConfirmOperator() { return confirmOperator; }
    public void setConfirmOperator(String confirmOperator) { this.confirmOperator = confirmOperator; }

    public LocalDateTime getConfirmTime() { return confirmTime; }
    public void setConfirmTime(LocalDateTime confirmTime) { this.confirmTime = confirmTime; }

    public String getConfirmNote() { return confirmNote; }
    public void setConfirmNote(String confirmNote) { this.confirmNote = confirmNote; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public String getSourceStatusText() { return sourceStatusText; }
    public void setSourceStatusText(String sourceStatusText) { this.sourceStatusText = sourceStatusText; }

    public Boolean getSourceStillOpen() { return sourceStillOpen; }
    public void setSourceStillOpen(Boolean sourceStillOpen) { this.sourceStillOpen = sourceStillOpen; }
}
