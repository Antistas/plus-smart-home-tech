package ru.yandex.practicum.telemetry.analyzer.service;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.kafka.telemetry.event.*;
import ru.yandex.practicum.telemetry.analyzer.model.Condition;
import ru.yandex.practicum.telemetry.analyzer.model.Scenario;
import ru.yandex.practicum.telemetry.analyzer.model.Sensor;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotAnalyzerTest {
    private final SnapshotAnalyzer analyzer = new SnapshotAnalyzer(null, null);

    @Test
    void matchesScenarioWhenAllConditionsAreTrue() {
        Sensor temperature = new Sensor("temperature-1", "hub-1");
        Sensor motion = new Sensor("motion-1", "hub-1");
        Scenario scenario = new Scenario("hub-1", "warm floor");
        scenario.replaceConditions(Map.of(
                temperature, new Condition(ConditionTypeAvro.TEMPERATURE, ConditionOperationAvro.LOWER_THAN, 15),
                motion, new Condition(ConditionTypeAvro.MOTION, ConditionOperationAvro.EQUALS, 1)
        ));

        Map<String, SensorStateAvro> states = Map.of(
                "temperature-1", state(TemperatureSensorAvro.newBuilder().setTemperatureC(10).setTemperatureF(50).build()),
                "motion-1", state(MotionSensorAvro.newBuilder().setLinkQuality(80).setMotion(true).setVoltage(220).build())
        );

        assertThat(analyzer.matches(scenario, states)).isTrue();
    }

    @Test
    void doesNotMatchWhenSensorIsMissingOrValueFails() {
        Sensor sensor = new Sensor("light-1", "hub-1");
        Scenario scenario = new Scenario("hub-1", "turn light off");
        scenario.replaceConditions(Map.of(sensor,
                new Condition(ConditionTypeAvro.LUMINOSITY, ConditionOperationAvro.GREATER_THAN, 100)));

        assertThat(analyzer.matches(scenario, Map.of())).isFalse();
        assertThat(analyzer.matches(scenario, Map.of("light-1",
                state(LightSensorAvro.newBuilder().setLinkQuality(90).setLuminosity(50).build())))).isFalse();
    }

    private SensorStateAvro state(Object data) {
        return SensorStateAvro.newBuilder().setTimestamp(Instant.now()).setData(data).build();
    }
}
