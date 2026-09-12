package com.bufferblock.dto;

/**
 * 班组交班登记入参：提交时系统一次性快照当前全部未还预约、
 * 待确认移交与待处理盘点差异作为交班事项清单。
 */
public class HandoverCreateDTO {

    /** 交班班组 */
    private String fromTeam;
    /** 接班班组 */
    private String toTeam;
    /** 交班登记人 */
    private String handoverOperator;
    /** 接班人（逐条确认的责任人） */
    private String receiveOperator;
    /** 备注（选填） */
    private String remark;

    public HandoverCreateDTO() {
    }

    public String getFromTeam() { return fromTeam; }
    public void setFromTeam(String fromTeam) { this.fromTeam = fromTeam; }

    public String getToTeam() { return toTeam; }
    public void setToTeam(String toTeam) { this.toTeam = toTeam; }

    public String getHandoverOperator() { return handoverOperator; }
    public void setHandoverOperator(String handoverOperator) { this.handoverOperator = handoverOperator; }

    public String getReceiveOperator() { return receiveOperator; }
    public void setReceiveOperator(String receiveOperator) { this.receiveOperator = receiveOperator; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
