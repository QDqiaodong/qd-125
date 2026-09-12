package com.bufferblock.dto;

/**
 * 取消预约入参。取消必须填写原因，原因与操作人随状态一并落库。
 */
public class BorrowCancelDTO {

    /** 取消原因（必填） */
    private String cancelReason;
    /** 取消操作人 */
    private String operator;

    public BorrowCancelDTO() {
    }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
