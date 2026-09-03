package ru.yandex.practicum.telemetry.collector.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.kafka.telemetry.event.ClimateSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceRemovedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceTypeAvro;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioRemovedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SwitchSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.TemperatureSensorAvro;
import ru.yandex.practicum.telemetry.collector.model.hub.HubEvent;
import ru.yandex.practicum.telemetry.collector.model.sensor.SensorEvent;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class EventMapperTest {
    private ObjectMapper objectMapper;
    private SensorEventMapper sensorMapper;
    private HubEventMapper hubMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        sensorMapper = new SensorEventMapper();
        hubMapper = new HubEventMapper();
    }

    @Test
    void shouldMapEverySensorPayload() throws Exception {
        assertInstanceOf(ClimateSensorAvro.class, mapSensor("""
                {"id":"climate-1","hubId":"hub-1","type":"CLIMATE_SENSOR_EVENT",
                 "temperatureC":21,"humidity":45,"co2Level":600}
                """).getPayload());
        assertInstanceOf(LightSensorAvro.class, mapSensor("""
                {"id":"light-1","hubId":"hub-1","type":"LIGHT_SENSOR_EVENT",
                 "linkQuality":80,"luminosity":50}
                """).getPayload());
        assertInstanceOf(MotionSensorAvro.class, mapSensor("""
                {"id":"motion-1","hubId":"hub-1","type":"MOTION_SENSOR_EVENT",
                 "linkQuality":90,"motion":true,"voltage":220}
                """).getPayload());
        assertInstanceOf(SwitchSensorAvro.class, mapSensor("""
                {"id":"switch-1","hubId":"hub-1","type":"SWITCH_SENSOR_EVENT","state":true}
                """).getPayload());

        SensorEventAvro temperature = mapSensor("""
                {"id":"temp-1","hubId":"hub-1","timestamp":"2026-09-03T10:15:30Z",
                 "type":"TEMPERATURE_SENSOR_EVENT","temperatureC":20,"temperatureF":68}
                """);
        TemperatureSensorAvro payload = assertInstanceOf(TemperatureSensorAvro.class, temperature.getPayload());
        assertAll(
                () -> assertEquals("temp-1", temperature.getId()),
                () -> assertEquals("hub-1", temperature.getHubId()),
                () -> assertEquals(Instant.parse("2026-09-03T10:15:30Z"), temperature.getTimestamp()),
                () -> assertEquals(20, payload.getTemperatureC()),
                () -> assertEquals(68, payload.getTemperatureF())
        );
    }

    @Test
    void shouldMapEveryHubPayloadIncludingNestedScenarioData() throws Exception {
        HubEventAvro deviceAdded = mapHub("""
                {"hubId":"hub-1","type":"DEVICE_ADDED","id":"sensor-1","deviceType":"MOTION_SENSOR"}
                """);
        DeviceAddedEventAvro addedPayload = assertInstanceOf(DeviceAddedEventAvro.class,
                deviceAdded.getPayload());
        assertEquals(DeviceTypeAvro.MOTION_SENSOR, addedPayload.getType());

        assertInstanceOf(DeviceRemovedEventAvro.class, mapHub("""
                {"hubId":"hub-1","type":"DEVICE_REMOVED","id":"sensor-1"}
                """).getPayload());
        assertInstanceOf(ScenarioRemovedEventAvro.class, mapHub("""
                {"hubId":"hub-1","type":"SCENARIO_REMOVED","name":"Night light"}
                """).getPayload());

        HubEventAvro scenario = mapHub("""
                {"hubId":"hub-1","timestamp":"2026-09-03T10:15:30Z","type":"SCENARIO_ADDED",
                 "name":"Night light",
                 "conditions":[{"sensorId":"motion-1","type":"MOTION","operation":"EQUALS","value":1}],
                 "actions":[{"sensorId":"switch-1","type":"ACTIVATE"}]}
                """);
        ScenarioAddedEventAvro scenarioPayload = assertInstanceOf(ScenarioAddedEventAvro.class,
                scenario.getPayload());
        assertAll(
                () -> assertEquals("hub-1", scenario.getHubId()),
                () -> assertEquals(Instant.parse("2026-09-03T10:15:30Z"), scenario.getTimestamp()),
                () -> assertEquals("Night light", scenarioPayload.getName()),
                () -> assertEquals("motion-1", scenarioPayload.getConditions().getFirst().getSensorId()),
                () -> assertEquals(1, scenarioPayload.getConditions().getFirst().getValue()),
                () -> assertEquals("switch-1", scenarioPayload.getActions().getFirst().getSensorId()),
                () -> assertNull(scenarioPayload.getActions().getFirst().getValue())
        );
    }

    private SensorEventAvro mapSensor(String json) throws Exception {
        return sensorMapper.mapToAvro(objectMapper.readValue(json, SensorEvent.class));
    }

    private HubEventAvro mapHub(String json) throws Exception {
        return hubMapper.mapToAvro(objectMapper.readValue(json, HubEvent.class));
    }
}
