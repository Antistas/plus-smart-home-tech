package ru.yandex.practicum.telemetry.collector.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto;
import ru.yandex.practicum.grpc.telemetry.event.SensorEventProto;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.telemetry.collector.kafka.KafkaEventProducer;
import ru.yandex.practicum.telemetry.collector.mapper.HubEventMapper;
import ru.yandex.practicum.telemetry.collector.mapper.SensorEventMapper;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectorServiceTest {
    @Mock private SensorEventMapper sensorEventMapper;
    @Mock private HubEventMapper hubEventMapper;
    @Mock private KafkaEventProducer kafkaEventProducer;
    private CollectorService collectorService;

    @BeforeEach
    void setUp() {
        collectorService = new CollectorService(sensorEventMapper, hubEventMapper, kafkaEventProducer);
    }

    @Test
    void shouldMapAndSendSensorEvent() {
        SensorEventProto source = SensorEventProto.getDefaultInstance();
        SensorEventAvro mapped = mock(SensorEventAvro.class);
        when(sensorEventMapper.mapToAvro(source)).thenReturn(mapped);
        collectorService.collectSensorEvent(source);
        verify(kafkaEventProducer).sendSensorEvent(mapped);
    }

    @Test
    void shouldMapAndSendHubEvent() {
        HubEventProto source = HubEventProto.getDefaultInstance();
        HubEventAvro mapped = mock(HubEventAvro.class);
        when(hubEventMapper.mapToAvro(source)).thenReturn(mapped);
        collectorService.collectHubEvent(source);
        verify(kafkaEventProducer).sendHubEvent(mapped);
    }
}
