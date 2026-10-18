package ru.yandex.practicum.telemetry.collector.mapper;

import com.google.protobuf.Timestamp;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.grpc.telemetry.event.SensorEventProto;
import ru.yandex.practicum.kafka.telemetry.event.ClimateSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SwitchSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.TemperatureSensorAvro;

import java.time.Instant;

@Component
public class SensorEventMapper {
    public SensorEventAvro mapToAvro(SensorEventProto event) {
        return SensorEventAvro.newBuilder()
                .setId(event.getId())
                .setHubId(event.getHubId())
                .setTimestamp(toInstant(event.getTimestamp()))
                .setPayload(mapPayload(event))
                .build();
    }

    private Object mapPayload(SensorEventProto event) {
        return switch (event.getPayloadCase()) {
            case CLIMATE_SENSOR_EVENT -> ClimateSensorAvro.newBuilder()
                    .setTemperatureC(event.getClimateSensorEvent().getTemperatureC())
                    .setHumidity(event.getClimateSensorEvent().getHumidity())
                    .setCo2Level(event.getClimateSensorEvent().getCo2Level()).build();
            case LIGHT_SENSOR_EVENT -> LightSensorAvro.newBuilder()
                    .setLinkQuality(event.getLightSensorEvent().getLinkQuality())
                    .setLuminosity(event.getLightSensorEvent().getLuminosity()).build();
            case MOTION_SENSOR_EVENT -> MotionSensorAvro.newBuilder()
                    .setLinkQuality(event.getMotionSensorEvent().getLinkQuality())
                    .setMotion(event.getMotionSensorEvent().getMotion())
                    .setVoltage(event.getMotionSensorEvent().getVoltage()).build();
            case SWITCH_SENSOR_EVENT -> SwitchSensorAvro.newBuilder()
                    .setState(event.getSwitchSensorEvent().getState()).build();
            case TEMPERATURE_SENSOR_EVENT -> TemperatureSensorAvro.newBuilder()
                    .setTemperatureC(event.getTemperatureSensorEvent().getTemperatureC())
                    .setTemperatureF(event.getTemperatureSensorEvent().getTemperatureF()).build();
            case PAYLOAD_NOT_SET -> throw new IllegalArgumentException("Sensor event payload is not set");
        };
    }

    private Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}
