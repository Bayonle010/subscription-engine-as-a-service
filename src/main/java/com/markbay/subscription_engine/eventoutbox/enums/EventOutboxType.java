package com.markbay.subscription_engine.eventoutbox.enums;

public enum EventOutboxType {

    SUBSCRIPTION_ACTIVATED("subscription.activated"),
    INVOICE_PAID("invoice.paid"),
    PAYMENT_SUCCEEDED("payment.succeeded"),
    PAYMENT_FAILED("payment.failed"),
    SUBSCRIPTION_CANCELLED("subscription.cancelled"),
    PAYMENT_METHOD_UPDATED("payment_method.updated");

    private final String value;

    EventOutboxType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static EventOutboxType from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        for (EventOutboxType eventType : values()) {
            if (eventType.value.equalsIgnoreCase(value.trim())) {
                return eventType;
            }
        }

        return null;
    }
}