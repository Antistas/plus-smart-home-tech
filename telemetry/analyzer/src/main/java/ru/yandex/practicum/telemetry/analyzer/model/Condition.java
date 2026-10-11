package ru.yandex.practicum.telemetry.analyzer.model;

import jakarta.persistence.*;
import ru.yandex.practicum.kafka.telemetry.event.ConditionOperationAvro;
import ru.yandex.practicum.kafka.telemetry.event.ConditionTypeAvro;

@Entity
@Table(name = "conditions")
public class Condition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConditionTypeAvro type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConditionOperationAvro operation;

    private Integer value;

    public Condition() {
    }

    public Condition(ConditionTypeAvro type, ConditionOperationAvro operation, Integer value) {
        this.type = type;
        this.operation = operation;
        this.value = value;
    }

    public Long getId() {
        return id;
    }

    public ConditionTypeAvro getType() {
        return type;
    }

    public ConditionOperationAvro getOperation() {
        return operation;
    }

    public Integer getValue() {
        return value;
    }
}
