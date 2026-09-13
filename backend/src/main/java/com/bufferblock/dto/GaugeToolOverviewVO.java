package com.bufferblock.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 点检工装校准台概览：工装台账清单与各口径条数。
 * <p>
 * {@code blockedCount} / {@code blockedItems} / {@code blockedCodes} 是点检打卡拦截的唯一来源，
 * 只按在期工装（停用除外）实时派生，不受类型/状态/班组/关键字筛选影响，
 * 因此刷新校准台后“超期条数”必然与班次点检打卡时返回的拦截清单一致。
 */
public class GaugeToolOverviewVO {

    /** 筛选后台账行数（= items.size()） */
    private int totalCount;

    private int normalCount;
    /** 到期未校准条数 */
    private int overdueCount;
    /** 最近校准结论不合格条数 */
    private int failCount;
    /** 从未校准条数（台账到期日即建账初校到期日，仍按到期日判定在期/超期） */
    private int uncalibratedCount;
    private int disabledCount;

    /**
     * 拦截条数：在期（未停用）且“到期未校准或最近校准不合格”的工装条数，
     * 恒等于 blockedItems.size() / blockedCodes.size()，与点检打卡拦截同一份派生口径。
     */
    private int blockedCount;

    /** 筛选后的台账行 */
    private List<GaugeToolItemVO> items = new ArrayList<>();

    /** 全部拦截工装明细（不受筛选影响，按超期/不合格与编号排序） */
    private List<GaugeToolItemVO> blockedItems = new ArrayList<>();

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public int getNormalCount() { return normalCount; }
    public void setNormalCount(int normalCount) { this.normalCount = normalCount; }

    public int getOverdueCount() { return overdueCount; }
    public void setOverdueCount(int overdueCount) { this.overdueCount = overdueCount; }

    public int getFailCount() { return failCount; }
    public void setFailCount(int failCount) { this.failCount = failCount; }

    public int getUncalibratedCount() { return uncalibratedCount; }
    public void setUncalibratedCount(int uncalibratedCount) { this.uncalibratedCount = uncalibratedCount; }

    public int getDisabledCount() { return disabledCount; }
    public void setDisabledCount(int disabledCount) { this.disabledCount = disabledCount; }

    public int getBlockedCount() { return blockedCount; }
    public void setBlockedCount(int blockedCount) { this.blockedCount = blockedCount; }

    public List<GaugeToolItemVO> getItems() { return items; }
    public void setItems(List<GaugeToolItemVO> items) { this.items = items; }

    public List<GaugeToolItemVO> getBlockedItems() { return blockedItems; }
    public void setBlockedItems(List<GaugeToolItemVO> blockedItems) { this.blockedItems = blockedItems; }
}
