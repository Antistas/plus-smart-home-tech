package ru.yandex.practicum.telemetry.analyzer.model;
import jakarta.persistence.*;
@Entity @Table(name = "sensors")
public class Sensor {
    @Id private String id;
    @Column(name = "hub_id", nullable = false) private String hubId;
    public Sensor() {} public Sensor(String id, String hubId) { this.id = id; this.hubId = hubId; }
    public String getId() { return id; } public String getHubId() { return hubId; }
}
