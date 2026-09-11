package com.bufferblock.dto;

/**
 * 盘点差异处理：确认差异属实 / 忽略（误盘、合理差异）。
 */
public class StocktakeHandleDTO {

    /** CONFIRM-确认差异, IGNORE-忽略差异 */
    private String action;
    /** 处理人 */
    private String operator;
    /** 处理说明/备注 */
    private String handleNote;

    public StocktakeHandleDTO() {
    }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getHandleNote() { return handleNote; }
    public void setHandleNote(String handleNote) { this.handleNote = handleNote; }
}
