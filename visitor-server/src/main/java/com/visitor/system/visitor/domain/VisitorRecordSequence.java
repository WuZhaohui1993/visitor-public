package com.visitor.system.visitor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "visitor_record_sequence")
public class VisitorRecordSequence {

    @Id
    @Column(name = "sequence_date", nullable = false, length = 8)
    private String sequenceDate;

    @Column(name = "current_value", nullable = false)
    private Long currentValue;
}
