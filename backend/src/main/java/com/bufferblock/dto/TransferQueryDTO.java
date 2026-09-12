package com.bufferblock.dto;

import java.time.LocalDate;

public class TransferQueryDTO {
    private LocalDate startDate;
    private LocalDate endDate;
    private Long fromLineId;
    private Long toLineId;
    /** 产线筛选：匹配移出或移入产线（移交确认列表使用） */
    private Long lineId;
    private String blockCode;
    /** 状态：PENDING-待确认, CONFIRMED-已确认, REJECTED-已驳回 */
    private String status;
    /**
     * 等待时长排序：LONGEST_FIRST-等待最长优先, SHORTEST_FIRST-等待最短优先；
     * 为空时按默认顺序（移交日期、登记时间倒序）
     */
    private String waitSort;
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

    public Long getLineId() { return lineId; }
    public void setLineId(Long lineId) { this.lineId = lineId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getBlockCode() { return blockCode; }
    public void setBlockCode(String blockCode) { this.blockCode = blockCode; }

    public String getWaitSort() { return waitSort; }
    public void setWaitSort(String waitSort) { this.waitSort = waitSort; }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
}
