package ru.yandex.practicum.telemetry.aggregator.service;

import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AggregationStarter {
    private static final Logger log = LoggerFactory.getLogger(AggregationStarter.class);

    private final Consumer<String, SensorEventAvro> consumer;
    private final Producer<String, SensorsSnapshotAvro> producer;
    private final SnapshotAggregator snapshotAggregator;
    private final String sensorEventsTopic;
    private final String snapshotsTopic;
    private final Duration pollTimeout;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public AggregationStarter(
            Consumer<String, SensorEventAvro> consumer,
            Producer<String, SensorsSnapshotAvro> producer,
            SnapshotAggregator snapshotAggregator,
            @Value("${aggregator.kafka.topics.sensors}") String sensorEventsTopic,
            @Value("${aggregator.kafka.topics.snapshots}") String snapshotsTopic,
            @Value("${aggregator.kafka.consumer.poll-timeout}") Duration pollTimeout
    ) {
        this.consumer = consumer;
        this.producer = producer;
        this.snapshotAggregator = snapshotAggregator;
        this.sensorEventsTopic = sensorEventsTopic;
        this.snapshotsTopic = snapshotsTopic;
        this.pollTimeout = pollTimeout;
    }

    public void start() {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(consumer::wakeup));
            consumer.subscribe(List.of(sensorEventsTopic));

            while (running.get()) {
                ConsumerRecords<String, SensorEventAvro> records = consumer.poll(pollTimeout);

                for (ConsumerRecord<String, SensorEventAvro> record : records) {
                    snapshotAggregator.updateState(record.value()).ifPresent(this::sendSnapshot);
                }

                if (!records.isEmpty()) {
                    producer.flush();
                    consumer.commitSync();
                }
            }
        } catch (WakeupException exception) {
            if (running.get()) {
                throw exception;
            }
        } catch (Exception exception) {
            log.error("Ошибка во время обработки событий от датчиков", exception);
        } finally {
            closeKafkaClients();
        }
    }

    private void sendSnapshot(SensorsSnapshotAvro snapshot) {
        ProducerRecord<String, SensorsSnapshotAvro> record = new ProducerRecord<>(
                snapshotsTopic,
                null,
                snapshot.getTimestamp().toEpochMilli(),
                snapshot.getHubId(),
                snapshot
        );
        producer.send(record);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        consumer.wakeup();
    }

    private void closeKafkaClients() {
        try {
            producer.flush();
            consumer.commitSync();
        } catch (Exception exception) {
            log.warn("Не удалось завершить отправку данных или зафиксировать смещения", exception);
        } finally {
            log.info("Закрываем консьюмер");
            consumer.close();
            log.info("Закрываем продюсер");
            producer.close();
        }
    }
}
