package ru.yandex.practicum.telemetry.collector.kafka;

import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SwitchSensorAvro;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class AvroSerializerTest {
    private final AvroSerializer serializer = new AvroSerializer();

    @Test
    void shouldSerializeSpecificRecordToAvroBinary() throws Exception {
        SensorEventAvro source = SensorEventAvro.newBuilder()
                .setId("switch-1")
                .setHubId("hub-1")
                .setTimestamp(Instant.parse("2026-09-03T10:15:30Z"))
                .setPayload(SwitchSensorAvro.newBuilder().setState(true).build())
                .build();

        byte[] bytes = serializer.serialize("telemetry.sensors.v1", source);
        SpecificDatumReader<SensorEventAvro> reader = new SpecificDatumReader<>(SensorEventAvro.getClassSchema());
        SensorEventAvro restored = reader.read(null, DecoderFactory.get().binaryDecoder(bytes, null));

        assertEquals(source.getId(), restored.getId());
        assertEquals(source.getHubId(), restored.getHubId());
        assertEquals(source.getTimestamp(), restored.getTimestamp());
        assertEquals(true, assertInstanceOf(SwitchSensorAvro.class, restored.getPayload()).getState());
    }

    @Test
    void shouldReturnNullForNullValue() {
        assertNull(serializer.serialize("telemetry.sensors.v1", null));
    }
}
