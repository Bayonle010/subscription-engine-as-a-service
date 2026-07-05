package com.markbay.subscription_engine.nomba.gateway;

import com.markbay.subscription_engine.nomba.dto.request.NombaBankTransferRequest;
import com.markbay.subscription_engine.nomba.dto.request.NombaWalletTransferRequest;
import com.markbay.subscription_engine.nomba.dto.response.NombaTransferResult;
import com.markbay.subscription_engine.nomba.gateway.NombaTransferGateway;
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
public class NombaTransferGatewayImpl implements NombaTransferGateway {

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

    private final ObjectMapper objectMapper;

    @Qualifier("nombaParentRestClient")
    private final RestClient nombaParentRestClient;

    @Value("${payment.nomba.account-id}")
    private String nombaParentAccountId;

    @Value("${payment.nomba.subaccount-id}")
    private String nombaSubAccountId;

    @Override
    public NombaTransferResult transferToBank(
            NombaBankTransferRequest request
    ) {
        log.info(
                "Initiating Nomba sub-account bank transfer. merchantTxRef={}, subAccountId={}, amount={}, bankCode={}, accountNumberMasked={}",
                request.merchantTxRef(),
                nombaSubAccountId,
                request.amount(),
                request.bankCode(),
                maskAccountNumber(request.accountNumber())
        );

        JsonNode response = nombaParentRestClient
                .post()
                .uri("/v2/transfers/bank/{subAccountId}", nombaSubAccountId)
                .header("accountId", nombaParentAccountId)
                .body(request)
                .retrieve()
                .body(JsonNode.class);

        NombaTransferResult result =
                parseTransferResponse(
                        response,
                        request.merchantTxRef()
                );

        log.info(
                "Nomba sub-account bank transfer response received. merchantTxRef={}, transferId={}, status={}, accepted={}, successful={}, pending={}",
                result.merchantTxRef(),
                result.transferId(),
                result.status(),
                result.accepted(),
                result.successful(),
                result.pending()
        );

        return result;
    }

    @Override
    public NombaTransferResult transferToWallet(
            NombaWalletTransferRequest request
    ) {
        log.info(
                "Initiating Nomba wallet transfer. merchantTxRef={}, amount={}, receiverAccountId={}",
                request.merchantTxRef(),
                request.amount(),
                request.receiverAccountId()
        );

        JsonNode response = nombaParentRestClient
                .post()
                .uri("/v2/transfers/wallet")
                .header("accountId", nombaParentAccountId)
                .body(request)
                .retrieve()
                .body(JsonNode.class);

        NombaTransferResult result =
                parseTransferResponse(
                        response,
                        request.merchantTxRef()
                );

        log.info(
                "Nomba wallet transfer response received. merchantTxRef={}, transferId={}, status={}, accepted={}, successful={}, pending={}",
                result.merchantTxRef(),
                result.transferId(),
                result.status(),
                result.accepted(),
                result.successful(),
                result.pending()
        );

        return result;
    }

    private NombaTransferResult parseTransferResponse(
            JsonNode response,
            String fallbackMerchantTxRef
    ) {
        String rawResponse = toRawJson(response);

        if (response == null || response.isNull()) {
            return new NombaTransferResult(
                    false,
                    false,
                    true,
                    null,
                    "UNKNOWN",
                    fallbackMerchantTxRef,
                    null,
                    null,
                    rawResponse
            );
        }

        String topLevelCode = text(response, "code");
        String topLevelStatus = text(response, "status");
        String description = text(response, "description");
        String message = text(response, "message");

        JsonNode data = response.path("data");
        JsonNode meta = data.path("meta");

        String providerStatus = firstNonBlank(
                text(data, "status"),
                topLevelStatus,
                description,
                message
        );

        String normalizedStatus = normalize(providerStatus);

        boolean successful =
                SUCCESS_STATUSES.contains(normalizedStatus)
                        || "00".equals(topLevelCode)
                        && SUCCESS_STATUSES.contains(normalize(text(data, "status")));

        boolean pending =
                PENDING_STATUSES.contains(normalizedStatus)
                        || "201".equals(topLevelCode);

        boolean accepted =
                successful
                        || pending
                        || "00".equals(topLevelCode)
                        || "201".equals(topLevelCode);

        String merchantTxRef = firstNonBlank(
                text(meta, "merchantTxRef"),
                text(data, "merchantTxRef"),
                fallbackMerchantTxRef
        );

        return new NombaTransferResult(
                accepted,
                successful,
                pending,
                firstNonBlank(
                        text(data, "id"),
                        text(data, "transferId"),
                        text(data, "transactionId"),
                        text(data, "sessionId")
                ),
                providerStatus,
                merchantTxRef,
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

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return accountNumber;
        }

        return "******" + accountNumber.substring(accountNumber.length() - 4);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}