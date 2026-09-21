package ru.yandex.practicum.telemetry.collector.service;

import org.springframework.stereotype.Service;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto;
import ru.yandex.practicum.grpc.telemetry.event.SensorEventProto;
import ru.yandex.practicum.telemetry.collector.kafka.KafkaEventProducer;
import ru.yandex.practicum.telemetry.collector.mapper.HubEventMapper;
import ru.yandex.practicum.telemetry.collector.mapper.SensorEventMapper;

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

    public void collectSensorEvent(SensorEventProto event) {
        kafkaEventProducer.sendSensorEvent(sensorEventMapper.mapToAvro(event));
    }

    public void collectHubEvent(HubEventProto event) {
        kafkaEventProducer.sendHubEvent(hubEventMapper.mapToAvro(event));
    }
}
