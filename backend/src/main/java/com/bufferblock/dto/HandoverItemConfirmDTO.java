package com.bufferblock.dto;

/**
 * 接班人逐条确认交班事项的入参。
 */
public class HandoverItemConfirmDTO {

    /** 确认人（接班人，必填） */
    private String operator;
    /** 确认备注（选填） */
    private String note;

    public HandoverItemConfirmDTO() {
    }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
