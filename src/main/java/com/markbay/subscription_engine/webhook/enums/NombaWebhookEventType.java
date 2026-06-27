package com.markbay.subscription_engine.webhook.enums;

public enum NombaWebhookEventType {

    PAYMENT_SUCCESS("payment_success"),
    PAYMENT_FAILED("payment_failed"),
    PAYMENT_REVERSAL("payment_reversal"),

    PAYOUT_SUCCESS("payout_success"),
    PAYOUT_FAILED("payout_failed"),
    PAYOUT_REFUND("payout_refund");

    private final String value;

    NombaWebhookEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static NombaWebhookEventType from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        for (NombaWebhookEventType eventType : values()) {
            if (eventType.value.equalsIgnoreCase(value.trim())) {
                return eventType;
            }
        }

        return null;
    }
}