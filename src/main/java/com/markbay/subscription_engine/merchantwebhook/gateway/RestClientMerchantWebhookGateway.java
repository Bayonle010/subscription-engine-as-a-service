package com.markbay.subscription_engine.merchantwebhook.gateway;

import com.markbay.subscription_engine.merchantwebhook.dto.MerchantWebhookDispatchResult;
import com.markbay.subscription_engine.merchantwebhook.entity.MerchantWebhookEndpoint;
import com.markbay.subscription_engine.merchantwebhook.service.MerchantWebhookSigningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class RestClientMerchantWebhookGateway implements MerchantWebhookGateway {

    private final RestClient.Builder restClientBuilder;
    private final MerchantWebhookSigningService signingService;

    @Override
    public MerchantWebhookDispatchResult send(
            MerchantWebhookEndpoint endpoint,
            String payloadJson,
            String eventName,
            String deliveryReference
    ) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        String signature = signingService.sign(
                payloadJson,
                endpoint.getSecretKey(),
                timestamp
        );

        try {
            String responseBody = restClientBuilder.build()
                    .post()
                    .uri(endpoint.getUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Subscription-Engine-Event", eventName)
                    .header("X-Subscription-Engine-Delivery", deliveryReference)
                    .header("X-Subscription-Engine-Timestamp", timestamp)
                    .header("X-Subscription-Engine-Signature", signature)
                    .body(payloadJson)
                    .retrieve()
                    .body(String.class);

            log.info(
                    "Merchant webhook delivered. endpointId={}, event={}, deliveryReference={}",
                    endpoint.getId(),
                    eventName,
                    deliveryReference
            );

            return new MerchantWebhookDispatchResult(
                    true,
                    200,
                    responseBody,
                    null
            );

        } catch (RestClientResponseException exception) {
            log.warn(
                    "Merchant webhook returned error. endpointId={}, event={}, deliveryReference={}, status={}, response={}",
                    endpoint.getId(),
                    eventName,
                    deliveryReference,
                    exception.getStatusCode().value(),
                    truncate(exception.getResponseBodyAsString())
            );

            return new MerchantWebhookDispatchResult(
                    false,
                    exception.getStatusCode().value(),
                    exception.getResponseBodyAsString(),
                    exception.getMessage()
            );

        } catch (ResourceAccessException exception) {
            log.warn(
                    "Merchant webhook network error. endpointId={}, event={}, deliveryReference={}, message={}",
                    endpoint.getId(),
                    eventName,
                    deliveryReference,
                    exception.getMessage()
            );

            return new MerchantWebhookDispatchResult(
                    false,
                    0,
                    null,
                    exception.getMessage()
            );

        } catch (Exception exception) {
            log.error(
                    "Merchant webhook unexpected error. endpointId={}, event={}, deliveryReference={}",
                    endpoint.getId(),
                    eventName,
                    deliveryReference,
                    exception
            );

            return new MerchantWebhookDispatchResult(
                    false,
                    0,
                    null,
                    exception.getMessage()
            );
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }

        if (value.length() <= 500) {
            return value;
        }

        return value.substring(0, 500) + "...";
    }
}