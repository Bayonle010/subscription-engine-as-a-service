package com.markbay.subscription_engine.common.response;

import java.time.Instant;

public class ResponseUtil {

    private ResponseUtil() {
    }

    public static <T> ApiResponse<T> success(
            int statusCode,
            String message,
            Object details,
            T data,
            Object metadata
    ) {
        return ApiResponse.<T>builder()
                .status(true)
                .statusCode(statusCode)
                .message(message)
                .details(details)
                .data(data)
                .metadata(metadata)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> success(
            String message,
            T data
    ) {
        return success(00, message, null, data, null);
    }

    public static ApiResponse<Object> error(
            int statusCode,
            String message,
            Object details
    ) {
        return ApiResponse.builder()
                .status(false)
                .statusCode(statusCode)
                .message(message)
                .details(details)
                .data(null)
                .metadata(null)
                .timestamp(Instant.now())
                .build();
    }
}