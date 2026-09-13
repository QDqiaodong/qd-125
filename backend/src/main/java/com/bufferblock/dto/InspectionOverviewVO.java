package com.bufferblock.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 点检台账概览：筛选条件下的在用挡块清单与各口径条数，
 * 条数全部由同一份清单实时统计，保证刷新/重进页面后条数与列表一致。
 */
public class InspectionOverviewVO {

    /** 筛选后的总条数（= items.size()） */
    private int totalCount;
    /** 最近一次点检结论为“可用”的条数 */
    private int usableCount;
    /** 最近一次点检结论为“不可用”的条数 */
    private int unusableCount;
    /** 从未点检的条数 */
    private int neverInspectedCount;
    /**
     * 待复检通过条数：产线范围内最近一次点检结论仍为“不可用”的在用挡块数量，
     * 只按产线范围派生（不受班次/结论/关键字筛选影响），等于 pendingRecheckItems.size()，
     * 与提交拦截清单同源于已落库记录，刷新后条数与提示对得上。
     */
    private int pendingRecheckCount;

    private List<InspectionItemVO> items = new ArrayList<>();

    /** 待复检通过挡块明细（条数 = pendingRecheckCount，按班次/产线/编号排序） */
    private List<InspectionPendingRecheckItemVO> pendingRecheckItems = new ArrayList<>();

    public InspectionOverviewVO() {
    }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public int getUsableCount() { return usableCount; }
    public void setUsableCount(int usableCount) { this.usableCount = usableCount; }

    public int getUnusableCount() { return unusableCount; }
    public void setUnusableCount(int unusableCount) { this.unusableCount = unusableCount; }

    public int getNeverInspectedCount() { return neverInspectedCount; }
    public void setNeverInspectedCount(int neverInspectedCount) { this.neverInspectedCount = neverInspectedCount; }

    public int getPendingRecheckCount() { return pendingRecheckCount; }
    public void setPendingRecheckCount(int pendingRecheckCount) { this.pendingRecheckCount = pendingRecheckCount; }

    public List<InspectionItemVO> getItems() { return items; }
    public void setItems(List<InspectionItemVO> items) { this.items = items; }

    public List<InspectionPendingRecheckItemVO> getPendingRecheckItems() { return pendingRecheckItems; }
    public void setPendingRecheckItems(List<InspectionPendingRecheckItemVO> pendingRecheckItems) {
        this.pendingRecheckItems = pendingRecheckItems;
    }
}
