package com.markbay.subscription_engine.reconciliation.support;

import com.markbay.subscription_engine.nomba.dto.response.NombaVerifiedTransactionResult;
import com.markbay.subscription_engine.nomba.dto.response.NombaWebhookPaymentData;
import com.markbay.subscription_engine.nomba.support.NombaWebhookPayloadExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
public class ReconciliationPaymentDataFactory {

    private final NombaWebhookPayloadExtractor payloadExtractor;

    public NombaWebhookPaymentData fromVerifiedTransaction(
            String fallbackOrderReference,
            NombaVerifiedTransactionResult verifiedTransaction
    ) {
        NombaWebhookPaymentData extracted = null;

        if (verifiedTransaction != null && hasText(verifiedTransaction.rawResponse())) {
            try {
                JsonNode payload = payloadExtractor.readTree(
                        verifiedTransaction.rawResponse()
                );

                extracted = payloadExtractor.paymentData(payload);
            } catch (Exception ignored) {
                extracted = null;
            }
        }

        return new NombaWebhookPaymentData(
                first(
                        extracted == null ? null : extracted.orderReference(),
                        verifiedTransaction == null ? null : verifiedTransaction.orderReference(),
                        fallbackOrderReference
                ),
                first(
                        extracted == null ? null : extracted.transactionReference(),
                        verifiedTransaction == null ? null : verifiedTransaction.transactionReference()
                ),
                extracted == null ? null : extracted.tokenKey(),
                extracted == null ? null : extracted.cardType(),
                extracted == null ? null : extracted.cardLast4(),
                extracted == null ? null : extracted.expiryMonth(),
                extracted == null ? null : extracted.expiryYear()
        );
    }

    private String first(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }

        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}