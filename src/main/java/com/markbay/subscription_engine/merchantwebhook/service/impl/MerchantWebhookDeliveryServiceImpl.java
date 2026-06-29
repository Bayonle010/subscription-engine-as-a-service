package com.markbay.subscription_engine.merchantwebhook.service.impl;

import com.markbay.subscription_engine.eventoutbox.entity.EventOutbox;
import com.markbay.subscription_engine.merchantwebhook.dto.MerchantWebhookDispatchResult;
import com.markbay.subscription_engine.merchantwebhook.dto.MerchantWebhookPayload;
import com.markbay.subscription_engine.merchantwebhook.entity.MerchantWebhookDelivery;
import com.markbay.subscription_engine.merchantwebhook.entity.MerchantWebhookEndpoint;
import com.markbay.subscription_engine.merchantwebhook.enums.MerchantWebhookDeliveryStatus;
import com.markbay.subscription_engine.merchantwebhook.enums.MerchantWebhookEndpointStatus;
import com.markbay.subscription_engine.merchantwebhook.gateway.MerchantWebhookGateway;
import com.markbay.subscription_engine.merchantwebhook.repository.MerchantWebhookDeliveryRepository;
import com.markbay.subscription_engine.merchantwebhook.repository.MerchantWebhookEndpointRepository;
import com.markbay.subscription_engine.merchantwebhook.service.MerchantWebhookDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantWebhookDeliveryServiceImpl implements MerchantWebhookDeliveryService {

    private final MerchantWebhookEndpointRepository endpointRepository;
    private final MerchantWebhookDeliveryRepository deliveryRepository;
    private final MerchantWebhookGateway merchantWebhookGateway;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void dispatchEvent(EventOutbox event) {
        List<MerchantWebhookEndpoint> endpoints =
                endpointRepository.findAllByTenant_IdAndStatus(
                        event.getTenant().getId(),
                        MerchantWebhookEndpointStatus.ACTIVE
                );

        if (endpoints.isEmpty()) {
            log.info(
                    "No active merchant webhook endpoints for event. tenantId={}, eventId={}, eventType={}",
                    event.getTenant().getId(),
                    event.getId(),
                    event.getEventType().value()
            );

            return;
        }

        boolean anyFailed = false;

        for (MerchantWebhookEndpoint endpoint : endpoints) {
            if (!endpoint.accepts(event.getEventType())) {
                continue;
            }

            boolean delivered = dispatchToEndpoint(event, endpoint);

            if (!delivered) {
                anyFailed = true;
            }
        }

        if (anyFailed) {
            throw new IllegalStateException(
                    "One or more merchant webhook deliveries failed"
            );
        }
    }

    private boolean dispatchToEndpoint(
            EventOutbox event,
            MerchantWebhookEndpoint endpoint
    ) {
        MerchantWebhookDelivery delivery = deliveryRepository
                .findByEndpoint_IdAndOutboxEvent_Id(endpoint.getId(), event.getId())
                .orElseGet(() -> createDelivery(event, endpoint));

        if (delivery.getStatus() == MerchantWebhookDeliveryStatus.SUCCEEDED) {
            log.info(
                    "Merchant webhook delivery already succeeded. deliveryId={}, eventId={}",
                    delivery.getId(),
                    event.getId()
            );

            return true;
        }

        delivery.setStatus(MerchantWebhookDeliveryStatus.SENDING);
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        delivery.setLastAttemptAt(Instant.now());

        String payloadJson = buildPayloadJson(event);

        MerchantWebhookDispatchResult result =
                merchantWebhookGateway.send(
                        endpoint,
                        payloadJson,
                        event.getEventType().value(),
                        delivery.getDeliveryReference()
                );

        delivery.setHttpStatus(result.httpStatus());
        delivery.setResponseBody(truncate(result.responseBody()));

        if (result.success()) {
            delivery.setStatus(MerchantWebhookDeliveryStatus.SUCCEEDED);
            delivery.setDeliveredAt(Instant.now());
            delivery.setFailureReason(null);

            deliveryRepository.save(delivery);

            return true;
        }

        delivery.setStatus(MerchantWebhookDeliveryStatus.FAILED);
        delivery.setFailureReason(result.errorMessage());

        deliveryRepository.save(delivery);

        return false;
    }

    private MerchantWebhookDelivery createDelivery(
            EventOutbox event,
            MerchantWebhookEndpoint endpoint
    ) {
        MerchantWebhookDelivery delivery = MerchantWebhookDelivery.builder()
                .tenant(event.getTenant())
                .endpoint(endpoint)
                .outboxEvent(event)
                .eventType(event.getEventType())
                .deliveryReference(
                        "whd_" + event.getId().toString().replace("-", "")
                                + "_"
                                + endpoint.getId().toString().replace("-", "")
                )
                .targetUrl(endpoint.getUrl())
                .status(MerchantWebhookDeliveryStatus.PENDING)
                .attemptCount(0)
                .build();

        return deliveryRepository.save(delivery);
    }

    private String buildPayloadJson(EventOutbox event) {
        try {
            JsonNode data = objectMapper.readTree(event.getPayload());

            MerchantWebhookPayload payload = new MerchantWebhookPayload(
                    event.getId(),
                    event.getEventType().value(),
                    event.getEventReference(),
                    event.getTenant().getId(),
                    event.getTenant().getId(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    data,
                    event.getCreatedAt()
            );

            return objectMapper.writeValueAsString(payload);

        } catch (Exception exception) {
            throw new IllegalStateException("Unable to build merchant webhook payload", exception);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }

        if (value.length() <= 1000) {
            return value;
        }

        return value.substring(0, 1000) + "...";
    }
}