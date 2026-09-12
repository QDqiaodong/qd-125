package com.bufferblock.dto;

/**
 * 取走/归还登记入参
 */
public class BorrowHandleDTO {

    /** 登记操作人 */
    private String operator;
    /** 补充说明（可空） */
    private String note;
    /** 归还登记时可改归还点（可空：留空沿用预约约定的归还点） */
    private String actualReturnPoint;

    public BorrowHandleDTO() {
    }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getActualReturnPoint() { return actualReturnPoint; }
    public void setActualReturnPoint(String actualReturnPoint) { this.actualReturnPoint = actualReturnPoint; }
}
