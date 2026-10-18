package ru.yandex.practicum.telemetry.collector.controller;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto;
import ru.yandex.practicum.grpc.telemetry.event.SensorEventProto;
import ru.yandex.practicum.telemetry.collector.service.CollectorService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EventControllerTest {
    private final CollectorService collectorService = mock(CollectorService.class);
    private final EventController controller = new EventController(collectorService);

    @Test
    void shouldProcessSensorEventAndCompleteResponse() {
        SensorEventProto request = SensorEventProto.getDefaultInstance();
        @SuppressWarnings("unchecked")
        StreamObserver<Empty> observer = mock(StreamObserver.class);

        controller.collectSensorEvent(request, observer);

        verify(collectorService).collectSensorEvent(request);
        verify(observer).onNext(Empty.getDefaultInstance());
        verify(observer).onCompleted();
    }

    @Test
    void shouldProcessHubEventAndCompleteResponse() {
        HubEventProto request = HubEventProto.getDefaultInstance();
        @SuppressWarnings("unchecked")
        StreamObserver<Empty> observer = mock(StreamObserver.class);

        controller.collectHubEvent(request, observer);

        verify(collectorService).collectHubEvent(request);
        verify(observer).onNext(Empty.getDefaultInstance());
        verify(observer).onCompleted();
    }
}
