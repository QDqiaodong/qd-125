package com.bufferblock.dto;

import com.bufferblock.entity.BlockLineBinding;
import com.bufferblock.entity.BlockTransfer;
import com.bufferblock.entity.StocktakeItem;
import com.bufferblock.entity.TransferFlowRecord;

import java.util.List;

/**
 * 盘点差异详情追溯：盘点明细 + 完整绑定历史 + 最近移交单与流转记录。
 */
public class StocktakeTraceVO {

    private StocktakeItem item;
    private List<BlockLineBinding> bindings;
    private List<BlockTransfer> transfers;
    private List<TransferFlowRecord> flowRecords;
    private BlockTransfer latestTransfer;

    public StocktakeTraceVO() {
    }

    public StocktakeItem getItem() { return item; }
    public void setItem(StocktakeItem item) { this.item = item; }

    public List<BlockLineBinding> getBindings() { return bindings; }
    public void setBindings(List<BlockLineBinding> bindings) { this.bindings = bindings; }

    public List<BlockTransfer> getTransfers() { return transfers; }
    public void setTransfers(List<BlockTransfer> transfers) { this.transfers = transfers; }

    public List<TransferFlowRecord> getFlowRecords() { return flowRecords; }
    public void setFlowRecords(List<TransferFlowRecord> flowRecords) { this.flowRecords = flowRecords; }

    public BlockTransfer getLatestTransfer() { return latestTransfer; }
    public void setLatestTransfer(BlockTransfer latestTransfer) { this.latestTransfer = latestTransfer; }
}
