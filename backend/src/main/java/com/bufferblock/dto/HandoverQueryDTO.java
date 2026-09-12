package com.bufferblock.dto;

public class HandoverQueryDTO {

    /** 状态：IN_PROGRESS-交班中 / COMPLETED-已完成，为空查全部 */
    private String status;
    /** 交班单号模糊筛选 */
    private String handoverNo;
    private Integer page = 1;
    private Integer size = 10;

    public HandoverQueryDTO() {
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getHandoverNo() { return handoverNo; }
    public void setHandoverNo(String handoverNo) { this.handoverNo = handoverNo; }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
}
