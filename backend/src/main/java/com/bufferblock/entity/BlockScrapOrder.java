package com.bufferblock.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 缓冲挡块报废出库单（一单一挡块）：
 * 使用人对“在用”挡块提交报废原因进入待确认，此时不改变档案状态与产线绑定；
 * 仓管逐条确认后挡块退出在用（档案状态置 SCRAPPED）并解开当前产线绑定，
 * 确认前使用人可撤回。单据状态、申请人/确认人/撤回人与时间全部落库，
 * 关闭页面再打开待确认条数、清单与档案状态保持一致；已报废挡块不能再预约或移交。
 */
@Entity
@Table(name = "block_scrap_order")
public class BlockScrapOrder {

    /** 待仓管确认（使用人已提交，确认前可撤回） */
    public static final String STATUS_PENDING = "PENDING";
    /** 已报废出库：仓管已确认，挡块退出在用并解开产线绑定，终态不可逆 */
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    /** 已撤回：使用人在仓管确认前撤回，挡块恢复完全可用，可再次申请 */
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scrap_no", nullable = false, unique = true, length = 50)
    private String scrapNo;

    @Column(name = "block_id", nullable = false)
    private Long blockId;

    /** 申请时快照的当前产线ID（确认解绑时校验绑定未漂移） */
    @Column(name = "line_id")
    private Long lineId;

    @Column(name = "scrap_date", nullable = false)
    private LocalDate scrapDate;

    /** 使用人填写的报废原因（必填） */
    @Column(name = "scrap_reason", nullable = false, length = 500)
    private String scrapReason;

    /** 申请人（使用人） */
    @Column(name = "apply_operator", nullable = false, length = 50)
    private String applyOperator;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING;

    /** 仓管确认人 */
    @Column(name = "confirm_operator", length = 50)
    private String confirmOperator;

    @Column(name = "confirm_time")
    private LocalDateTime confirmTime;

    /** 仓管确认备注（选填） */
    @Column(name = "confirm_note", length = 500)
    private String confirmNote;

    /** 撤回操作人（使用人） */
    @Column(name = "withdraw_operator", length = 50)
    private String withdrawOperator;

    @Column(name = "withdraw_time")
    private LocalDateTime withdrawTime;

    /** 撤回原因（选填） */
    @Column(name = "withdraw_reason", length = 500)
    private String withdrawReason;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    // ---- 读取时实时派生（不落库） ----

    @Transient
    private String blockCode;

    @Transient
    private String adapterModel;

    @Transient
    private BigDecimal thickness;

    @Transient
    private String specTemplate;

    @Transient
    private String lineName;

    /** 挡块档案当前状态：IN_SERVICE/SUSPENDED/SCRAPPED */
    @Transient
    private String blockServiceStatus;

    /** 待确认单从提交时刻到当前时刻的实时等待时长；终态定格在办理时刻 */
    @Transient
    private String waitingDuration;

    public BlockScrapOrder() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getScrapNo() { return scrapNo; }
    public void setScrapNo(String scrapNo) { this.scrapNo = scrapNo; }

    public Long getBlockId() { return blockId; }
    public void setBlockId(Long blockId) { this.blockId = blockId; }

    public Long getLineId() { return lineId; }
    public void setLineId(Long lineId) { this.lineId = lineId; }

    public LocalDate getScrapDate() { return scrapDate; }
    public void setScrapDate(LocalDate scrapDate) { this.scrapDate = scrapDate; }

    public String getScrapReason() { return scrapReason; }
    public void setScrapReason(String scrapReason) { this.scrapReason = scrapReason; }

    public String getApplyOperator() { return applyOperator; }
    public void setApplyOperator(String applyOperator) { this.applyOperator = applyOperator; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getConfirmOperator() { return confirmOperator; }
    public void setConfirmOperator(String confirmOperator) { this.confirmOperator = confirmOperator; }

    public LocalDateTime getConfirmTime() { return confirmTime; }
    public void setConfirmTime(LocalDateTime confirmTime) { this.confirmTime = confirmTime; }

    public String getConfirmNote() { return confirmNote; }
    public void setConfirmNote(String confirmNote) { this.confirmNote = confirmNote; }

    public String getWithdrawOperator() { return withdrawOperator; }
    public void setWithdrawOperator(String withdrawOperator) { this.withdrawOperator = withdrawOperator; }

    public LocalDateTime getWithdrawTime() { return withdrawTime; }
    public void setWithdrawTime(LocalDateTime withdrawTime) { this.withdrawTime = withdrawTime; }

    public String getWithdrawReason() { return withdrawReason; }
    public void setWithdrawReason(String withdrawReason) { this.withdrawReason = withdrawReason; }

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

    public String getLineName() { return lineName; }
    public void setLineName(String lineName) { this.lineName = lineName; }

    public String getBlockServiceStatus() { return blockServiceStatus; }
    public void setBlockServiceStatus(String blockServiceStatus) { this.blockServiceStatus = blockServiceStatus; }

    public String getWaitingDuration() { return waitingDuration; }
    public void setWaitingDuration(String waitingDuration) { this.waitingDuration = waitingDuration; }
}
