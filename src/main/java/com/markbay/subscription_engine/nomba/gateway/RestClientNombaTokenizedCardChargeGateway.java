package com.markbay.subscription_engine.nomba.gateway;

import com.markbay.subscription_engine.nomba.dto.request.NombaTokenizedCardChargeRequest;
import com.markbay.subscription_engine.nomba.dto.response.NombaApiResponse;
import com.markbay.subscription_engine.nomba.dto.response.NombaTokenizedCardChargeResult;
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

@Slf4j
@Component
public class RestClientNombaTokenizedCardChargeGateway
        implements NombaTokenizedCardChargeGateway {

    private final RestClient nombaSubAccountRestClient;
    private final NombaAuthService nombaAuthService;
    private final NombaRestClientErrorHandler nombaErrorHandler;
    private final ObjectMapper objectMapper;

    public RestClientNombaTokenizedCardChargeGateway(
            @Qualifier("nombaSubAccountRestClient") RestClient nombaSubAccountRestClient,
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
        try {
            log.info(
                    "Charging Nomba tokenized card. orderReference={}",
                    request.order() != null ? request.order().orderReference() : null
            );

            NombaApiResponse<JsonNode> response = nombaSubAccountRestClient.post()
                    .uri("/v1/checkout/tokenized-card-payment")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + nombaAuthService.getAccessToken())
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, nombaErrorHandler::handle)
                    .body(new ParameterizedTypeReference<NombaApiResponse<JsonNode>>() {});

            NombaTokenizedCardChargeResult result =
                    parseResponse(response, request);

            log.info(
                    "Nomba tokenized card charge completed. orderReference={}, success={}, status={}",
                    result.orderReference(),
                    result.success(),
                    result.status()
            );

            return result;

        } catch (NombaApiException exception) {
            throw exception;

        } catch (ResourceAccessException exception) {
            log.error(
                    "Nomba tokenized card charge network error. orderReference={}, message={}",
                    request.order() != null ? request.order().orderReference() : null,
                    exception.getMessage()
            );

            throw new NombaApiException("Nomba tokenized card charge network error", exception);

        } catch (Exception exception) {
            log.error(
                    "Nomba tokenized card charge unexpected error. orderReference={}",
                    request.order() != null ? request.order().orderReference() : null,
                    exception
            );

            throw new NombaApiException("Nomba tokenized card charge unexpected error", exception);
        }
    }

    private NombaTokenizedCardChargeResult parseResponse(
            NombaApiResponse<JsonNode> response,
            NombaTokenizedCardChargeRequest request
    ) {
        if (response == null) {
            throw new NombaApiException("Nomba tokenized card charge response is empty");
        }

        JsonNode data = response.data();

        String status = data == null ? null : firstText(
                data,
                "status",
                "transaction.status",
                "payment.status"
        );

        String message = data == null ? response.description() : firstText(
                data,
                "message",
                "description",
                "transaction.message"
        );

        String transactionReference = data == null ? null : firstText(
                data,
                "transactionRef",
                "transactionReference",
                "transactionId",
                "paymentReference",
                "transaction.transactionId"
        );

        String orderReference = data == null ? null : firstText(
                data,
                "orderReference",
                "order.orderReference",
                "merchantTxRef",
                "transaction.merchantTxRef"
        );

        if (!hasText(orderReference) && request.order() != null) {
            orderReference = request.order().orderReference();
        }

        boolean success =
                response.isSuccessful()
                        && (
                        "true".equalsIgnoreCase(status)
                                || "SUCCESS".equalsIgnoreCase(status)
                                || "success".equalsIgnoreCase(message)
                                || data == null
                );

        return new NombaTokenizedCardChargeResult(
                success,
                status,
                message,
                orderReference,
                transactionReference,
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

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}