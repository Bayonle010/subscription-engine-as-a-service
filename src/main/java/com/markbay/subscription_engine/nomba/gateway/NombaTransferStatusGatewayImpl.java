package com.markbay.subscription_engine.nomba.gateway;

import com.markbay.subscription_engine.nomba.dto.response.NombaTransferStatusResult;
import com.markbay.subscription_engine.nomba.gateway.NombaTransferStatusGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class NombaTransferStatusGatewayImpl
        implements NombaTransferStatusGateway {

    private static final Set<String> SUCCESS_STATUSES = Set.of(
            "SUCCESS",
            "SUCCESSFUL",
            "COMPLETED"
    );

    private static final Set<String> PENDING_STATUSES = Set.of(
            "NEW",
            "PENDING",
            "PROCESSING",
            "PENDING_BILLING"
    );

    private static final Set<String> REVERSED_STATUSES = Set.of(
            "REFUND",
            "REFUNDED",
            "REVERSED"
    );

    private static final Set<String> FAILED_STATUSES = Set.of(
            "FAILED",
            "FAILURE",
            "DECLINED",
            "CANCELLED",
            "CANCELED",
            "REJECTED"
    );

    private final ObjectMapper objectMapper;

    @Qualifier("nombaParentRestClient")
    private final RestClient nombaParentRestClient;

    @Value("${payment.nomba.account-id}")
    private String nombaParentAccountId;

    @Value("${payment.nomba.subaccount-id}")
    private String nombaSubAccountId;

    @Override
    public NombaTransferStatusResult requerySubAccountTransfer(
            String transactionRef
    ) {
        log.info(
                "Requerying Nomba sub-account transfer. subAccountId={}, transactionRef={}",
                nombaSubAccountId,
                transactionRef
        );

        JsonNode response = nombaParentRestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/transactions/accounts/{subAccountId}/single")
                        .queryParam("transactionRef", transactionRef)
                        .build(nombaSubAccountId))
                .header("accountId", nombaParentAccountId)
                .retrieve()
                .body(JsonNode.class);

        NombaTransferStatusResult result =
                parseStatusResponse(
                        response,
                        transactionRef
                );

        log.info(
                "Nomba sub-account transfer requery completed. transactionRef={}, status={}, successful={}, pending={}, reversed={}, failed={}",
                transactionRef,
                result.status(),
                result.successful(),
                result.pending(),
                result.reversed(),
                result.failed()
        );

        return result;
    }

    private NombaTransferStatusResult parseStatusResponse(
            JsonNode response,
            String fallbackTransactionRef
    ) {
        String rawResponse = toRawJson(response);

        if (response == null || response.isNull()) {
            return new NombaTransferStatusResult(
                    false,
                    false,
                    true,
                    false,
                    false,
                    fallbackTransactionRef,
                    null,
                    "UNKNOWN",
                    null,
                    null,
                    rawResponse
            );
        }

        JsonNode data = response.path("data");
        JsonNode meta = data.path("meta");

        String status = firstNonBlank(
                text(data, "status"),
                text(response, "status"),
                text(response, "description"),
                text(response, "message")
        );

        String normalizedStatus = normalize(status);

        boolean successful = SUCCESS_STATUSES.contains(normalizedStatus);
        boolean pending = PENDING_STATUSES.contains(normalizedStatus);
        boolean reversed = REVERSED_STATUSES.contains(normalizedStatus);
        boolean failed = FAILED_STATUSES.contains(normalizedStatus);

        String transferId = firstNonBlank(
                text(data, "id"),
                text(data, "transferId"),
                text(data, "transactionId"),
                fallbackTransactionRef
        );

        String merchantTxRef = firstNonBlank(
                text(meta, "merchantTxRef"),
                text(data, "merchantTxRef")
        );

        return new NombaTransferStatusResult(
                true,
                successful,
                pending,
                reversed,
                failed,
                transferId,
                merchantTxRef,
                status,
                decimal(data, "amount"),
                decimal(data, "fee"),
                rawResponse
        );
    }

    private String text(JsonNode node, String field) {
        if (node == null
                || node.isMissingNode()
                || node.isNull()
                || node.path(field).isMissingNode()
                || node.path(field).isNull()) {
            return null;
        }

        return node.path(field).asText();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        if (node == null
                || node.isMissingNode()
                || node.isNull()
                || node.path(field).isMissingNode()
                || node.path(field).isNull()) {
            return null;
        }

        try {
            return node.path(field).decimalValue();
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

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().toUpperCase();
    }

    private String toRawJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}