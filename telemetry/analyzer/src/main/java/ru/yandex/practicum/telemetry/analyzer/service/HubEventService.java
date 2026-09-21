package ru.yandex.practicum.telemetry.analyzer.service;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.kafka.telemetry.event.*;
import ru.yandex.practicum.telemetry.analyzer.model.*;
import ru.yandex.practicum.telemetry.analyzer.repository.*;
import java.util.*;
import java.util.stream.Collectors;
@Service
public class HubEventService {
    private final SensorRepository sensors; private final ScenarioRepository scenarios;
    private final ConditionRepository conditions; private final ActionRepository actions;
    public HubEventService(SensorRepository sensors, ScenarioRepository scenarios, ConditionRepository conditions, ActionRepository actions) {
        this.sensors = sensors; this.scenarios = scenarios; this.conditions = conditions; this.actions = actions;
    }
    @Transactional
    public void handle(HubEventAvro event) {
        Object payload = event.getPayload(); String hubId = event.getHubId();
        if (payload instanceof DeviceAddedEventAvro added) addSensor(hubId, added);
        else if (payload instanceof DeviceRemovedEventAvro removed) removeSensor(hubId, removed.getId());
        else if (payload instanceof ScenarioAddedEventAvro added) saveScenario(hubId, added);
        else if (payload instanceof ScenarioRemovedEventAvro removed) scenarios.findByHubIdAndName(hubId, removed.getName()).ifPresent(scenarios::delete);
        else throw new IllegalArgumentException("Неизвестный тип события хаба: " + payload.getClass());
    }
    private void addSensor(String hubId, DeviceAddedEventAvro event) {
        sensors.findById(event.getId()).ifPresentOrElse(sensor -> {
            if (!sensor.getHubId().equals(hubId)) throw new IllegalArgumentException("Датчик уже зарегистрирован в другом хабе: " + event.getId());
        }, () -> sensors.save(new Sensor(event.getId(), hubId)));
    }
    private void removeSensor(String hubId, String sensorId) {
        sensors.findByIdAndHubId(sensorId, hubId).ifPresent(sensor -> {
            List<Scenario> affected = scenarios.findByHubId(hubId).stream()
                    .filter(s -> s.getConditions().containsKey(sensor) || s.getActions().containsKey(sensor)).toList();
            List<Condition> obsoleteConditions = affected.stream().map(s -> s.getConditions().get(sensor)).filter(Objects::nonNull).toList();
            List<Action> obsoleteActions = affected.stream().map(s -> s.getActions().get(sensor)).filter(Objects::nonNull).toList();
            affected.forEach(s -> s.removeSensor(sensor));
            scenarios.saveAllAndFlush(affected);
            conditions.deleteAll(obsoleteConditions); actions.deleteAll(obsoleteActions);
            sensors.delete(sensor);
        });
    }
    private void saveScenario(String hubId, ScenarioAddedEventAvro event) {
        Set<String> ids = new HashSet<>();
        event.getConditions().forEach(c -> ids.add(c.getSensorId())); event.getActions().forEach(a -> ids.add(a.getSensorId()));
        Map<String, Sensor> sensorMap = sensors.findAllById(ids).stream().filter(s -> s.getHubId().equals(hubId)).collect(Collectors.toMap(Sensor::getId, s -> s));
        if (sensorMap.size() != ids.size()) throw new IllegalArgumentException("Не все устройства сценария зарегистрированы в хабе " + hubId);
        Scenario scenario = scenarios.findByHubIdAndName(hubId, event.getName()).orElseGet(() -> new Scenario(hubId, event.getName()));
        List<Condition> obsoleteConditions = new ArrayList<>(scenario.getConditions().values());
        List<Action> obsoleteActions = new ArrayList<>(scenario.getActions().values());
        Map<Sensor, Condition> conditions = new LinkedHashMap<>();
        event.getConditions().forEach(c -> conditions.put(sensorMap.get(c.getSensorId()), new Condition(c.getType(), c.getOperation(), conditionValue(c.getValue()))));
        Map<Sensor, Action> actions = new LinkedHashMap<>();
        event.getActions().forEach(a -> actions.put(sensorMap.get(a.getSensorId()), new Action(a.getType(), a.getValue())));
        scenario.replaceConditions(conditions); scenario.replaceActions(actions); scenarios.saveAndFlush(scenario);
        this.conditions.deleteAll(obsoleteConditions); this.actions.deleteAll(obsoleteActions);
    }
    private Integer conditionValue(Object value) { return value == null ? null : value instanceof Boolean b ? (b ? 1 : 0) : (Integer) value; }
}
