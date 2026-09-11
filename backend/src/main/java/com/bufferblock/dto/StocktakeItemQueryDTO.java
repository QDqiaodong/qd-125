package com.bufferblock.dto;

public class StocktakeItemQueryDTO {

    /** 差异类型：MISSING-缺失, WRONG_LINE-错线, DUPLICATE-重复, EXTRA-盘盈, DAMAGED-损坏, SCRAPPED-报废, NONE-无差异 */
    private String discrepancyType;
    /** 有效处理状态：WAITING-待盘, PENDING-待处理, CONFIRMED-已确认, IGNORED-已忽略, NONE-无差异 */
    private String status;
    private String blockCode;
    private Integer page = 1;
    private Integer size = 10;

    public StocktakeItemQueryDTO() {
    }

    public String getDiscrepancyType() { return discrepancyType; }
    public void setDiscrepancyType(String discrepancyType) { this.discrepancyType = discrepancyType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getBlockCode() { return blockCode; }
    public void setBlockCode(String blockCode) { this.blockCode = blockCode; }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
}
