package com.markbay.subscription_engine.merchantwebhook.service.impl;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.merchantwebhook.dto.CreateMerchantWebhookEndpointRequest;
import com.markbay.subscription_engine.merchantwebhook.dto.MerchantWebhookEndpointResponse;
import com.markbay.subscription_engine.merchantwebhook.dto.UpdateMerchantWebhookEndpointRequest;
import com.markbay.subscription_engine.merchantwebhook.entity.MerchantWebhookEndpoint;
import com.markbay.subscription_engine.merchantwebhook.enums.MerchantWebhookEndpointStatus;
import com.markbay.subscription_engine.merchantwebhook.repository.MerchantWebhookEndpointRepository;
import com.markbay.subscription_engine.security.AuthenticatedTenantProvider;
import com.markbay.subscription_engine.tenant.entity.Tenant;
import com.markbay.subscription_engine.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantWebhookEndpointServiceImpl
        implements com.markbay.subscription_engine.merchantwebhook.service.MerchantWebhookEndpointService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthenticatedTenantProvider authenticatedTenantProvider;
    private final TenantRepository tenantRepository;
    private final MerchantWebhookEndpointRepository endpointRepository;

    @Override
    @Transactional
    public MerchantWebhookEndpointResponse createEndpoint(
            CreateMerchantWebhookEndpointRequest request
    ) {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        validateUrl(request.url());

        MerchantWebhookEndpoint endpoint = MerchantWebhookEndpoint.builder()
                .tenant(tenant)
                .name(request.name().trim())
                .url(request.url().trim())
                .secretKey(generateSigningSecret())
                .status(MerchantWebhookEndpointStatus.ACTIVE)
                .subscribedEvents(
                        request.subscribedEvents() == null
                                ? new HashSet<>()
                                : new HashSet<>(request.subscribedEvents())
                )
                .build();

        MerchantWebhookEndpoint savedEndpoint = endpointRepository.save(endpoint);

        log.info(
                "Merchant webhook endpoint created. tenantId={}, endpointId={}, url={}",
                tenantId,
                savedEndpoint.getId(),
                savedEndpoint.getUrl()
        );

        return MerchantWebhookEndpointResponse.from(savedEndpoint, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MerchantWebhookEndpointResponse> listEndpoints() {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        return endpointRepository.findAllByTenant_IdOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(endpoint -> MerchantWebhookEndpointResponse.from(endpoint, false))
                .toList();
    }

    @Override
    @Transactional
    public MerchantWebhookEndpointResponse updateEndpoint(
            UUID endpointId,
            UpdateMerchantWebhookEndpointRequest request
    ) {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        MerchantWebhookEndpoint endpoint = endpointRepository
                .findByIdAndTenant_Id(endpointId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Merchant webhook endpoint not found"
                ));

        if (hasText(request.name())) {
            endpoint.setName(request.name().trim());
        }

        if (hasText(request.url())) {
            validateUrl(request.url());
            endpoint.setUrl(request.url().trim());
        }

        if (request.subscribedEvents() != null) {
            endpoint.setSubscribedEvents(new HashSet<>(request.subscribedEvents()));
        }

        MerchantWebhookEndpoint savedEndpoint = endpointRepository.save(endpoint);

        log.info(
                "Merchant webhook endpoint updated. tenantId={}, endpointId={}",
                tenantId,
                endpointId
        );

        return MerchantWebhookEndpointResponse.from(savedEndpoint, false);
    }

    @Override
    @Transactional
    public MerchantWebhookEndpointResponse disableEndpoint(UUID endpointId) {
        UUID tenantId = authenticatedTenantProvider.getCurrentTenantId();

        MerchantWebhookEndpoint endpoint = endpointRepository
                .findByIdAndTenant_Id(endpointId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Merchant webhook endpoint not found"
                ));

        endpoint.setStatus(MerchantWebhookEndpointStatus.DISABLED);

        MerchantWebhookEndpoint savedEndpoint = endpointRepository.save(endpoint);

        log.info(
                "Merchant webhook endpoint disabled. tenantId={}, endpointId={}",
                tenantId,
                endpointId
        );

        return MerchantWebhookEndpointResponse.from(savedEndpoint, false);
    }

    private void validateUrl(String url) {
        if (!hasText(url)) {
            throw new BadRequestException("Webhook URL is required");
        }

        try {
            URI uri = URI.create(url.trim());

            if (
                    uri.getScheme() == null
                            || (!uri.getScheme().equalsIgnoreCase("http")
                            && !uri.getScheme().equalsIgnoreCase("https"))
                            || uri.getHost() == null
            ) {
                throw new BadRequestException("Webhook URL must be a valid HTTP or HTTPS URL");
            }

        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Webhook URL must be valid");
        }
    }

    private String generateSigningSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);

        return "whsec_" + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}