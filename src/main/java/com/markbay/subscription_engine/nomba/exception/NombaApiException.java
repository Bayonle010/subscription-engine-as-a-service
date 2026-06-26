package com.markbay.subscription_engine.nomba.exception;

public class NombaApiException extends RuntimeException {
    public NombaApiException(String message) {
        super(message);
    }

    public NombaApiException(String message, Throwable cause) {
        super(message, cause);
    }}
