package ru.yandex.practicum.telemetry.analyzer.config;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.telemetry.analyzer.kafka.HubEventDeserializer;
import ru.yandex.practicum.telemetry.analyzer.kafka.SnapshotDeserializer;
import java.util.HashMap;
import java.util.Map;
@Configuration
public class KafkaConfig {
    @Bean(destroyMethod = "") @Qualifier("hubEventConsumer")
    Consumer<String, HubEventAvro> hubEventConsumer(@Value("${analyzer.kafka.bootstrap-servers}") String servers, @Value("${analyzer.kafka.consumers.hubs.group-id}") String group) {
        return new KafkaConsumer<>(properties(servers, group, HubEventDeserializer.class));
    }
    @Bean(destroyMethod = "") @Qualifier("snapshotConsumer")
    Consumer<String, SensorsSnapshotAvro> snapshotConsumer(@Value("${analyzer.kafka.bootstrap-servers}") String servers,
                                                           @Value("${analyzer.kafka.consumers.snapshots.group-id}") String group,
                                                           @Value("${analyzer.kafka.consumers.snapshots.auto-offset-reset:earliest}") String autoOffsetReset,
                                                           @Value("${analyzer.kafka.consumers.snapshots.max-poll-records:1}") int maxPollRecords) {
        Map<String, Object> configuration = properties(servers, group, SnapshotDeserializer.class);
        configuration.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        configuration.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        return new KafkaConsumer<>(configuration);
    }
    private Map<String, Object> properties(String servers, String group, Class<?> deserializer) {
        Map<String, Object> result = new HashMap<>();
        result.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
        result.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        result.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        result.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
        result.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        result.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return result;
    }
}
