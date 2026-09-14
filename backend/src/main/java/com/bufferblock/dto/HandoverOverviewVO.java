package com.bufferblock.dto;

import java.time.LocalDateTime;

/**
 * 班组交班概览：当前进行中的交班与未确认条数。
 * 供交班页横幅、借用预约页“禁止新开预约”闸门与首页提醒使用，
 * 全部由落库数据实时派生，关闭页面再打开计数保持一致。
 */
public class HandoverOverviewVO {

    /** 当前是否存在交班中（接班人未确认完）的交班单 */
    private boolean inProgress;
    private Long handoverId;
    private String handoverNo;
    private String fromTeam;
    private String toTeam;
    private Integer totalCount = 0;
    private Integer confirmedCount = 0;
    private Integer unconfirmedCount = 0;
    /** 未确认拦截中点检工装件数（与校准台拦截条数同一派生口径，刷新后必然对得上） */
    private Integer unconfirmedGaugeCount = 0;
    private LocalDateTime createTime;

    public HandoverOverviewVO() {
    }

    public boolean isInProgress() { return inProgress; }
    public void setInProgress(boolean inProgress) { this.inProgress = inProgress; }

    public Long getHandoverId() { return handoverId; }
    public void setHandoverId(Long handoverId) { this.handoverId = handoverId; }

    public String getHandoverNo() { return handoverNo; }
    public void setHandoverNo(String handoverNo) { this.handoverNo = handoverNo; }

    public String getFromTeam() { return fromTeam; }
    public void setFromTeam(String fromTeam) { this.fromTeam = fromTeam; }

    public String getToTeam() { return toTeam; }
    public void setToTeam(String toTeam) { this.toTeam = toTeam; }

    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }

    public Integer getConfirmedCount() { return confirmedCount; }
    public void setConfirmedCount(Integer confirmedCount) { this.confirmedCount = confirmedCount; }

    public Integer getUnconfirmedCount() { return unconfirmedCount; }
    public void setUnconfirmedCount(Integer unconfirmedCount) { this.unconfirmedCount = unconfirmedCount; }

    public Integer getUnconfirmedGaugeCount() { return unconfirmedGaugeCount; }
    public void setUnconfirmedGaugeCount(Integer unconfirmedGaugeCount) { this.unconfirmedGaugeCount = unconfirmedGaugeCount; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
