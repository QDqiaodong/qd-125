package com.bufferblock.dto;

import java.time.LocalDate;

public class TransferCreateDTO {
    private Long blockId;
    private Long fromLineId;
    private Long toLineId;
    private LocalDate transferDate;
    private String transferReason;
    private String transferOperator;
    private String receiveOperator;
    private String remark;

    public TransferCreateDTO() {
    }

    public Long getBlockId() { return blockId; }
    public void setBlockId(Long blockId) { this.blockId = blockId; }

    public Long getFromLineId() { return fromLineId; }
    public void setFromLineId(Long fromLineId) { this.fromLineId = fromLineId; }

    public Long getToLineId() { return toLineId; }
    public void setToLineId(Long toLineId) { this.toLineId = toLineId; }

    public LocalDate getTransferDate() { return transferDate; }
    public void setTransferDate(LocalDate transferDate) { this.transferDate = transferDate; }

    public String getTransferReason() { return transferReason; }
    public void setTransferReason(String transferReason) { this.transferReason = transferReason; }

    public String getTransferOperator() { return transferOperator; }
    public void setTransferOperator(String transferOperator) { this.transferOperator = transferOperator; }

    public String getReceiveOperator() { return receiveOperator; }
    public void setReceiveOperator(String receiveOperator) { this.receiveOperator = receiveOperator; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
