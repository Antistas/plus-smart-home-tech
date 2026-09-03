package ru.yandex.practicum.telemetry.collector.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.telemetry.collector.kafka.KafkaEventProducer;
import ru.yandex.practicum.telemetry.collector.mapper.HubEventMapper;
import ru.yandex.practicum.telemetry.collector.mapper.SensorEventMapper;
import ru.yandex.practicum.telemetry.collector.model.hub.DeviceRemovedEvent;
import ru.yandex.practicum.telemetry.collector.model.sensor.SwitchSensorEvent;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class CollectorServiceTest {
    @Mock
    private SensorEventMapper sensorEventMapper;
    @Mock
    private HubEventMapper hubEventMapper;
    @Mock
    private KafkaEventProducer kafkaEventProducer;

    private CollectorService collectorService;

    @BeforeEach
    void setUp() {
        collectorService = new CollectorService(sensorEventMapper, hubEventMapper, kafkaEventProducer);
    }

    @Test
    void shouldMapAndSendSensorEvent() {
        SwitchSensorEvent source = new SwitchSensorEvent();
        SensorEventAvro mapped = mock(SensorEventAvro.class);
        when(sensorEventMapper.mapToAvro(source)).thenReturn(mapped);

        collectorService.collectSensorEvent(source);

        verify(kafkaEventProducer).sendSensorEvent(mapped);
    }

    @Test
    void shouldMapAndSendHubEvent() {
        DeviceRemovedEvent source = new DeviceRemovedEvent();
        HubEventAvro mapped = mock(HubEventAvro.class);
        when(hubEventMapper.mapToAvro(source)).thenReturn(mapped);

        collectorService.collectHubEvent(source);

        verify(kafkaEventProducer).sendHubEvent(mapped);
    }
}
