package com.markbay.subscription_engine.eventoutbox.enums;

public enum EventOutboxStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    FAILED,
    DISCARDED
}