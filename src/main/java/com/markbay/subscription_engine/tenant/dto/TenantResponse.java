package com.markbay.subscription_engine.tenant.dto;



import com.markbay.subscription_engine.tenant.entity.Tenant;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID accountId,
        UUID tenantId,
        String businessName,
        String businessEmail,
        String supportEmail,
        String logoUrl,
        String primaryColor,
        String defaultCurrency,
        String billingTimezone,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getId(),
                tenant.getBusinessName(),
                tenant.getBusinessEmail(),
                tenant.getSupportEmail(),
                tenant.getLogoUrl(),
                tenant.getPrimaryColor(),
                tenant.getDefaultCurrency(),
                tenant.getBillingTimezone(),
                tenant.getStatus().name(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }
}