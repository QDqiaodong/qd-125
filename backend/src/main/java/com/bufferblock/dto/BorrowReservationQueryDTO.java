package com.bufferblock.dto;

public class BorrowReservationQueryDTO {

    /** 状态：RESERVED/PICKED_UP/RETURNED/CANCELLED/OVERDUE，为空查全部 */
    private String status;
    /** 班组名模糊筛选 */
    private String teamName;
    /** 挡块编号模糊筛选 */
    private String blockCode;
    private Integer page = 1;
    private Integer size = 10;

    public BorrowReservationQueryDTO() {
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public String getBlockCode() { return blockCode; }
    public void setBlockCode(String blockCode) { this.blockCode = blockCode; }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
}
