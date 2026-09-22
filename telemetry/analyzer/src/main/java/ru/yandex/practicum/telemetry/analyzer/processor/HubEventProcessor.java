package ru.yandex.practicum.telemetry.analyzer.processor;

import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.telemetry.analyzer.service.HubEventService;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class HubEventProcessor implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(HubEventProcessor.class);
    private final Consumer<String, HubEventAvro> consumer;
    private final HubEventService service;
    private final String topic;
    private final Duration timeout;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public HubEventProcessor(@Qualifier("hubEventConsumer") Consumer<String, HubEventAvro> consumer, HubEventService service,
                             @Value("${analyzer.kafka.topics.hubs}") String topic, @Value("${analyzer.kafka.consumers.hubs.poll-timeout}") Duration timeout) {
        this.consumer = consumer;
        this.service = service;
        this.topic = topic;
        this.timeout = timeout;
    }

    @Override
    public void run() {
        try {
            consumer.subscribe(List.of(topic));
            while (running.get()) {
                ConsumerRecords<String, HubEventAvro> records = consumer.poll(timeout);
                for (ConsumerRecord<String, HubEventAvro> record : records) service.handle(record.value());
                if (!records.isEmpty()) consumer.commitSync();
            }
        } catch (WakeupException e) {
            if (running.get()) throw e;
        } catch (Exception e) {
            log.error("Ошибка обработки событий хабов", e);
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
