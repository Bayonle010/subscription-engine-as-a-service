package com.markbay.subscription_engine.eventoutbox.enums;

public enum EventOutboxType {

    SUBSCRIPTION_ACTIVATED("subscription.activated"),
    SUBSCRIPTION_UPDATED("subscription.updated"),
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
        for (EventOutboxType type : values()) {
            if (type.name().equalsIgnoreCase(value)
                    || type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unsupported event outbox type: " + value);
    }
}