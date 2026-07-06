package com.markbay.subscription_engine.subscription.service.impl;

import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.security.AuthenticatedMerchantProvider;
import com.markbay.subscription_engine.subscription.dto.response.SubscriptionAnalyticsResponse;
import com.markbay.subscription_engine.subscription.dto.response.SubscriptionResponse;
import com.markbay.subscription_engine.subscription.entity.Subscription;
import com.markbay.subscription_engine.subscription.enums.SubscriptionStatus;
import com.markbay.subscription_engine.subscription.repository.SubscriptionRepository;
import com.markbay.subscription_engine.subscription.service.SubscriptionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionQueryServiceImpl implements SubscriptionQueryService {

    private final SubscriptionRepository subscriptionRepository;
    private final AuthenticatedMerchantProvider authenticatedMerchantProvider;

    @Override
    public Page<SubscriptionResponse> getSubscriptions(
            SubscriptionStatus status,
            Pageable pageable
    ) {
        UUID tenantId = authenticatedMerchantProvider.getCurrentTenantId();

        Page<Subscription> subscriptions = status == null
                ? subscriptionRepository.findAllByTenant_Id(tenantId, pageable)
                : subscriptionRepository.findAllByTenant_IdAndStatus(
                tenantId,
                status,
                pageable
        );

        return subscriptions.map(SubscriptionResponse::from);
    }

    @Override
    public SubscriptionResponse getSubscriptionById(UUID subscriptionId) {
        UUID tenantId = authenticatedMerchantProvider.getCurrentTenantId();

        Subscription subscription = subscriptionRepository
                .findByIdAndTenant_Id(subscriptionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        return SubscriptionResponse.from(subscription);
    }

    @Override
    public SubscriptionAnalyticsResponse getSubscriptionAnalytics() {
        UUID tenantId = authenticatedMerchantProvider.getCurrentTenantId();

        long totalSubscriptions = subscriptionRepository.countByTenant_Id(tenantId);

        Map<String, Long> subscriptionsByStatus = new LinkedHashMap<>();

        for (SubscriptionStatus status : SubscriptionStatus.values()) {
            long count = subscriptionRepository.countByTenant_IdAndStatus(
                    tenantId,
                    status
            );

            subscriptionsByStatus.put(status.name(), count);
        }

        return new SubscriptionAnalyticsResponse(
                totalSubscriptions,
                subscriptionsByStatus
        );
    }
}