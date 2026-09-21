package ru.yandex.practicum.telemetry.aggregator.service;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.kafka.telemetry.event.SwitchSensorAvro;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotAggregatorTest {
    private static final Instant FIRST_TIMESTAMP = Instant.parse("2026-09-21T10:00:00Z");
    private static final Instant SECOND_TIMESTAMP = Instant.parse("2026-09-21T10:01:00Z");

    private final SnapshotAggregator aggregator = new SnapshotAggregator();

    @Test
    void shouldCreateSnapshotForFirstHubEvent() {
        SensorEventAvro event = event("hub-1", "switch-1", FIRST_TIMESTAMP, true);

        SensorsSnapshotAvro snapshot = aggregator.updateState(event).orElseThrow();

        assertThat(snapshot.getHubId()).isEqualTo("hub-1");
        assertThat(snapshot.getTimestamp()).isEqualTo(FIRST_TIMESTAMP);
        assertThat(snapshot.getSensorsState()).containsOnlyKeys("switch-1");
        assertThat(snapshot.getSensorsState().get("switch-1").getData()).isEqualTo(event.getPayload());
    }

    @Test
    void shouldAddAnotherSensorToExistingSnapshot() {
        aggregator.updateState(event("hub-1", "switch-1", FIRST_TIMESTAMP, true));

        SensorsSnapshotAvro snapshot = aggregator
                .updateState(event("hub-1", "switch-2", SECOND_TIMESTAMP, false))
                .orElseThrow();

        assertThat(snapshot.getSensorsState()).containsOnlyKeys("switch-1", "switch-2");
        assertThat(snapshot.getTimestamp()).isEqualTo(SECOND_TIMESTAMP);
    }

    @Test
    void shouldUpdateChangedStateFromNewerEvent() {
        aggregator.updateState(event("hub-1", "switch-1", FIRST_TIMESTAMP, false));

        SensorsSnapshotAvro snapshot = aggregator
                .updateState(event("hub-1", "switch-1", SECOND_TIMESTAMP, true))
                .orElseThrow();

        SwitchSensorAvro state = (SwitchSensorAvro) snapshot.getSensorsState().get("switch-1").getData();
        assertThat(state.getState()).isTrue();
        assertThat(snapshot.getTimestamp()).isEqualTo(SECOND_TIMESTAMP);
    }

    @Test
    void shouldIgnoreOlderEvent() {
        aggregator.updateState(event("hub-1", "switch-1", SECOND_TIMESTAMP, false));

        Optional<SensorsSnapshotAvro> result =
                aggregator.updateState(event("hub-1", "switch-1", FIRST_TIMESTAMP, true));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldIgnoreNewerEventWithUnchangedData() {
        aggregator.updateState(event("hub-1", "switch-1", FIRST_TIMESTAMP, true));

        Optional<SensorsSnapshotAvro> result =
                aggregator.updateState(event("hub-1", "switch-1", SECOND_TIMESTAMP, true));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldMaintainIndependentSnapshotsForDifferentHubs() {
        SensorsSnapshotAvro first = aggregator
                .updateState(event("hub-1", "switch-1", FIRST_TIMESTAMP, true))
                .orElseThrow();
        SensorsSnapshotAvro second = aggregator
                .updateState(event("hub-2", "switch-2", SECOND_TIMESTAMP, false))
                .orElseThrow();

        assertThat(first.getHubId()).isEqualTo("hub-1");
        assertThat(second.getHubId()).isEqualTo("hub-2");
        assertThat(first.getSensorsState()).containsOnlyKeys("switch-1");
        assertThat(second.getSensorsState()).containsOnlyKeys("switch-2");
    }

    private SensorEventAvro event(String hubId, String sensorId, Instant timestamp, boolean state) {
        return SensorEventAvro.newBuilder()
                .setHubId(hubId)
                .setId(sensorId)
                .setTimestamp(timestamp)
                .setPayload(SwitchSensorAvro.newBuilder().setState(state).build())
                .build();
    }
}
