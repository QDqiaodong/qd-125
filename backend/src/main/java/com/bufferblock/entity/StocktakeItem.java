package com.bufferblock.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 盘点明细：批次创建时按当前产线绑定快照生成应盘记录（is_extra=0）；
 * 逐项录入实物后回写现场状态；账外盘到的挡块生成盘盈记录（is_extra=1）。
 */
@Entity
@Table(name = "stocktake_item")
public class StocktakeItem {

    /** 实物状态：正常 */
    public static final String PHYSICAL_NORMAL = "NORMAL";
    /** 实物状态：损坏 */
    public static final String PHYSICAL_DAMAGED = "DAMAGED";
    /** 实物状态：报废 */
    public static final String PHYSICAL_SCRAPPED = "SCRAPPED";

    /** 差异类型：无差异 */
    public static final String DIFF_NONE = "NONE";
    /** 差异类型：缺失（应盘未盘到） */
    public static final String DIFF_MISSING = "MISSING";
    /** 差异类型：错线（当前绑定产线与现场产线不一致） */
    public static final String DIFF_WRONG_LINE = "WRONG_LINE";
    /** 差异类型：重复盘点 */
    public static final String DIFF_DUPLICATE = "DUPLICATE";
    /** 差异类型：盘盈（账外/未绑定挡块盘到） */
    public static final String DIFF_EXTRA = "EXTRA";
    /** 差异类型：实物损坏 */
    public static final String DIFF_DAMAGED = "DAMAGED";
    /** 差异类型：实物报废 */
    public static final String DIFF_SCRAPPED = "SCRAPPED";

    /** 差异处理状态：待处理 */
    public static final String STATUS_PENDING = "PENDING";
    /** 差异处理状态：已确认（情况属实） */
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    /** 差异处理状态：已忽略（误盘/合理差异） */
    public static final String STATUS_IGNORED = "IGNORED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    /** 盘盈且编号无法归档时允许为空，正常挡块必为挡块档案ID */
    @Column(name = "block_id")
    private Long blockId;

    @Column(name = "block_code", length = 50)
    private String blockCode;

    /** 应盘产线（批次产线）快照 */
    @Column(name = "expected_line_id")
    private Long expectedLineId;

    /** 当前绑定产线（录入/快照时点） */
    @Column(name = "bound_line_id")
    private Long boundLineId;

    /** 现场盘点产线 */
    @Column(name = "site_line_id")
    private Long siteLineId;

    /** 是否盘盈记录：1-是（账外盘到）, 0-否（快照应盘） */
    @Column(name = "is_extra", nullable = false)
    private Integer isExtra = 0;

    /** 是否已盘（已逐项录入实物）：1-是, 0-否 */
    @Column(name = "is_counted", nullable = false)
    private Integer isCounted = 0;

    @Column(name = "physical_status", length = 20)
    private String physicalStatus;

    /** 重复盘点次数（首盘为 1） */
    @Column(name = "repeat_count", nullable = false)
    private Integer repeatCount = 0;

    @Column(name = "count_operator", length = 50)
    private String countOperator;

    @Column(name = "count_time")
    private LocalDateTime countTime;

    @Column(name = "count_remark", length = 500)
    private String countRemark;

    @Column(name = "discrepancy_type", nullable = false, length = 20)
    private String discrepancyType = DIFF_NONE;

    /**
     * 差异处理状态：PENDING-待处理, CONFIRMED-已确认, IGNORED-已忽略。
     * 未盘记录无差异时为 NULL（列表层动态展示为 WAITING-待盘）。
     */
    @Column(name = "discrepancy_status", length = 20)
    private String discrepancyStatus;

    @Column(name = "handle_operator", length = 50)
    private String handleOperator;

    @Column(name = "handle_note", length = 500)
    private String handleNote;

    @Column(name = "handle_time")
    private LocalDateTime handleTime;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    // ---- 以下为列表/详情/导出富化字段（不入库） ----
    @Transient private String adapterModel;
    @Transient private BigDecimal thickness;
    @Transient private String specTemplate;
    @Transient private String expectedLineName;
    @Transient private String boundLineName;
    @Transient private String siteLineName;
    /** 有效差异处理状态（未盘动态补 WAITING），供前端与导出使用 */
    @Transient private String effectiveStatus;
    @Transient private String discrepancyTypeText;
    @Transient private String effectiveStatusText;
    @Transient private String physicalStatusText;
    /** 最近移交单号/状态/日期提示，用于差异追溯 */
    @Transient private String lastTransferNo;
    @Transient private String lastTransferStatus;
    @Transient private String lastTransferDate;
    @Transient private String transferHint;

