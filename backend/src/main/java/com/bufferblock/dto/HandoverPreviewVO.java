package com.bufferblock.dto;

import com.bufferblock.entity.ShiftHandoverItem;

import java.util.List;

/**
 * 交班登记预览：提交前展示当前将被一次性登记的四类未结事项
 * （未还预约/待确认移交/待处理盘点差异/拦截中点检工装），内容即正式登记时的快照口径。
 */
public class HandoverPreviewVO {

    /** 将被登记的事项清单（瞬态快照，未落库） */
    private List<ShiftHandoverItem> items;
    /** 未还预约条数 */
    private long borrowCount;
    /** 待确认移交条数 */
    private long transferCount;
    /** 待处理盘点差异条数 */
    private long stocktakeCount;
    /** 拦截中点检工装条数（与校准台拦截条数同源） */
    private long gaugeCount;

    public HandoverPreviewVO() {
    }

    public List<ShiftHandoverItem> getItems() { return items; }
    public void setItems(List<ShiftHandoverItem> items) { this.items = items; }

    public long getBorrowCount() { return borrowCount; }
    public void setBorrowCount(long borrowCount) { this.borrowCount = borrowCount; }

    public long getTransferCount() { return transferCount; }
    public void setTransferCount(long transferCount) { this.transferCount = transferCount; }

    public long getStocktakeCount() { return stocktakeCount; }
    public void setStocktakeCount(long stocktakeCount) { this.stocktakeCount = stocktakeCount; }

    public long getGaugeCount() { return gaugeCount; }
    public void setGaugeCount(long gaugeCount) { this.gaugeCount = gaugeCount; }
}
