package com.markbay.subscription_engine.nomba.gateway;

import com.markbay.subscription_engine.nomba.dto.request.NombaTokenizedCardChargeRequest;
import com.markbay.subscription_engine.nomba.dto.request.NombaTokenizedCardOrder;
import com.markbay.subscription_engine.nomba.dto.response.NombaApiResponse;
import com.markbay.subscription_engine.nomba.dto.response.NombaTokenizedCardChargeResult;
import com.markbay.subscription_engine.nomba.exception.NombaApiException;
import com.markbay.subscription_engine.nomba.service.NombaAuthService;
import com.markbay.subscription_engine.nomba.support.NombaRestClientErrorHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

@Slf4j
@Component
public class RestClientNombaTokenizedCardChargeGateway
        implements NombaTokenizedCardChargeGateway {

    private static final Set<String> SUCCESS_STATUSES = Set.of(
            "SUCCESS",
            "SUCCESSFUL",
            "COMPLETED",
            "PAYMENT_SUCCESS",
            "PAID"
    );

    private final RestClient nombaSubAccountRestClient;
    private final NombaAuthService nombaAuthService;
    private final NombaRestClientErrorHandler nombaErrorHandler;
    private final ObjectMapper objectMapper;

    @Value("${payment.nomba.subaccount-id}")
    private String nombaSubAccountId;

    public RestClientNombaTokenizedCardChargeGateway(
            @Qualifier("nombaParentRestClient") RestClient nombaSubAccountRestClient,
            NombaAuthService nombaAuthService,
            NombaRestClientErrorHandler nombaErrorHandler,
            ObjectMapper objectMapper
    ) {
        this.nombaSubAccountRestClient = nombaSubAccountRestClient;
        this.nombaAuthService = nombaAuthService;
        this.nombaErrorHandler = nombaErrorHandler;
        this.objectMapper = objectMapper;
    }

    @Override
    public NombaTokenizedCardChargeResult chargeTokenizedCard(
            NombaTokenizedCardChargeRequest request
    ) {
        String fallbackOrderReference = resolveRequestOrderReference(request);

        try {
            log.info(
                    "Charging Nomba tokenized card. orderReference={}",
                    fallbackOrderReference
            );



            NombaTokenizedCardChargeRequest requestWithSubAccount =
                    withSubAccountId(request);

            log.info(
                    "Sending request to Nomba tokenized-card-payment endpoint. orderReference={}, payload={}",
                    fallbackOrderReference,
                    sanitizeAndTrimForLog(requestWithSubAccount.toString())
            );

            NombaApiResponse<JsonNode> response = nombaSubAccountRestClient.post()
                    .uri("/v1/checkout/tokenized-card-payment")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + nombaAuthService.getAccessToken())
                    .body(requestWithSubAccount)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, nombaErrorHandler::handle)
                    .body(new ParameterizedTypeReference<NombaApiResponse<JsonNode>>() {});

            NombaTokenizedCardChargeResult result =
                    parseResponse(
                            response,
                            request
                    );

            log.info(
                    "Nomba tokenized card charge completed. orderReference={}, accepted={}, success={}, requiresCustomerAction={}, status={}, message={}, rawResponse={}",
                    result.orderReference(),
                    result.accepted(),
                    result.success(),
                    result.requiresCustomerAction(),
                    result.status(),
                    result.message(),
                    sanitizeAndTrimForLog(result.rawResponse())
            );

            return result;

        } catch (NombaApiException exception) {
            throw exception;

        } catch (ResourceAccessException exception) {
            log.error(
                    "Nomba tokenized card charge network error. orderReference={}, message={}",
                    fallbackOrderReference,
                    exception.getMessage()
            );

            throw new NombaApiException("Nomba tokenized card charge network error", exception);

        } catch (Exception exception) {
            log.error(
                    "Nomba tokenized card charge unexpected error. orderReference={}",
                    fallbackOrderReference,
                    exception
            );

            throw new NombaApiException("Nomba tokenized card charge unexpected error", exception);
        }
    }

    private NombaTokenizedCardChargeRequest withSubAccountId(
            NombaTokenizedCardChargeRequest request
    ) {
        if (request == null || request.order() == null) {
            return request;
        }

        NombaTokenizedCardOrder order = request.order();

        NombaTokenizedCardOrder orderWithSubAccount = new NombaTokenizedCardOrder(
                order.amount(),
                order.currency(),
                order.orderReference(),
                order.customerEmail(),
                order.customerId(),
                order.callbackUrl(),
                nombaSubAccountId
        );

        return new NombaTokenizedCardChargeRequest(
                request.tokenKey(),
                orderWithSubAccount
        );
    }

    private NombaTokenizedCardChargeResult parseResponse(
            NombaApiResponse<JsonNode> response,
            NombaTokenizedCardChargeRequest request
    ) {
        if (response == null) {
            throw new NombaApiException("Nomba tokenized card charge response is empty");
        }

        JsonNode data = response.data();

        String fallbackOrderReference = resolveRequestOrderReference(request);

        String message = data == null
                ? response.description()
                : firstText(
                data,
                "message",
                "description",
                "transaction.message",
                "payment.message"
        );

        if (!hasText(message)) {
            message = response.description();
        }

        boolean requiresCustomerAction = requiresCustomerAction(message);

        String providerStatus = data == null
                ? null
                : firstText(
                data,
                "status",
                "transaction.status",
                "payment.status"
        );

        String status = requiresCustomerAction
                ? "REQUIRES_CUSTOMER_ACTION"
                : firstNonBlank(
                providerStatus,
                response.description()
        );

        String orderId = data == null
                ? null
                : firstText(
                data,
                "orderId",
                "onlineCheckoutOrderId",
                "order.id",
                "transaction.orderId"
        );

        String transactionReference = data == null
                ? null
                : firstText(
                data,
                "transactionRef",
                "transactionReference",
                "transactionId",
                "paymentReference",
                "paymentVendorReference",
                "id",
                "transaction.transactionId"
        );

        String orderReference = data == null
                ? null
                : firstText(
                data,
                "orderReference",
                "onlineCheckoutOrderReference",
                "order.orderReference",
                "transaction.orderReference"
        );

        if (!hasText(orderReference)) {
            orderReference = fallbackOrderReference;
        }

        boolean dataStatusIsTrue = "true".equalsIgnoreCase(providerStatus);

        boolean accepted =
                response.isSuccessful()
                        || dataStatusIsTrue
                        || SUCCESS_STATUSES.contains(normalize(providerStatus));

        boolean success =
                accepted
                        && !requiresCustomerAction
                        && SUCCESS_STATUSES.contains(normalize(status));

        return new NombaTokenizedCardChargeResult(
                success,
                accepted,
                requiresCustomerAction,
                status,
                message,
                orderId,
                orderReference,
                transactionReference,
                toJson(response)
        );
    }

    private boolean requiresCustomerAction(String message) {
        if (!hasText(message)) {
            return false;
        }

        String normalized = message.toLowerCase();

        return normalized.contains("otp")
                || normalized.contains("3ds")
                || normalized.contains("3-d")
                || normalized.contains("authenticate")
                || normalized.contains("authentication")
                || normalized.contains("enter the")
                || normalized.contains("sent to");
    }

    private String resolveRequestOrderReference(
            NombaTokenizedCardChargeRequest request
    ) {
        if (request == null || request.order() == null) {
            return null;
        }

        return request.order().orderReference();
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return null;
        }
    }

    private String sanitizeAndTrimForLog(String value) {
        if (!hasText(value)) {
            return value;
        }

        String sanitized = value
                .replaceAll("(?i)(\"tokenKey\"\\s*:\\s*\")[^\"]*(\")", "$1***$2")
                .replaceAll("(?i)(\"onlineCheckoutTokenKey\"\\s*:\\s*\")[^\"]*(\")", "$1***$2")
                .replaceAll("(?i)(\"cardPan\"\\s*:\\s*\")[^\"]*(\")", "$1***$2")
                .replaceAll("(?i)(\"onlineCheckoutCardPan\"\\s*:\\s*\")[^\"]*(\")", "$1***$2")
                .replaceAll("(?i)(\"customerBillerId\"\\s*:\\s*\")[^\"]*(\")", "$1***$2");

        int maxLength = 2000;

        if (sanitized.length() <= maxLength) {
            return sanitized;
        }

        return sanitized.substring(0, maxLength) + "...[truncated]";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}