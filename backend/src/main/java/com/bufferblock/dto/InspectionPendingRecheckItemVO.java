package com.bufferblock.dto;

/**
 * 点检概览中的“待复检通过”挡块明细：当前绑定在所选产线范围内、
 * 最近一次点检结论仍为“不可用”的在用挡块（按班次/产线/编号排序）。
 * 与提交拦截返回的清单同源于已落库点检记录实时派生，刷新后条数与编号一致。
 */
public class InspectionPendingRecheckItemVO {

    private Long blockId;
    private String blockCode;
    /** 不可用结论对应的班次：MORNING/AFTERNOON/NIGHT */
    private String shiftCode;
    private String shiftName;
    private Long lineId;
    private String lineName;

    public InspectionPendingRecheckItemVO() {
    }

    public Long getBlockId() { return blockId; }
    public void setBlockId(Long blockId) { this.blockId = blockId; }

    public String getBlockCode() { return blockCode; }
    public void setBlockCode(String blockCode) { this.blockCode = blockCode; }

    public String getShiftCode() { return shiftCode; }
    public void setShiftCode(String shiftCode) { this.shiftCode = shiftCode; }

    public String getShiftName() { return shiftName; }
    public void setShiftName(String shiftName) { this.shiftName = shiftName; }

    public Long getLineId() { return lineId; }
    public void setLineId(Long lineId) { this.lineId = lineId; }

    public String getLineName() { return lineName; }
    public void setLineName(String lineName) { this.lineName = lineName; }
}
