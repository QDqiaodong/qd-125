package com.bufferblock.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "buffer_block")
public class BufferBlock {

    /** 在用（可上线、可移交） */
    public static final String STATUS_IN_SERVICE = "IN_SERVICE";
    /** 挂起待修（校准不合格，修好复校通过前不得上线/移交） */
    public static final String STATUS_SUSPENDED = "SUSPENDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "block_code", nullable = false, unique = true, length = 50)
    private String blockCode;

    @Column(name = "adapter_model", nullable = false, length = 100)
    private String adapterModel;

    @Column(name = "thickness", nullable = false, precision = 10, scale = 2)
    private BigDecimal thickness;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "spec_template", length = 50)
    private String specTemplate;

    @Column(name = "calibration_cycle_months")
    private Integer calibrationCycleMonths = 12;

    @Column(name = "service_status", length = 20)
    private String serviceStatus = STATUS_IN_SERVICE;

    @Column(name = "suspend_reason", length = 500)
    private String suspendReason;

    @Column(name = "suspend_time")
    private LocalDateTime suspendTime;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    public BufferBlock() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBlockCode() { return blockCode; }
    public void setBlockCode(String blockCode) { this.blockCode = blockCode; }

    public String getAdapterModel() { return adapterModel; }
    public void setAdapterModel(String adapterModel) { this.adapterModel = adapterModel; }

    public BigDecimal getThickness() { return thickness; }
    public void setThickness(BigDecimal thickness) { this.thickness = thickness; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getSpecTemplate() { return specTemplate; }
    public void setSpecTemplate(String specTemplate) { this.specTemplate = specTemplate; }

    public Integer getCalibrationCycleMonths() { return calibrationCycleMonths; }
    public void setCalibrationCycleMonths(Integer calibrationCycleMonths) { this.calibrationCycleMonths = calibrationCycleMonths; }

    public String getServiceStatus() { return serviceStatus; }
    public void setServiceStatus(String serviceStatus) { this.serviceStatus = serviceStatus; }

    public String getSuspendReason() { return suspendReason; }
    public void setSuspendReason(String suspendReason) { this.suspendReason = suspendReason; }

    public LocalDateTime getSuspendTime() { return suspendTime; }
    public void setSuspendTime(LocalDateTime suspendTime) { this.suspendTime = suspendTime; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
