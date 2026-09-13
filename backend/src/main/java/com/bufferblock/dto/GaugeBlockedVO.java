package com.bufferblock.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 点检工装校验拦截明细：班次点检打卡时若存在“到期未校准或校准结论不合格”的在用工装，
 * 返回结构化清单（条数 + 超期工装编号 + 逐条明细），前端弹窗逐条列出超期工装编号。
 * 与校准台 blockedItems 同一份实时派生口径，条数与编号必然对得上。
 */
public class GaugeBlockedVO {

    /** 拦截条数，恒等于 codes.size() / items.size() */
    private int count;

    /** 超期/不合格工装编号（按编号排序） */
    private List<String> codes = new ArrayList<>();

    /** 逐条明细（工装类型/保管班组/到期日/拦截原因） */
    private List<GaugeToolItemVO> items = new ArrayList<>();

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public List<String> getCodes() { return codes; }
    public void setCodes(List<String> codes) { this.codes = codes; }

    public List<GaugeToolItemVO> getItems() { return items; }
    public void setItems(List<GaugeToolItemVO> items) { this.items = items; }
}
