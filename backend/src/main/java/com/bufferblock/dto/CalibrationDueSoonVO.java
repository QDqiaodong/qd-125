package com.bufferblock.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 校准临期概览（派生，不入库）：下次应校日期进入临期窗口的在用挡块清单。
 * 挂起待修、校准逾期、未校准的挡块不计入；条数与清单同源，刷新后保持一致。
 */
public class CalibrationDueSoonVO {

    /** 临期窗口天数：下次应校日期距今天不超过该天数即视为临期 */
    private Integer windowDays;
    /** 临期条数，与 items 大小一致 */
    private Integer count;
    private List<Item> items = new ArrayList<>();

    public CalibrationDueSoonVO() {
    }

    public Integer getWindowDays() { return windowDays; }
    public void setWindowDays(Integer windowDays) { this.windowDays = windowDays; }

    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    /**
     * 单条临期挡块：核对应校日、所属产线与最近一次校准结论。
     */
    public static class Item {
        private Long blockId;
        private String blockCode;
        private String adapterModel;
        private Long lineId;
        private String lineName;
        /** 下次应校日期 */
        private LocalDate nextDueDate;
        /** 距应校日剩余天数（0 表示今天到期） */
        private Integer daysUntilDue;
        /** 最近一次校准结论：PASS-合格 / FAIL-不合格 */
        private String lastResult;
        private LocalDate lastCalibrationDate;
        private String lastCalibrator;

        public Long getBlockId() { return blockId; }
        public void setBlockId(Long blockId) { this.blockId = blockId; }

        public String getBlockCode() { return blockCode; }
        public void setBlockCode(String blockCode) { this.blockCode = blockCode; }

        public String getAdapterModel() { return adapterModel; }
        public void setAdapterModel(String adapterModel) { this.adapterModel = adapterModel; }

        public Long getLineId() { return lineId; }
        public void setLineId(Long lineId) { this.lineId = lineId; }

        public String getLineName() { return lineName; }
        public void setLineName(String lineName) { this.lineName = lineName; }

        public LocalDate getNextDueDate() { return nextDueDate; }
        public void setNextDueDate(LocalDate nextDueDate) { this.nextDueDate = nextDueDate; }

        public Integer getDaysUntilDue() { return daysUntilDue; }
        public void setDaysUntilDue(Integer daysUntilDue) { this.daysUntilDue = daysUntilDue; }

        public String getLastResult() { return lastResult; }
        public void setLastResult(String lastResult) { this.lastResult = lastResult; }

        public LocalDate getLastCalibrationDate() { return lastCalibrationDate; }
        public void setLastCalibrationDate(LocalDate lastCalibrationDate) { this.lastCalibrationDate = lastCalibrationDate; }

        public String getLastCalibrator() { return lastCalibrator; }
        public void setLastCalibrator(String lastCalibrator) { this.lastCalibrator = lastCalibrator; }
    }
}
