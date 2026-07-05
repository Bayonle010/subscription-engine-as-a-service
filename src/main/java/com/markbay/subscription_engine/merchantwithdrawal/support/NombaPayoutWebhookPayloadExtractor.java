package com.markbay.subscription_engine.merchantwithdrawal.support;

import com.markbay.subscription_engine.merchantwithdrawal.dto.NombaPayoutWebhookData;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

@Component
public class NombaPayoutWebhookPayloadExtractor {

    public NombaPayoutWebhookData extract(
            String eventType,
            JsonNode payload
    ) {
        return new NombaPayoutWebhookData(
                eventType,
                firstNonBlank(
                        findText(payload, "merchantTxRef"),
                        findText(payload, "merchant_tx_ref")
                ),
                firstNonBlank(
                        findText(payload, "transferId"),
                        findText(payload, "transfer_id"),
                        findText(payload, "transactionId"),
                        findText(payload, "transaction_id"),
                        findText(payload, "transactionRef"),
                        findText(payload, "transaction_ref"),
                        findText(payload, "id")
                ),
                firstNonBlank(
                        findText(payload, "status"),
                        findText(payload, "transactionStatus"),
                        findText(payload, "transaction_status"),
                        eventTypeToStatus(eventType)
                ),
                findDecimal(payload, "amount"),
                payload == null ? "{}" : payload.toString()
        );
    }

    private String eventTypeToStatus(String eventType) {
        if (eventType == null || eventType.trim().isEmpty()) {
            return null;
        }

        return switch (eventType.trim().toLowerCase()) {
            case "payout_success" -> "SUCCESS";
            case "payout_failed" -> "FAILED";
            case "payout_refund" -> "REFUND";
            default -> null;
        };
    }

    private String findText(JsonNode root, String fieldName) {
        if (root == null || root.isNull()) {
            return null;
        }

        JsonNode found = root.findValue(fieldName);

        if (found == null || found.isNull() || found.isMissingNode()) {
            return null;
        }

        String value = found.asText();

        return hasText(value) ? value : null;
    }

    private BigDecimal findDecimal(JsonNode root, String fieldName) {
        String value = findText(root, fieldName);

        if (!hasText(value)) {
            return null;
        }

        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (Exception exception) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }

        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}