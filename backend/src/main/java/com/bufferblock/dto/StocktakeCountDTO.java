package com.bufferblock.dto;

/**
 * 逐项录入实物盘点结果。
 */
public class StocktakeCountDTO {

    /** 挡块编号（扫码/手输），系统据此比对挡块档案 */
    private String blockCode;
    /** 实物状态：NORMAL-正常, DAMAGED-损坏, SCRAPPED-报废 */
    private String physicalStatus;
    /** 现场产线ID，默认批次产线 */
    private Long siteLineId;
    /** 盘点人 */
    private String operator;
    /** 现场备注 */
    private String remark;

    public StocktakeCountDTO() {
    }

    public String getBlockCode() { return blockCode; }
    public void setBlockCode(String blockCode) { this.blockCode = blockCode; }

    public String getPhysicalStatus() { return physicalStatus; }
    public void setPhysicalStatus(String physicalStatus) { this.physicalStatus = physicalStatus; }

    public Long getSiteLineId() { return siteLineId; }
    public void setSiteLineId(Long siteLineId) { this.siteLineId = siteLineId; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
