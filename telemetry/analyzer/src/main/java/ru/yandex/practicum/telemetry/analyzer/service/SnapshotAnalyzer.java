package ru.yandex.practicum.telemetry.analyzer.service;

import com.google.protobuf.Timestamp;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.grpc.telemetry.event.*;
import ru.yandex.practicum.grpc.telemetry.hubrouter.HubRouterControllerGrpc;
import ru.yandex.practicum.kafka.telemetry.event.*;
import ru.yandex.practicum.telemetry.analyzer.model.Action;
import ru.yandex.practicum.telemetry.analyzer.model.Condition;
import ru.yandex.practicum.telemetry.analyzer.model.Scenario;
import ru.yandex.practicum.telemetry.analyzer.model.Sensor;
import ru.yandex.practicum.telemetry.analyzer.repository.ScenarioRepository;

import java.time.Instant;
import java.util.Map;

@Service
public class SnapshotAnalyzer {
    private final ScenarioRepository scenarios;
    private final HubRouterControllerGrpc.HubRouterControllerBlockingStub hubRouterClient;

    public SnapshotAnalyzer(ScenarioRepository scenarios, @GrpcClient("hub-router") HubRouterControllerGrpc.HubRouterControllerBlockingStub hubRouterClient) {
        this.scenarios = scenarios;
        this.hubRouterClient = hubRouterClient;
    }

    @Transactional(readOnly = true)
    public void analyze(SensorsSnapshotAvro snapshot) {
        scenarios.findByHubId(snapshot.getHubId()).stream()
                .filter(scenario -> matches(scenario, snapshot.getSensorsState()))
                .forEach(scenario -> scenario.getActions().forEach((sensor, action) -> execute(snapshot, scenario, sensor, action)));
    }

    boolean matches(Scenario scenario, Map<String, SensorStateAvro> states) {
        return scenario.getConditions().entrySet().stream().allMatch(entry -> {
            SensorStateAvro state = states.get(entry.getKey().getId());
            return state != null && test(entry.getValue(), state.getData());
        });
    }

    boolean test(Condition condition, Object data) {
        Integer actual = extract(condition.getType(), data);
        Integer expected = condition.getValue();
        if (actual == null || expected == null) return false;
        return switch (condition.getOperation()) {
            case EQUALS -> actual.equals(expected);
            case GREATER_THAN -> actual > expected;
            case LOWER_THAN -> actual < expected;
        };
    }

    private Integer extract(ConditionTypeAvro type, Object data) {
        return switch (type) {
            case MOTION -> data instanceof MotionSensorAvro d ? bool(d.getMotion()) : null;
            case LUMINOSITY -> data instanceof LightSensorAvro d ? d.getLuminosity() : null;
            case SWITCH -> data instanceof SwitchSensorAvro d ? bool(d.getState()) : null;
            case TEMPERATURE ->
                    data instanceof TemperatureSensorAvro d ? d.getTemperatureC() : data instanceof ClimateSensorAvro d ? d.getTemperatureC() : null;
            case CO2LEVEL -> data instanceof ClimateSensorAvro d ? d.getCo2Level() : null;
            case HUMIDITY -> data instanceof ClimateSensorAvro d ? d.getHumidity() : null;
        };
    }

    private int bool(boolean value) {
        return value ? 1 : 0;
    }

    private void execute(SensorsSnapshotAvro snapshot, Scenario scenario, Sensor sensor, Action action) {
        DeviceActionProto.Builder actionBuilder = DeviceActionProto.newBuilder().setSensorId(sensor.getId()).setType(ActionTypeProto.valueOf(action.getType().name()));
        if (action.getValue() != null) actionBuilder.setValue(action.getValue());
        Instant instant = snapshot.getTimestamp();
        DeviceActionRequest request = DeviceActionRequest.newBuilder()
                .setHubId(snapshot.getHubId()).setScenarioName(scenario.getName())
                .setAction(actionBuilder)
                .setTimestamp(Timestamp.newBuilder().setSeconds(instant.getEpochSecond())
                        .setNanos(instant.getNano())).build();
        hubRouterClient.handleDeviceAction(request);
    }
}
