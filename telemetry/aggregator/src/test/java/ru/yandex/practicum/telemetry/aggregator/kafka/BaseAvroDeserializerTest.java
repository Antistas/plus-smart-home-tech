package ru.yandex.practicum.telemetry.aggregator.kafka;

import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SwitchSensorAvro;

import java.io.ByteArrayOutputStream;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BaseAvroDeserializerTest {

    private final BaseAvroDeserializer<SensorEventAvro> deserializer =
            new BaseAvroDeserializer<>(SensorEventAvro.getClassSchema());

    @Test
    void shouldDeserializeSensorEvent() throws Exception {
        SensorEventAvro expected = SensorEventAvro.newBuilder()
                .setId("switch-1")
                .setHubId("hub-1")
                .setTimestamp(Instant.parse("2026-09-21T10:00:00Z"))
                .setPayload(SwitchSensorAvro.newBuilder().setState(true).build())
                .build();

        SensorEventAvro actual = deserializer.deserialize("telemetry.sensors.v1", serialize(expected));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void shouldReturnNullForNullData() {
        assertThat(deserializer.deserialize("telemetry.sensors.v1", null)).isNull();
    }

    @Test
    void sensorEventDeserializerShouldUseSensorEventSchema() throws Exception {
        SensorEventAvro expected = SensorEventAvro.newBuilder()
                .setId("switch-2")
                .setHubId("hub-2")
                .setTimestamp(Instant.parse("2026-09-21T11:00:00Z"))
                .setPayload(SwitchSensorAvro.newBuilder().setState(false).build())
                .build();

        SensorEventAvro actual = new SensorEventDeserializer()
                .deserialize("telemetry.sensors.v1", serialize(expected));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void shouldWrapInvalidDataInSerializationException() {
        assertThatThrownBy(() -> deserializer.deserialize("telemetry.sensors.v1", new byte[]{1}))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("telemetry.sensors.v1");
    }

    private byte[] serialize(SensorEventAvro event) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
            new SpecificDatumWriter<SensorEventAvro>(SensorEventAvro.getClassSchema()).write(event, encoder);
            encoder.flush();
            return outputStream.toByteArray();
        }
    }
}
