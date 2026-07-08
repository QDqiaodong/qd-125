package com.bufferblock.dto;

import java.time.LocalDate;

public class TransferQueryDTO {
    private LocalDate startDate;
    private LocalDate endDate;
    private Long fromLineId;
    private Long toLineId;
    private String blockCode;
    private Integer page = 1;
    private Integer size = 10;

    public TransferQueryDTO() {
    }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public Long getFromLineId() { return fromLineId; }
    public void setFromLineId(Long fromLineId) { this.fromLineId = fromLineId; }

    public Long getToLineId() { return toLineId; }
    public void setToLineId(Long toLineId) { this.toLineId = toLineId; }

    public String getBlockCode() { return blockCode; }
    public void setBlockCode(String blockCode) { this.blockCode = blockCode; }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
}
