package com.bufferblock.entity;

import jakarta.persistence.*;

/**
 * 按业务日期维护盘点批次流水号。每个日期一行，由数据库行锁串行分配序号。
 */
@Entity
@Table(name = "stocktake_sequence")
public class StocktakeSequence {

    @Id
    @Column(name = "sequence_date", length = 8, nullable = false)
    private String sequenceDate;

    @Column(name = "current_value", nullable = false)
    private Integer currentValue;

    protected StocktakeSequence() {
    }

    public StocktakeSequence(String sequenceDate, Integer currentValue) {
        this.sequenceDate = sequenceDate;
        this.currentValue = currentValue;
    }

    public String getSequenceDate() {
        return sequenceDate;
    }

    public void setSequenceDate(String sequenceDate) {
        this.sequenceDate = sequenceDate;
    }

    public Integer getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(Integer currentValue) {
        this.currentValue = currentValue;
    }
}
