package ru.yandex.practicum.telemetry.collector.service;

import org.springframework.stereotype.Service;
import ru.yandex.practicum.telemetry.collector.kafka.KafkaEventProducer;
import ru.yandex.practicum.telemetry.collector.mapper.HubEventMapper;
import ru.yandex.practicum.telemetry.collector.mapper.SensorEventMapper;
import ru.yandex.practicum.telemetry.collector.model.hub.HubEvent;
import ru.yandex.practicum.telemetry.collector.model.sensor.SensorEvent;

@Service
public class CollectorService {
    private final SensorEventMapper sensorEventMapper;
    private final HubEventMapper hubEventMapper;
    private final KafkaEventProducer kafkaEventProducer;

    public CollectorService(SensorEventMapper sensorEventMapper,
                            HubEventMapper hubEventMapper,
                            KafkaEventProducer kafkaEventProducer) {
        this.sensorEventMapper = sensorEventMapper;
        this.hubEventMapper = hubEventMapper;
        this.kafkaEventProducer = kafkaEventProducer;
    }

    public void collectSensorEvent(SensorEvent event) {
        kafkaEventProducer.sendSensorEvent(sensorEventMapper.mapToAvro(event));
    }

    public void collectHubEvent(HubEvent event) {
        kafkaEventProducer.sendHubEvent(hubEventMapper.mapToAvro(event));
    }
}
