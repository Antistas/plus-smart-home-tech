package ru.yandex.practicum.telemetry.collector.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.telemetry.collector.model.hub.DeviceAddedEvent;
import ru.yandex.practicum.telemetry.collector.model.sensor.LightSensorEvent;
import ru.yandex.practicum.telemetry.collector.model.sensor.SensorEvent;
import ru.yandex.practicum.telemetry.collector.service.CollectorService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
class EventControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CollectorService collectorService;

    @Test
    void shouldAcceptSensorEvent() throws Exception {
        mockMvc.perform(post("/events/sensors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"light-1","hubId":"hub-1","type":"LIGHT_SENSOR_EVENT",
                                 "linkQuality":75,"luminosity":59}
                                """))
                .andExpect(status().isOk());

        verify(collectorService).collectSensorEvent(isA(LightSensorEvent.class));
    }

    @Test
    void shouldAcceptHubEvent() throws Exception {
        mockMvc.perform(post("/events/hubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hubId":"hub-1","type":"DEVICE_ADDED",
                                 "id":"motion-1","deviceType":"MOTION_SENSOR"}
                                """))
                .andExpect(status().isOk());

        verify(collectorService).collectHubEvent(isA(DeviceAddedEvent.class));
    }

    @Test
    void shouldRejectInvalidEvent() throws Exception {
        mockMvc.perform(post("/events/sensors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"","hubId":"hub-1","type":"SWITCH_SENSOR_EVENT"}
                                """))
                .andExpect(status().isBadRequest());

        verify(collectorService, never()).collectSensorEvent(any(SensorEvent.class));
    }

    @Test
    void shouldRejectUnknownEventType() throws Exception {
        mockMvc.perform(post("/events/hubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hubId":"hub-1","type":"UNKNOWN"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
