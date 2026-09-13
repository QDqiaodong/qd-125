package com.bufferblock.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 点检台账行：一块在用挡块一行，携带其最近一次班次点检结论（实时从落库记录派生）。
 */
public class InspectionItemVO {

    private Long blockId;
    private String blockCode;
    private String adapterModel;
    private BigDecimal thickness;
    private String specTemplate;
    private Long lineId;
    private String lineName;

    /** 最近一次点检时刻（从未点检为 null） */
    private LocalDateTime lastInspectionTime;
    /** 最近一次点检班次：MORNING/AFTERNOON/NIGHT */
    private String lastShiftCode;
    /** 最近一次点检人 */
    private String lastInspector;
    /** 最近一次点检结论：USABLE-可用 / UNUSABLE-不可用 */
    private String lastResult;
    /** 最近一次点检备注 */
    private String lastNote;
    /** 是否从未点检 */
    private boolean neverInspected;
    /** 是否“不可用且尚未复检通过”（最近一次点检结论为不可用） */
    private boolean pendingRecheck;

    public InspectionItemVO() {
    }

    public Long getBlockId() { return blockId; }
    public void setBlockId(Long blockId) { this.blockId = blockId; }

    public String getBlockCode() { return blockCode; }
    public void setBlockCode(String blockCode) { this.blockCode = blockCode; }

    public String getAdapterModel() { return adapterModel; }
    public void setAdapterModel(String adapterModel) { this.adapterModel = adapterModel; }

    public BigDecimal getThickness() { return thickness; }
    public void setThickness(BigDecimal thickness) { this.thickness = thickness; }

    public String getSpecTemplate() { return specTemplate; }
    public void setSpecTemplate(String specTemplate) { this.specTemplate = specTemplate; }

    public Long getLineId() { return lineId; }
    public void setLineId(Long lineId) { this.lineId = lineId; }

    public String getLineName() { return lineName; }
    public void setLineName(String lineName) { this.lineName = lineName; }

    public LocalDateTime getLastInspectionTime() { return lastInspectionTime; }
    public void setLastInspectionTime(LocalDateTime lastInspectionTime) { this.lastInspectionTime = lastInspectionTime; }

    public String getLastShiftCode() { return lastShiftCode; }
    public void setLastShiftCode(String lastShiftCode) { this.lastShiftCode = lastShiftCode; }

    public String getLastInspector() { return lastInspector; }
    public void setLastInspector(String lastInspector) { this.lastInspector = lastInspector; }

    public String getLastResult() { return lastResult; }
    public void setLastResult(String lastResult) { this.lastResult = lastResult; }

    public String getLastNote() { return lastNote; }
    public void setLastNote(String lastNote) { this.lastNote = lastNote; }

    public boolean isNeverInspected() { return neverInspected; }
    public void setNeverInspected(boolean neverInspected) { this.neverInspected = neverInspected; }

    public boolean isPendingRecheck() { return pendingRecheck; }
    public void setPendingRecheck(boolean pendingRecheck) { this.pendingRecheck = pendingRecheck; }
}
