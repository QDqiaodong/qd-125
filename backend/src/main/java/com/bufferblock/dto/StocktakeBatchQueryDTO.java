package com.bufferblock.dto;

import java.time.LocalDate;

public class StocktakeBatchQueryDTO {

    private Long lineId;
    /** COUNTING-盘点中, COMPLETED-已完成 */
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private String batchNo;
    private Integer page = 1;
    private Integer size = 10;

    public StocktakeBatchQueryDTO() {
    }

    public Long getLineId() { return lineId; }
    public void setLineId(Long lineId) { this.lineId = lineId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
}
