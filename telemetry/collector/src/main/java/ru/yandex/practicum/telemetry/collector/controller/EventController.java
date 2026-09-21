package ru.yandex.practicum.telemetry.collector.controller;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.yandex.practicum.grpc.telemetry.collector.CollectorControllerGrpc;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto;
import ru.yandex.practicum.grpc.telemetry.event.SensorEventProto;
import ru.yandex.practicum.telemetry.collector.service.CollectorService;

@GrpcService
public class EventController extends CollectorControllerGrpc.CollectorControllerImplBase {
    private final CollectorService collectorService;

    public EventController(CollectorService collectorService) {
        this.collectorService = collectorService;
    }

    @Override
    public void collectSensorEvent(SensorEventProto request, StreamObserver<Empty> responseObserver) {
        process(() -> collectorService.collectSensorEvent(request), responseObserver);
    }

    @Override
    public void collectHubEvent(HubEventProto request, StreamObserver<Empty> responseObserver) {
        process(() -> collectorService.collectHubEvent(request), responseObserver);
    }

    private void process(Runnable action, StreamObserver<Empty> responseObserver) {
        try {
            action.run();
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Exception exception) {
            responseObserver.onError(Status.INTERNAL.withDescription(exception.getMessage())
                    .withCause(exception).asRuntimeException());
        }
    }
}
