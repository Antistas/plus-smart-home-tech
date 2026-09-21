package ru.yandex.practicum.telemetry.analyzer.model;
import jakarta.persistence.*;
import java.util.LinkedHashMap;
import java.util.Map;
@Entity @Table(name = "scenarios", uniqueConstraints = @UniqueConstraint(columnNames = {"hub_id", "name"}))
public class Scenario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "hub_id", nullable = false) private String hubId;
    @Column(nullable = false) private String name;
    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "scenario_conditions", joinColumns = @JoinColumn(name = "scenario_id"), inverseJoinColumns = @JoinColumn(name = "condition_id"))
    @MapKeyJoinColumn(name = "sensor_id")
    private Map<Sensor, Condition> conditions = new LinkedHashMap<>();
    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "scenario_actions", joinColumns = @JoinColumn(name = "scenario_id"), inverseJoinColumns = @JoinColumn(name = "action_id"))
    @MapKeyJoinColumn(name = "sensor_id")
    private Map<Sensor, Action> actions = new LinkedHashMap<>();
    public Scenario() {} public Scenario(String hubId, String name) { this.hubId = hubId; this.name = name; }
    public Long getId() { return id; } public String getHubId() { return hubId; } public String getName() { return name; }
    public Map<Sensor, Condition> getConditions() { return conditions; } public Map<Sensor, Action> getActions() { return actions; }
    public void replaceConditions(Map<Sensor, Condition> value) { conditions.clear(); conditions.putAll(value); }
    public void replaceActions(Map<Sensor, Action> value) { actions.clear(); actions.putAll(value); }
    public void removeSensor(Sensor sensor) { conditions.remove(sensor); actions.remove(sensor); }
}
