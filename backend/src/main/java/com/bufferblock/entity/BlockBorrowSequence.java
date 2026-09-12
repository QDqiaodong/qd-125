package com.bufferblock.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "block_borrow_sequence")
public class BlockBorrowSequence {

    @Id
    @Column(name = "sequence_date", length = 8)
    private String sequenceDate;

    @Column(name = "current_value", nullable = false)
    private Integer currentValue;

    public BlockBorrowSequence() {
    }

    public BlockBorrowSequence(String sequenceDate, Integer currentValue) {
        this.sequenceDate = sequenceDate;
        this.currentValue = currentValue;
    }

    public String getSequenceDate() { return sequenceDate; }
    public void setSequenceDate(String sequenceDate) { this.sequenceDate = sequenceDate; }

    public Integer getCurrentValue() { return currentValue; }
    public void setCurrentValue(Integer currentValue) { this.currentValue = currentValue; }
}
