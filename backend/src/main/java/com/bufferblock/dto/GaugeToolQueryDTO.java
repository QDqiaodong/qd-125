package com.bufferblock.dto;

/**
 * 点检工装校准台筛选条件：工装类型、校准状态、保管班组、编号/型号关键字。
 */
public class GaugeToolQueryDTO {

    /** 工装类型：CALIPER/FEELER/DIAL_INDICATOR */
    private String toolType;

    /**
     * 校准状态筛选：
     * NORMAL 合格在期 / OVERDUE 到期未校准 / FAIL 校准结论不合格 / DISABLED 已停用
     */
    private String status;

    private String keeperTeam;

    private String keyword;

    public String getToolType() { return toolType; }
    public void setToolType(String toolType) { this.toolType = toolType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getKeeperTeam() { return keeperTeam; }
    public void setKeeperTeam(String keeperTeam) { this.keeperTeam = keeperTeam; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
}
