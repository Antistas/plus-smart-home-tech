package ru.yandex.practicum.order.service;

public record ServiceCallResult<T>(Status status, T value, String message) {

    public enum Status {
        SUCCESS,
        FAILURE,
        DEGRADED
    }

    public static <T> ServiceCallResult<T> success(T value) {
        return new ServiceCallResult<>(Status.SUCCESS, value, null);
    }

    public static <T> ServiceCallResult<T> failure(String message) {
        return new ServiceCallResult<>(Status.FAILURE, null, message);
    }

    public static <T> ServiceCallResult<T> degraded(T value, String message) {
        return new ServiceCallResult<>(Status.DEGRADED, value, message);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isFailure() {
        return status == Status.FAILURE;
    }

    public boolean isDegraded() {
        return status == Status.DEGRADED;
    }
}
