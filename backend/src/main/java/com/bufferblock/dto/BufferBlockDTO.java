package com.bufferblock.dto;

import java.math.BigDecimal;

public class BufferBlockDTO {
    private Long id;
    private String blockCode;
    private String adapterModel;
    private BigDecimal thickness;
    private String imageUrl;
    private String specTemplate;
    private Long lineId;
    private String lineName;

    public BufferBlockDTO() {
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

    public Long getLineId() { return lineId; }
    public void setLineId(Long lineId) { this.lineId = lineId; }

    public String getLineName() { return lineName; }
    public void setLineName(String lineName) { this.lineName = lineName; }
}
