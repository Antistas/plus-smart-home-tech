package ru.yandex.practicum.telemetry.analyzer.processor;

import jakarta.annotation.PreDestroy;
import io.grpc.StatusRuntimeException;
import io.grpc.Status;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.analyzer.service.SnapshotAnalyzer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SnapshotProcessor {
    private static final Logger log = LoggerFactory.getLogger(SnapshotProcessor.class);
    private final Consumer<String, SensorsSnapshotAvro> consumer;
    private final SnapshotAnalyzer analyzer;
    private final String topic;
    private final Duration timeout;
    private final Duration retryBackoff;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public SnapshotProcessor(@Qualifier("snapshotConsumer") Consumer<String, SensorsSnapshotAvro> consumer, SnapshotAnalyzer analyzer,
                             @Value("${analyzer.kafka.topics.snapshots}") String topic,
                             @Value("${analyzer.kafka.consumers.snapshots.poll-timeout}") Duration timeout,
                             @Value("${analyzer.kafka.consumers.snapshots.retry-backoff:1s}") Duration retryBackoff) {
        this.consumer = consumer;
        this.analyzer = analyzer;
        this.topic = topic;
        this.timeout = timeout;
        this.retryBackoff = retryBackoff;
    }

    public void start() {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(consumer::wakeup));

            consumer.subscribe(List.of(topic));

            while (running.get()) {
                ConsumerRecords<String, SensorsSnapshotAvro> records = consumer.poll(timeout);
                for (ConsumerRecord<String, SensorsSnapshotAvro> record : records) {
                    try {
                        analyzer.analyze(record.value());
                        consumer.commitSync();
                    } catch (StatusRuntimeException e) {
                        if (e.getStatus().getCode() != Status.Code.UNAVAILABLE
                                && e.getStatus().getCode() != Status.Code.DEADLINE_EXCEEDED) {
                            throw e;
                        }

                        log.warn("Hub Router недоступен, повторим снапшот hubId={}, offset={}",
                                record.key(), record.offset());

                        consumer.seek(new TopicPartition(record.topic(), record.partition()), record.offset());

                        try {
                            Thread.sleep(retryBackoff.toMillis());
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            running.set(false);
                        }
                        break;
                    }
                }
            }
        } catch (WakeupException e) {
            if (running.get()) throw e;
        } catch (Exception e) {
            log.error("Ошибка обработки снапшотов", e);
        } finally {
            consumer.close();
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        consumer.wakeup();
    }
}
