package com.bufferblock.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 挡块班次点检记录。每个班次点检人对在用挡块逐块打卡一行：
 * 点检班次（早/中/晚班）、点检结论（USABLE 可用 / UNUSABLE 不可用）、点检人与备注。
 */
@Entity
@Table(name = "block_inspection")
public class BlockInspection {

    public static final String RESULT_USABLE = "USABLE";
    public static final String RESULT_UNUSABLE = "UNUSABLE";

    public static final String SHIFT_MORNING = "MORNING";
    public static final String SHIFT_AFTERNOON = "AFTERNOON";
    public static final String SHIFT_NIGHT = "NIGHT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "block_id", nullable = false)
    private Long blockId;

    @Column(name = "inspection_time", nullable = false)
    private LocalDateTime inspectionTime;

    @Column(name = "shift_code", nullable = false, length = 20)
    private String shiftCode;

    @Column(name = "inspector", nullable = false, length = 50)
    private String inspector;

    @Column(name = "result", nullable = false, length = 20)
    private String result;

    @Column(name = "note", length = 500)
    private String note;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    public BlockInspection() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBlockId() { return blockId; }
    public void setBlockId(Long blockId) { this.blockId = blockId; }

    public LocalDateTime getInspectionTime() { return inspectionTime; }
    public void setInspectionTime(LocalDateTime inspectionTime) { this.inspectionTime = inspectionTime; }

    public String getShiftCode() { return shiftCode; }
    public void setShiftCode(String shiftCode) { this.shiftCode = shiftCode; }

    public String getInspector() { return inspector; }
    public void setInspector(String inspector) { this.inspector = inspector; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
