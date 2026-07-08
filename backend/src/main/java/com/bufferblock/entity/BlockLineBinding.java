package com.bufferblock.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "block_line_binding")
public class BlockLineBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "block_id", nullable = false)
    private Long blockId;

    @Column(name = "line_id", nullable = false)
    private Long lineId;

    @Column(name = "bind_type", nullable = false)
    private Integer bindType = 1;

    @Column(name = "bind_time", nullable = false)
    private LocalDateTime bindTime;

    @Column(name = "operator", length = 50)
    private String operator;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "is_current")
    private Integer isCurrent = 1;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Transient
    private String blockCode;

    @Transient
    private String lineCode;

    @Transient
    private String lineName;

    public BlockLineBinding() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBlockId() { return blockId; }
    public void setBlockId(Long blockId) { this.blockId = blockId; }

    public Long getLineId() { return lineId; }
    public void setLineId(Long lineId) { this.lineId = lineId; }

    public Integer getBindType() { return bindType; }
    public void setBindType(Integer bindType) { this.bindType = bindType; }

    public LocalDateTime getBindTime() { return bindTime; }
    public void setBindTime(LocalDateTime bindTime) { this.bindTime = bindTime; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Integer getIsCurrent() { return isCurrent; }
    public void setIsCurrent(Integer isCurrent) { this.isCurrent = isCurrent; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public String getBlockCode() { return blockCode; }
    public void setBlockCode(String blockCode) { this.blockCode = blockCode; }

    public String getLineCode() { return lineCode; }
    public void setLineCode(String lineCode) { this.lineCode = lineCode; }

    public String getLineName() { return lineName; }
    public void setLineName(String lineName) { this.lineName = lineName; }
}
