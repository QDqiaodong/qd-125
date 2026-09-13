package com.bufferblock.dto;

/**
 * 点检台账查询条件：按产线（车间或具体产线均可）筛选在用挡块，
 * 可叠加班次/最近结论/关键字筛选。条数与列表同源返回，刷新后保持一致。
 */
public class InspectionQueryDTO {

    /** 产线/车间节点 ID；为空表示全部产线 */
    private Long lineId;
    /** 按最近一次点检班次筛选：MORNING/AFTERNOON/NIGHT */
    private String shiftCode;
    /** 按最近一次点检结论筛选：USABLE/UNUSABLE；NEVER-从未点检 */
    private String result;
    /** 挡块编号/机型关键字 */
    private String keyword;

    public InspectionQueryDTO() {
    }

    public Long getLineId() { return lineId; }
    public void setLineId(Long lineId) { this.lineId = lineId; }

    public String getShiftCode() { return shiftCode; }
    public void setShiftCode(String shiftCode) { this.shiftCode = shiftCode; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
}
