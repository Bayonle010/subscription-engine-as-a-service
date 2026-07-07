package com.markbay.subscription_engine.nomba.gateway;

import com.markbay.subscription_engine.nomba.dto.response.NombaApiResponse;
import com.markbay.subscription_engine.nomba.dto.response.NombaVerifiedTransactionResult;
import com.markbay.subscription_engine.nomba.exception.NombaApiException;
import com.markbay.subscription_engine.nomba.service.NombaAuthService;
import com.markbay.subscription_engine.nomba.support.NombaRestClientErrorHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Set;

@Slf4j
@Component
public class NombaTransactionGateway {

    private static final Set<String> SUCCESS_STATUSES = Set.of(
            "SUCCESS",
            "SUCCESSFUL",
            "COMPLETED",
            "PAYMENT_SUCCESS",
            "PAYMENT SUCCESSFUL",
            "PAID"
    );

    private final RestClient nombaRestClient;
    private final NombaAuthService nombaAuthService;
    private final NombaRestClientErrorHandler nombaErrorHandler;
    private final ObjectMapper objectMapper;

    public NombaTransactionGateway(
            @Qualifier("nombaParentRestClient") RestClient nombaRestClient,
            NombaAuthService nombaAuthService,
            NombaRestClientErrorHandler nombaErrorHandler,
            ObjectMapper objectMapper
    ) {
        this.nombaRestClient = nombaRestClient;
        this.nombaAuthService = nombaAuthService;
        this.nombaErrorHandler = nombaErrorHandler;
        this.objectMapper = objectMapper;
    }

    public NombaVerifiedTransactionResult verifyByOrderReference(String orderReference) {
        try {
            log.info(
                    "Verifying Nomba transaction. orderReference={}",
                    orderReference
            );

            NombaApiResponse<JsonNode> response = nombaRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/transactions/accounts/single")
                            .queryParam("orderReference", orderReference)
                            .build()
                    )
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + nombaAuthService.getAccessToken())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, nombaErrorHandler::handle)
                    .body(new ParameterizedTypeReference<NombaApiResponse<JsonNode>>() {});

            NombaVerifiedTransactionResult result =
                    parseVerificationResponse(response, orderReference);

            log.info(
                    "Nomba transaction verification completed. requestedOrderReference={}, verifiedOrderReference={}, status={}, success={}",
                    orderReference,
                    result.orderReference(),
                    result.status(),
                    result.success()
            );

            return result;

        } catch (NombaApiException exception) {
            throw exception;

        } catch (ResourceAccessException exception) {
            log.error(
                    "Nomba transaction verification network error. orderReference={}, message={}",
                    orderReference,
                    exception.getMessage()
            );

            throw new NombaApiException("Nomba transaction verification network error", exception);

        } catch (Exception exception) {
            log.error(
                    "Nomba transaction verification unexpected error. orderReference={}",
                    orderReference,
                    exception
            );

            throw new NombaApiException("Nomba transaction verification unexpected error", exception);
        }
    }

    private NombaVerifiedTransactionResult parseVerificationResponse(
            NombaApiResponse<JsonNode> response,
            String fallbackOrderReference
    ) {
        if (response == null) {
            throw new NombaApiException("Nomba transaction verification response is empty");
        }

        if (response.code() != null && !response.isSuccessful()) {
            throw new NombaApiException(
                    "Nomba transaction verification failed: " + response.description()
            );
        }

        JsonNode data = response.data();

        if (data == null || data.isNull()) {
            throw new NombaApiException("Nomba transaction verification data is empty");
        }

        String status = firstText(
                data,
                "status",
                "transaction.status",
                "transactionDetails.statusCode",
                "statusCode"
        );

        /*
         * Important:
         * For online checkout/tokenized-card transactions, Nomba returns:
         *
         * onlineCheckoutOrderReference = our original orderReference
         * merchantTxRef = Nomba/internal transaction reference
         *
         * So merchantTxRef must NOT be used as orderReference.
         */
        String verifiedOrderReference = firstText(
                data,
                "onlineCheckoutOrderReference",
                "orderReference",
                "order.orderReference",
                "checkoutOrderReference",
                "transaction.orderReference"
        );

        if (!hasText(verifiedOrderReference)) {
            verifiedOrderReference = fallbackOrderReference;
        }

        String transactionReference = firstText(
                data,
                "id",
                "transactionRef",
                "transactionReference",
                "transactionId",
                "paymentReference",
                "paymentVendorReference",
                "merchantTxRef",
                "transaction.transactionId",
                "transactionDetails.paymentReference"
        );

        BigDecimal amount = firstDecimal(
                data,
                "onlineCheckoutAmount",
                "amount",
                "order.amount",
                "transactionAmount",
                "transaction.transactionAmount"
        );

        String currency = firstText(
                data,
                "onlineCheckoutCurrency",
                "currency",
                "order.currency",
                "cardDetails.cardCurrency"
        );

        boolean success = SUCCESS_STATUSES.contains(normalize(status));

        return new NombaVerifiedTransactionResult(
                success,
                status,
                verifiedOrderReference,
                transactionReference,
                amount,
                currency,
                toJson(response)
        );
    }

    private String firstText(JsonNode root, String... paths) {
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

    private BigDecimal firstDecimal(JsonNode root, String... paths) {
        if (root == null || paths == null) {
            return null;
        }

        for (String path : paths) {
            JsonNode node = readPath(root, path);

            if (node != null && !node.isNull() && node.isValueNode()) {
                try {
                    return new BigDecimal(node.asText().replace(",", ""));
                } catch (NumberFormatException ignored) {
                    // try next path
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return null;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().toUpperCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}