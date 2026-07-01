package com.markbay.subscription_engine.nomba.gateway;

import com.markbay.subscription_engine.nomba.dto.request.NombaCheckoutOrder;
import com.markbay.subscription_engine.nomba.dto.request.NombaCreateCheckoutOrderRequest;
import com.markbay.subscription_engine.nomba.dto.response.NombaApiResponse;
import com.markbay.subscription_engine.nomba.dto.response.NombaCheckoutOrderResult;
import com.markbay.subscription_engine.nomba.exception.NombaApiException;
import com.markbay.subscription_engine.nomba.service.NombaAuthService;
import com.markbay.subscription_engine.nomba.support.NombaRestClientErrorHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParseException;
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
public class NombaCheckoutGateway {

    private final RestClient nombaRestClient;

    private final NombaAuthService nombaAuthService;
    private final NombaRestClientErrorHandler nombaErrorHandler;
    private final ObjectMapper objectMapper;
    @Value("${payment.nomba.subaccount-id}")
    private String nombaSubAccountId;

    public NombaCheckoutGateway(@Qualifier("nombaParentRestClient")RestClient nombaRestClient,
                                NombaAuthService nombaAuthService, NombaRestClientErrorHandler nombaErrorHandler,
                                ObjectMapper objectMapper) {
        this.nombaRestClient = nombaRestClient;
        this.nombaAuthService = nombaAuthService;
        this.nombaErrorHandler = nombaErrorHandler;
        this.objectMapper = objectMapper;
    }

    public NombaCheckoutOrderResult createCheckoutOrder(
            NombaCreateCheckoutOrderRequest request
    ) {
        try {
            log.info(
                    "Creating Nomba checkout order. orderReference={}, amount={}, currency={}",
                    request.order().orderReference(),
                    request.order().amount(),
                    request.order().currency()
            );

            NombaCreateCheckoutOrderRequest requestWithSubAccount =
                    withSubAccountId(request);

            NombaApiResponse<JsonNode> response = nombaRestClient.post()
                    .uri("/v1/checkout/order")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + nombaAuthService.getAccessToken())
                    .body(requestWithSubAccount)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, nombaErrorHandler::handle)
                    .body(new ParameterizedTypeReference<NombaApiResponse<JsonNode>>() {});

            NombaCheckoutOrderResult result = parseCheckoutOrderResponse(
                    response,
                    request.order().orderReference()
            );

            log.info(
                    "Nomba checkout order created successfully. orderReference={}",
                    result.orderReference()
            );

            return result;

        } catch (NombaApiException exception) {
            throw exception;

        } catch (ResourceAccessException exception) {
            log.error(
                    "Nomba checkout order network error. orderReference={}, message={}",
                    request.order().orderReference(),
                    exception.getMessage()
            );

            throw new NombaApiException("Nomba checkout order network error", exception);

        } catch (Exception exception) {
            log.error(
                    "Nomba checkout order unexpected error. orderReference={}",
                    request.order().orderReference(),
                    exception
            );

            throw new NombaApiException("Nomba checkout order unexpected error", exception);
        }
    }

    private NombaCreateCheckoutOrderRequest withSubAccountId(
            NombaCreateCheckoutOrderRequest request
    ) {
        if (request == null || request.order() == null) {
            return request;
        }

        NombaCheckoutOrder order = request.order();

        NombaCheckoutOrder orderWithSubAccount = new NombaCheckoutOrder(
                order.amount(),
                order.currency(),
                order.orderReference(),
                order.callbackUrl(),
                order.customerEmail(),
                order.customerId(),
                nombaSubAccountId,
                order.orderMetaData(),
                order.allowedPaymentMethods()
        );

        return new NombaCreateCheckoutOrderRequest(
                orderWithSubAccount,
                request.tokenizeCard(),
                request.meta()
        );
    }

    private NombaCheckoutOrderResult parseCheckoutOrderResponse(
            NombaApiResponse<JsonNode> response,
            String fallbackOrderReference
    ) {
        if (response == null) {
            throw new NombaApiException("Nomba checkout order response is empty");
        }

        if (response.code() != null && !response.isSuccessful()) {
            throw new NombaApiException(
                    "Nomba checkout order failed: " + response.description()
            );
        }

        JsonNode data = response.data();

        if (data == null || data.isNull()) {
            throw new NombaApiException("Nomba checkout order response data is empty");
        }

        String checkoutLink = firstText(
                data,
                "checkoutLink",
                "checkoutUrl",
                "link",
                "order.checkoutLink",
                "order.checkoutUrl",
                "order.link"
        );

        if (!hasText(checkoutLink)) {
            throw new NombaApiException("Nomba checkout link missing from response");
        }

        String orderReference = firstText(
                data,
                "orderReference",
                "order.orderReference",
                "reference",
                "order.reference"
        );

        if (!hasText(orderReference)) {
            orderReference = fallbackOrderReference;
        }

        return new NombaCheckoutOrderResult(
                orderReference,
                checkoutLink,
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
        } catch (JsonParseException exception) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}