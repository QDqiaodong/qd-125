package com.bufferblock.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 点检提交被“待复检通过”挡块拦截时返回的结构化明细：
 * 本产线该班次已有结论为不可用、且至今尚未复检通过的在用挡块清单（不含本次提交的挡块自身）。
 * 条数与编号清单、点检台账概览中的 pendingRecheckCount 同源于已落库点检记录实时派生，
 * 刷新或重进页面后保持一致。
 */
public class InspectionPendingRecheckVO {

    /** 待复检通过的挡块条数（= blockCodes.size()） */
    private int count;
    /** 拦截对应的班次：MORNING/AFTERNOON/NIGHT */
    private String shiftCode;
    /** 班次中文名：早班/中班/晚班 */
    private String shiftName;
    /** 产线 ID */
    private Long lineId;
    /** 产线名称 */
    private String lineName;
    /** 待复检通过挡块编号（按编号排序） */
    private List<String> blockCodes = new ArrayList<>();

    public InspectionPendingRecheckVO() {
    }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public String getShiftCode() { return shiftCode; }
    public void setShiftCode(String shiftCode) { this.shiftCode = shiftCode; }

    public String getShiftName() { return shiftName; }
    public void setShiftName(String shiftName) { this.shiftName = shiftName; }

    public Long getLineId() { return lineId; }
    public void setLineId(Long lineId) { this.lineId = lineId; }

    public String getLineName() { return lineName; }
    public void setLineName(String lineName) { this.lineName = lineName; }

    public List<String> getBlockCodes() { return blockCodes; }
    public void setBlockCodes(List<String> blockCodes) { this.blockCodes = blockCodes; }
}
