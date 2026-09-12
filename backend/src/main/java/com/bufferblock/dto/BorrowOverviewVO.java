package com.bufferblock.dto;

/**
 * 借用预约概览计数（首页提醒角标使用，全部由落库状态实时派生，刷新后保持一致）。
 */
public class BorrowOverviewVO {

    /** 已预约、等待取走的单数 */
    private long reservedCount;
    /** 已取走、实物占用中的单数 */
    private long pickedUpCount;
    /** 过了约定取用时间仍未取走（已提醒）的单数 */
    private long overdueCount;
    /** 已取走但超过计划还期仍未归还的单数 */
    private long overdueReturnCount;
    /** 占用中合计（已预约 + 已取走 + 逾时未取），即档案上“已约出”的挡块数 */
    private long activeCount;

    public BorrowOverviewVO() {
    }

    public long getReservedCount() { return reservedCount; }
    public void setReservedCount(long reservedCount) { this.reservedCount = reservedCount; }

    public long getPickedUpCount() { return pickedUpCount; }
    public void setPickedUpCount(long pickedUpCount) { this.pickedUpCount = pickedUpCount; }

    public long getOverdueCount() { return overdueCount; }
    public void setOverdueCount(long overdueCount) { this.overdueCount = overdueCount; }

    public long getOverdueReturnCount() { return overdueReturnCount; }
    public void setOverdueReturnCount(long overdueReturnCount) { this.overdueReturnCount = overdueReturnCount; }

    public long getActiveCount() { return activeCount; }
    public void setActiveCount(long activeCount) { this.activeCount = activeCount; }
}
