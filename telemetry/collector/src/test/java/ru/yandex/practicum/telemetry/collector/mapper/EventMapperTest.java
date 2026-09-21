package ru.yandex.practicum.telemetry.collector.mapper;

import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.grpc.telemetry.event.DeviceAddedEventProto;
import ru.yandex.practicum.grpc.telemetry.event.DeviceTypeProto;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto;
import ru.yandex.practicum.grpc.telemetry.event.MotionSensorProto;
import ru.yandex.practicum.grpc.telemetry.event.SensorEventProto;
import ru.yandex.practicum.kafka.telemetry.event.DeviceAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceTypeAvro;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventMapperTest {
    private final Timestamp timestamp = Timestamp.newBuilder().setSeconds(100).setNanos(20).build();

    @Test
    void shouldMapSensorProtoToAvro() {
        SensorEventProto source = SensorEventProto.newBuilder()
                .setId("motion-1").setHubId("hub-1").setTimestamp(timestamp)
                .setMotionSensorEvent(MotionSensorProto.newBuilder()
                        .setLinkQuality(90).setMotion(true).setVoltage(220)).build();

        var result = new SensorEventMapper().mapToAvro(source);

        assertThat(result.getTimestamp()).isEqualTo(Instant.ofEpochMilli(100_000));
        assertThat(result.getPayload()).isEqualTo(MotionSensorAvro.newBuilder()
                .setLinkQuality(90).setMotion(true).setVoltage(220).build());
    }

    @Test
    void shouldMapHubProtoToAvro() {
        HubEventProto source = HubEventProto.newBuilder()
                .setHubId("hub-1").setTimestamp(timestamp)
                .setDeviceAdded(DeviceAddedEventProto.newBuilder()
                        .setId("motion-1").setType(DeviceTypeProto.MOTION_SENSOR)).build();

        var result = new HubEventMapper().mapToAvro(source);
        var payload = (DeviceAddedEventAvro) result.getPayload();

        assertThat(payload.getId()).isEqualTo("motion-1");
        assertThat(payload.getType()).isEqualTo(DeviceTypeAvro.MOTION_SENSOR);
    }
}
