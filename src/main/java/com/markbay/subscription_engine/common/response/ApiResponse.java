package com.markbay.subscription_engine.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private boolean status;
    private int statusCode;
    private String message;
    private Object details;
    private T data;
    private Object metadata;
    private Instant timestamp;
}