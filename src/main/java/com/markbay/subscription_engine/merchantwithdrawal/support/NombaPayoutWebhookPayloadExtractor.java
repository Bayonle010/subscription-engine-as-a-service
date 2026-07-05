package com.markbay.subscription_engine.merchantwithdrawal.support;

import com.markbay.subscription_engine.merchantwithdrawal.dto.NombaPayoutWebhookData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class NombaPayoutWebhookPayloadExtractor {

    private final ObjectMapper objectMapper;

    public NombaPayoutWebhookData extract(
            String eventType,
            JsonNode payload
    ) {
        return new NombaPayoutWebhookData(
                eventType,
                findText(payload, "merchantTxRef"),
                firstNonBlank(
                        findText(payload, "transferId"),
                        findText(payload, "transactionId"),
                        findText(payload, "transactionRef"),
                        findText(payload, "id")
                ),
                firstNonBlank(
                        findText(payload, "status"),
                        findText(payload, "transactionStatus"),
                        eventTypeToStatus(eventType)
                ),
                findDecimal(payload, "amount"),
                toRawJson(payload)
        );
    }

    private String eventTypeToStatus(String eventType) {
        if (eventType == null) {
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

        if (found == null || found.isMissingNode() || found.isNull()) {
            return null;
        }

        return found.asText();
    }

    private BigDecimal findDecimal(JsonNode root, String fieldName) {
        if (root == null || root.isNull()) {
            return null;
        }

        JsonNode found = root.findValue(fieldName);

        if (found == null || found.isMissingNode() || found.isNull()) {
            return null;
        }

        try {
            return found.decimalValue();
        } catch (Exception exception) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }

        return null;
    }

    private String toRawJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            return "{}";
        }
    }
}