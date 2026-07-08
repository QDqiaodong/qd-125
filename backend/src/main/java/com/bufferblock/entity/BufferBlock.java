package com.bufferblock.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "buffer_block")
public class BufferBlock {

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

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
