package com.bufferblock.dto;

public class TransferHandleDTO {

    /** CONFIRM-确认接收, REJECT-驳回 */
    private String action;

    /** 接收方处理人 */
    private String receiveOperator;

    /** 处理说明（必填） */
    private String handleNote;

    public TransferHandleDTO() {
    }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getReceiveOperator() { return receiveOperator; }
    public void setReceiveOperator(String receiveOperator) { this.receiveOperator = receiveOperator; }

    public String getHandleNote() { return handleNote; }
    public void setHandleNote(String handleNote) { this.handleNote = handleNote; }
}
