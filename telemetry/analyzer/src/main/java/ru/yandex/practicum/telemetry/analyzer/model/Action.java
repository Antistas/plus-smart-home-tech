package ru.yandex.practicum.telemetry.analyzer.model;

import jakarta.persistence.*;
import ru.yandex.practicum.kafka.telemetry.event.ActionTypeAvro;

@Entity
@Table(name = "actions")
public class Action {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionTypeAvro type;

    private Integer value;

    public Action() {
    }

    public Action(ActionTypeAvro type, Integer value) {
        this.type = type;
        this.value = value;
    }

    public Long getId() {
        return id;
    }

    public ActionTypeAvro getType() {
        return type;
    }

    public Integer getValue() {
        return value;
    }
}
