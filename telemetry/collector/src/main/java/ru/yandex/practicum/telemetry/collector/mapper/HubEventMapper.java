package ru.yandex.practicum.telemetry.collector.mapper;

import com.google.protobuf.Timestamp;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionProto;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto;
import ru.yandex.practicum.grpc.telemetry.event.ScenarioConditionProto;
import ru.yandex.practicum.kafka.telemetry.event.*;

import java.time.Instant;

@Component
public class HubEventMapper {
    public HubEventAvro mapToAvro(HubEventProto event) {
        return HubEventAvro.newBuilder()
                .setHubId(event.getHubId())
                .setTimestamp(toInstant(event.getTimestamp()))
                .setPayload(mapPayload(event))
                .build();
    }

    private Object mapPayload(HubEventProto event) {
        return switch (event.getPayloadCase()) {
            case DEVICE_ADDED -> DeviceAddedEventAvro.newBuilder()
                    .setId(event.getDeviceAdded().getId())
                    .setType(DeviceTypeAvro.valueOf(event.getDeviceAdded().getType().name())).build();
            case DEVICE_REMOVED -> DeviceRemovedEventAvro.newBuilder()
                    .setId(event.getDeviceRemoved().getId()).build();
            case SCENARIO_ADDED -> ScenarioAddedEventAvro.newBuilder()
                    .setName(event.getScenarioAdded().getName())
                    .setConditions(event.getScenarioAdded().getConditionList().stream()
                            .map(this::mapCondition).toList())
                    .setActions(event.getScenarioAdded().getActionList().stream()
                            .map(this::mapAction).toList()).build();
            case SCENARIO_REMOVED -> ScenarioRemovedEventAvro.newBuilder()
                    .setName(event.getScenarioRemoved().getName()).build();
            case PAYLOAD_NOT_SET -> throw new IllegalArgumentException("Hub event payload is not set");
        };
    }

    private ScenarioConditionAvro mapCondition(ScenarioConditionProto condition) {
        return ScenarioConditionAvro.newBuilder()
                .setSensorId(condition.getSensorId())
                .setType(ConditionTypeAvro.valueOf(condition.getType().name()))
                .setOperation(ConditionOperationAvro.valueOf(condition.getOperation().name()))
                .setValue(switch (condition.getValueCase()) {
                    case BOOL_VALUE -> condition.getBoolValue();
                    case INT_VALUE -> condition.getIntValue();
                    case VALUE_NOT_SET -> null;
                }).build();
    }

    private DeviceActionAvro mapAction(DeviceActionProto action) {
        return DeviceActionAvro.newBuilder()
                .setSensorId(action.getSensorId())
                .setType(ActionTypeAvro.valueOf(action.getType().name()))
                .setValue(action.hasValue() ? action.getValue() : null).build();
    }

    private Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}
