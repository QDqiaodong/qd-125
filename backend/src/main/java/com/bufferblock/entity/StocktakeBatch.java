package com.bufferblock.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 挡块盘点批次：管理员按产线 + 盘点日期创建，
 * 创建时快照该产线当前绑定的挡块作为应盘清单（stocktake_item is_extra=0）。
 */
@Entity
@Table(name = "stocktake_batch")
public class StocktakeBatch {

    /** 盘点中：可继续逐项录入实物、处理差异 */
    public static final String STATUS_COUNTING = "COUNTING";
    /** 已完成：差异全部闭环，批次封账，需要时可重新打开补盘 */
    public static final String STATUS_COMPLETED = "COMPLETED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_no", nullable = false, unique = true, length = 50)
    private String batchNo;

    @Column(name = "line_id", nullable = false)
    private Long lineId;

    @Column(name = "stocktake_date", nullable = false)
    private LocalDate stocktakeDate;

    @Column(name = "operator", nullable = false, length = 50)
    private String operator;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_COUNTING;

    /** 应盘数量（快照内挡块数，不含盘盈） */
    @Column(name = "total_count", nullable = false)
    private Integer totalCount = 0;

    /** 已盘数量（实际录入次数，按挡块去重） */
    @Column(name = "counted_count", nullable = false)
    private Integer countedCount = 0;

    /** 差异总数：缺失/错线/重复/盘盈/损坏报废 */
    @Column(name = "discrepancy_count", nullable = false)
    private Integer discrepancyCount = 0;

    /** 待处理差异数（差异状态为 PENDING） */
    @Column(name = "pending_count", nullable = false)
    private Integer pendingCount = 0;

    @Column(name = "finish_time")
    private LocalDateTime finishTime;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Transient
    private String lineCode;

    @Transient
    private String lineName;

    public StocktakeBatch() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }

    public Long getLineId() { return lineId; }
    public void setLineId(Long lineId) { this.lineId = lineId; }

    public LocalDate getStocktakeDate() { return stocktakeDate; }
    public void setStocktakeDate(LocalDate stocktakeDate) { this.stocktakeDate = stocktakeDate; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }

    public Integer getCountedCount() { return countedCount; }
    public void setCountedCount(Integer countedCount) { this.countedCount = countedCount; }

    public Integer getDiscrepancyCount() { return discrepancyCount; }
    public void setDiscrepancyCount(Integer discrepancyCount) { this.discrepancyCount = discrepancyCount; }

    public Integer getPendingCount() { return pendingCount; }
    public void setPendingCount(Integer pendingCount) { this.pendingCount = pendingCount; }

    public LocalDateTime getFinishTime() { return finishTime; }
    public void setFinishTime(LocalDateTime finishTime) { this.finishTime = finishTime; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public String getLineCode() { return lineCode; }
    public void setLineCode(String lineCode) { this.lineCode = lineCode; }

    public String getLineName() { return lineName; }
    public void setLineName(String lineName) { this.lineName = lineName; }
}
