package com.bufferblock.dto;

import java.time.LocalDate;

public class StocktakeBatchCreateDTO {

    private Long lineId;
    private LocalDate stocktakeDate;
    private String operator;
    private String remark;

    public StocktakeBatchCreateDTO() {
    }

    public Long getLineId() { return lineId; }
    public void setLineId(Long lineId) { this.lineId = lineId; }

    public LocalDate getStocktakeDate() { return stocktakeDate; }
    public void setStocktakeDate(LocalDate stocktakeDate) { this.stocktakeDate = stocktakeDate; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
