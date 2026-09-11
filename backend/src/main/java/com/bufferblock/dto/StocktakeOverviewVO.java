package com.bufferblock.dto;

/**
 * 盘点差异闭环首页概览。
 */
public class StocktakeOverviewVO {

    /** 盘点中批次数 */
    private long countingBatches;
    /** 待处理差异总数（跨盘点中批次） */
    private long pendingDiscrepancies;

    public StocktakeOverviewVO() {
    }

    public long getCountingBatches() { return countingBatches; }
    public void setCountingBatches(long countingBatches) { this.countingBatches = countingBatches; }

    public long getPendingDiscrepancies() { return pendingDiscrepancies; }
    public void setPendingDiscrepancies(long pendingDiscrepancies) { this.pendingDiscrepancies = pendingDiscrepancies; }
}
