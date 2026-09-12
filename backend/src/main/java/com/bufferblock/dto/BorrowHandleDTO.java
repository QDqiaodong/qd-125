package com.bufferblock.dto;

/**
 * 取走/归还登记入参
 */
public class BorrowHandleDTO {

    /** 登记操作人 */
    private String operator;
    /** 补充说明（可空） */
    private String note;

    public BorrowHandleDTO() {
    }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
