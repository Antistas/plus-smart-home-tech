package ru.yandex.practicum.telemetry.collector.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.telemetry.collector.model.hub.DeviceAddedEvent;
import ru.yandex.practicum.telemetry.collector.model.hub.DeviceRemovedEvent;
import ru.yandex.practicum.telemetry.collector.model.hub.HubEvent;
import ru.yandex.practicum.telemetry.collector.model.hub.ScenarioAddedEvent;
import ru.yandex.practicum.telemetry.collector.model.hub.ScenarioRemovedEvent;
import ru.yandex.practicum.telemetry.collector.model.sensor.ClimateSensorEvent;
import ru.yandex.practicum.telemetry.collector.model.sensor.LightSensorEvent;
import ru.yandex.practicum.telemetry.collector.model.sensor.MotionSensorEvent;
import ru.yandex.practicum.telemetry.collector.model.sensor.SensorEvent;
import ru.yandex.practicum.telemetry.collector.model.sensor.SwitchSensorEvent;
import ru.yandex.practicum.telemetry.collector.model.sensor.TemperatureSensorEvent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventDeserializationTest {
    private ObjectMapper objectMapper;
    private Validator validator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldDeserializeEverySensorEventSubtype() throws Exception {
        assertInstanceOf(ClimateSensorEvent.class, readSensor("""
                {"id":"climate-1","hubId":"hub-1","type":"CLIMATE_SENSOR_EVENT",
                 "temperatureC":21,"humidity":45,"co2Level":600}
                """));
        assertInstanceOf(LightSensorEvent.class, readSensor("""
                {"id":"light-1","hubId":"hub-1","type":"LIGHT_SENSOR_EVENT",
                 "linkQuality":80,"luminosity":50}
                """));
        assertInstanceOf(MotionSensorEvent.class, readSensor("""
                {"id":"motion-1","hubId":"hub-1","type":"MOTION_SENSOR_EVENT",
                 "linkQuality":90,"motion":true,"voltage":220}
                """));
        assertInstanceOf(SwitchSensorEvent.class, readSensor("""
                {"id":"switch-1","hubId":"hub-1","type":"SWITCH_SENSOR_EVENT","state":true}
                """));
        assertInstanceOf(TemperatureSensorEvent.class, readSensor("""
                {"id":"temp-1","hubId":"hub-1","type":"TEMPERATURE_SENSOR_EVENT",
                 "temperatureC":20,"temperatureF":68}
                """));
    }

    @Test
    void shouldDeserializeEveryHubEventSubtype() throws Exception {
        assertInstanceOf(DeviceAddedEvent.class, readHub("""
                {"hubId":"hub-1","type":"DEVICE_ADDED","id":"sensor-1","deviceType":"MOTION_SENSOR"}
                """));
        assertInstanceOf(DeviceRemovedEvent.class, readHub("""
                {"hubId":"hub-1","type":"DEVICE_REMOVED","id":"sensor-1"}
                """));
        assertInstanceOf(ScenarioAddedEvent.class, readHub("""
                {"hubId":"hub-1","type":"SCENARIO_ADDED","name":"Night light",
                 "conditions":[{"sensorId":"motion-1","type":"MOTION","operation":"EQUALS","value":1}],
                 "actions":[{"sensorId":"switch-1","type":"ACTIVATE"}]}
                """));
        assertInstanceOf(ScenarioRemovedEvent.class, readHub("""
                {"hubId":"hub-1","type":"SCENARIO_REMOVED","name":"Night light"}
                """));
    }

    @Test
    void shouldSetCurrentTimestampWhenItIsMissing() throws Exception {
        SensorEvent event = readSensor("""
                {"id":"light-1","hubId":"hub-1","type":"LIGHT_SENSOR_EVENT",
                 "linkQuality":80,"luminosity":50}
                """);

        assertNotNull(event.getTimestamp());
    }

    @Test
    void shouldRejectUnknownEventType() {
        String json = """
                {"id":"sensor-1","hubId":"hub-1","type":"UNKNOWN"}
                """;

        assertThrows(Exception.class, () -> readSensor(json));
    }

    @Test
    void shouldReportConstraintViolations() throws Exception {
        SensorEvent invalidSensor = readSensor("""
                {"id":"","hubId":"hub-1","type":"SWITCH_SENSOR_EVENT"}
                """);
        HubEvent invalidScenario = readHub("""
                {"hubId":"hub-1","type":"SCENARIO_ADDED","name":"ab",
                 "conditions":[],"actions":[]}
                """);

        assertFalse(validator.validate(invalidSensor).isEmpty());
        assertFalse(validator.validate(invalidScenario).isEmpty());
    }

    private SensorEvent readSensor(String json) throws Exception {
        return objectMapper.readValue(json, SensorEvent.class);
    }

    private HubEvent readHub(String json) throws Exception {
        return objectMapper.readValue(json, HubEvent.class);
    }
}
