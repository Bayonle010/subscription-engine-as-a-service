package com.markbay.subscription_engine.nomba.support;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.nomba.dto.response.NombaWebhookPaymentData;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.json.JsonParseException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class NombaWebhookPayloadExtractor {

    private final ObjectMapper objectMapper;

    public JsonNode readTree(String rawPayload) {
        try {
            return objectMapper.readTree(rawPayload);
        } catch (JsonParseException exception) {
            throw new BadRequestException("Invalid Nomba webhook payload");
        }
    }

    public String eventType(JsonNode payload) {
        return firstText(payload, "event_type", "eventType", "event");
    }

    public String requestId(JsonNode payload) {
        return firstText(payload, "requestId", "request_id");
    }

    public String eventReference(JsonNode payload) {
        String requestId = requestId(payload);

        if (hasText(requestId)) {
            return requestId;
        }

        String transactionId = transactionId(payload);

        if (hasText(transactionId)) {
            return transactionId;
        }

        return orderReference(payload);
    }

    public String orderReference(JsonNode payload) {
        return firstText(
                payload,
                "data.order.orderReference",
                "data.transaction.merchantTxRef",
                "orderReference"
        );
    }

    public String transactionId(JsonNode payload) {
        return firstText(
                payload,
                "data.transaction.transactionId",
                "transactionId"
        );
    }

    public NombaWebhookPaymentData paymentData(JsonNode payload) {
        String cardPan = firstText(
                payload,
                "data.tokenizedCardData.cardPan",
                "data.order.cardPan"
        );

        String cardLast4 = firstText(
                payload,
                "data.order.cardLast4Digits",
                "data.tokenizedCardData.cardLast4",
                "data.cardDetails.cardLast4"
        );

        if (!hasText(cardLast4)) {
            cardLast4 = extractLast4(cardPan);
        }

        return new NombaWebhookPaymentData(
                orderReference(payload),
                transactionId(payload),
                firstText(payload, "data.tokenizedCardData.tokenKey"),
                firstText(payload, "data.tokenizedCardData.cardType", "data.order.cardType"),
                cardLast4,
                firstText(payload, "data.tokenizedCardData.tokenExpiryMonth"),
                firstText(payload, "data.tokenizedCardData.tokenExpiryYear")
        );
    }

    public String firstText(JsonNode root, String... paths) {
        if (root == null || paths == null) {
            return null;
        }

        for (String path : paths) {
            JsonNode node = readPath(root, path);

            if (node != null && !node.isNull() && node.isValueNode()) {
                String value = node.asText();

                if (hasText(value)) {
                    return value.trim();
                }
            }
        }

        return null;
    }

    private JsonNode readPath(JsonNode root, String path) {
        if (root == null || !hasText(path)) {
            return null;
        }

        JsonNode current = root;

        for (String part : path.split("\\.")) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }

            current = current.path(part);
        }

        if (current == null || current.isMissingNode()) {
            return null;
        }

        return current;
    }

    private String extractLast4(String cardPan) {
        if (!hasText(cardPan)) {
            return null;
        }

        String digitsOnly = cardPan.replaceAll("\\D", "");

        if (digitsOnly.length() < 4) {
            return null;
        }

        return digitsOnly.substring(digitsOnly.length() - 4);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}