package com.bufferblock.dto;

import java.time.LocalDateTime;

/**
 * 班次点检打卡请求：登记点检人、点检班次与本次是否可用。
 * 打卡时刻缺省由服务端取当前时间，全部字段随记录落库。
 */
public class InspectionCreateDTO {

    private Long blockId;
    /** 点检班次：MORNING-早班 / AFTERNOON-中班 / NIGHT-晚班 */
    private String shiftCode;
    private String inspector;
    /** 点检结论：USABLE-可用 / UNUSABLE-不可用 */
    private String result;
    private String note;
    /** 打卡时刻，可选；缺省取服务端当前时间 */
    private LocalDateTime inspectionTime;

    public InspectionCreateDTO() {
    }

    public Long getBlockId() { return blockId; }
    public void setBlockId(Long blockId) { this.blockId = blockId; }

    public String getShiftCode() { return shiftCode; }
    public void setShiftCode(String shiftCode) { this.shiftCode = shiftCode; }

    public String getInspector() { return inspector; }
    public void setInspector(String inspector) { this.inspector = inspector; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getInspectionTime() { return inspectionTime; }
    public void setInspectionTime(LocalDateTime inspectionTime) { this.inspectionTime = inspectionTime; }
}