    public StocktakeItem() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }

    public Long getBlockId() { return blockId; }
    public void setBlockId(Long blockId) { this.blockId = blockId; }

    public String getBlockCode() { return blockCode; }
    public void setBlockCode(String blockCode) { this.blockCode = blockCode; }

    public Long getExpectedLineId() { return expectedLineId; }
    public void setExpectedLineId(Long expectedLineId) { this.expectedLineId = expectedLineId; }

    public Long getBoundLineId() { return boundLineId; }
    public void setBoundLineId(Long boundLineId) { this.boundLineId = boundLineId; }

    public Long getSiteLineId() { return siteLineId; }
    public void setSiteLineId(Long siteLineId) { this.siteLineId = siteLineId; }

    public Integer getIsExtra() { return isExtra; }
    public void setIsExtra(Integer isExtra) { this.isExtra = isExtra; }

    public Integer getIsCounted() { return isCounted; }
    public void setIsCounted(Integer isCounted) { this.isCounted = isCounted; }

    public String getPhysicalStatus() { return physicalStatus; }
    public void setPhysicalStatus(String physicalStatus) { this.physicalStatus = physicalStatus; }

    public Integer getRepeatCount() { return repeatCount; }
    public void setRepeatCount(Integer repeatCount) { this.repeatCount = repeatCount; }

    public String getCountOperator() { return countOperator; }
    public void setCountOperator(String countOperator) { this.countOperator = countOperator; }

    public LocalDateTime getCountTime() { return countTime; }
    public void setCountTime(LocalDateTime countTime) { this.countTime = countTime; }

    public String getCountRemark() { return countRemark; }
    public void setCountRemark(String countRemark) { this.countRemark = countRemark; }

    public String getDiscrepancyType() { return discrepancyType; }
    public void setDiscrepancyType(String discrepancyType) { this.discrepancyType = discrepancyType; }

    public String getDiscrepancyStatus() { return discrepancyStatus; }
    public void setDiscrepancyStatus(String discrepancyStatus) { this.discrepancyStatus = discrepancyStatus; }

    public String getHandleOperator() { return handleOperator; }
    public void setHandleOperator(String handleOperator) { this.handleOperator = handleOperator; }

    public String getHandleNote() { return handleNote; }
    public void setHandleNote(String handleNote) { this.handleNote = handleNote; }

    public LocalDateTime getHandleTime() { return handleTime; }
    public void setHandleTime(LocalDateTime handleTime) { this.handleTime = handleTime; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public String getAdapterModel() { return adapterModel; }
    public void setAdapterModel(String adapterModel) { this.adapterModel = adapterModel; }

    public BigDecimal getThickness() { return thickness; }
    public void setThickness(BigDecimal thickness) { this.thickness = thickness; }

    public String getSpecTemplate() { return specTemplate; }
    public void setSpecTemplate(String specTemplate) { this.specTemplate = specTemplate; }

    public String getExpectedLineName() { return expectedLineName; }
    public void setExpectedLineName(String expectedLineName) { this.expectedLineName = expectedLineName; }

    public String getBoundLineName() { return boundLineName; }
    public void setBoundLineName(String boundLineName) { this.boundLineName = boundLineName; }

    public String getSiteLineName() { return siteLineName; }
    public void setSiteLineName(String siteLineName) { this.siteLineName = siteLineName; }

    public String getEffectiveStatus() { return effectiveStatus; }
    public void setEffectiveStatus(String effectiveStatus) { this.effectiveStatus = effectiveStatus; }

    public String getDiscrepancyTypeText() { return discrepancyTypeText; }
    public void setDiscrepancyTypeText(String discrepancyTypeText) { this.discrepancyTypeText = discrepancyTypeText; }

    public String getEffectiveStatusText() { return effectiveStatusText; }
    public void setEffectiveStatusText(String effectiveStatusText) { this.effectiveStatusText = effectiveStatusText; }

    public String getPhysicalStatusText() { return physicalStatusText; }
    public void setPhysicalStatusText(String physicalStatusText) { this.physicalStatusText = physicalStatusText; }

    public String getLastTransferNo() { return lastTransferNo; }
    public void setLastTransferNo(String lastTransferNo) { this.lastTransferNo = lastTransferNo; }

    public String getLastTransferStatus() { return lastTransferStatus; }
    public void setLastTransferStatus(String lastTransferStatus) { this.lastTransferStatus = lastTransferStatus; }

    public String getLastTransferDate() { return lastTransferDate; }
    public void setLastTransferDate(String lastTransferDate) { this.lastTransferDate = lastTransferDate; }

    public String getTransferHint() { return transferHint; }
    public void setTransferHint(String transferHint) { this.transferHint = transferHint; }
}
