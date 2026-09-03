package ru.yandex.practicum.telemetry.collector.kafka;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.yandex.practicum.kafka.telemetry.event.DeviceRemovedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SwitchSensorAvro;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KafkaEventProducerTest {
    private static final String SENSORS_TOPIC = "telemetry.sensors.v1";
    private static final String HUBS_TOPIC = "telemetry.hubs.v1";
    private static final Instant TIMESTAMP = Instant.parse("2026-09-03T10:15:30Z");

    @Mock
    private Producer<String, SpecificRecordBase> producer;

    private KafkaEventProducer eventProducer;

    @BeforeEach
    void setUp() {
        eventProducer = new KafkaEventProducer(producer, SENSORS_TOPIC, HUBS_TOPIC);
    }

    @Test
    void shouldSendSensorEventToSensorsTopicUsingHubAsKey() {
        SensorEventAvro event = SensorEventAvro.newBuilder()
                .setId("switch-1")
                .setHubId("hub-1")
                .setTimestamp(TIMESTAMP)
                .setPayload(SwitchSensorAvro.newBuilder().setState(true).build())
                .build();

        eventProducer.sendSensorEvent(event);

        ProducerRecord<String, SpecificRecordBase> record = captureRecord();
        assertRecord(record, SENSORS_TOPIC, event);
    }

    @Test
    void shouldSendHubEventToHubsTopicUsingHubAsKey() {
        HubEventAvro event = HubEventAvro.newBuilder()
                .setHubId("hub-1")
                .setTimestamp(TIMESTAMP)
                .setPayload(DeviceRemovedEventAvro.newBuilder().setId("sensor-1").build())
                .build();

        eventProducer.sendHubEvent(event);

        ProducerRecord<String, SpecificRecordBase> record = captureRecord();
        assertRecord(record, HUBS_TOPIC, event);
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, SpecificRecordBase> captureRecord() {
        ArgumentCaptor<ProducerRecord<String, SpecificRecordBase>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer).send(captor.capture(), any(Callback.class));
        return captor.getValue();
    }

    private void assertRecord(ProducerRecord<String, SpecificRecordBase> record,
                              String expectedTopic,
                              SpecificRecordBase expectedValue) {
        assertEquals(expectedTopic, record.topic());
        assertEquals("hub-1", record.key());
        assertEquals(TIMESTAMP.toEpochMilli(), record.timestamp());
        assertSame(expectedValue, record.value());
    }
}
