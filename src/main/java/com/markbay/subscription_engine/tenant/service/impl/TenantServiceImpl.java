package com.markbay.subscription_engine.tenant.service.impl;

import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.security.AuthenticatedMerchantProvider;
import com.markbay.subscription_engine.tenant.dto.TenantResponse;
import com.markbay.subscription_engine.tenant.dto.UpdateTenantRequest;
import com.markbay.subscription_engine.tenant.entity.Tenant;
import com.markbay.subscription_engine.tenant.repository.TenantRepository;
import com.markbay.subscription_engine.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@RequiredArgsConstructor
@Service
public class TenantServiceImpl implements TenantService {
    private final TenantRepository tenantRepository;
    private final AuthenticatedMerchantProvider merchantProvider;


    @Transactional(readOnly = true)
    @Override
    public TenantResponse getCurrentTenant() {
        UUID tenantId = merchantProvider.getCurrentTenantId();

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        return TenantResponse.from(tenant);
    }


    @Transactional
    @Override
    public TenantResponse updateCurrentTenant(UpdateTenantRequest request) {
        UUID tenantId = merchantProvider.getCurrentTenantId();

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        if (hasText(request.businessName())) {
            tenant.setBusinessName(request.businessName().trim());
        }

        if (hasText(request.supportEmail())) {
            tenant.setSupportEmail(request.supportEmail().trim().toLowerCase());
        }

        if (hasText(request.logoUrl())) {
            tenant.setLogoUrl(request.logoUrl().trim());
        }

        if (hasText(request.primaryColor())) {
            tenant.setPrimaryColor(request.primaryColor().trim());
        }

        if (hasText(request.defaultCurrency())) {
            tenant.setDefaultCurrency(request.defaultCurrency().trim().toUpperCase());
        }

        if (hasText(request.billingTimezone())) {
            tenant.setBillingTimezone(request.billingTimezone().trim());
        }

        Tenant savedTenant = tenantRepository.save(tenant);

        return TenantResponse.from(savedTenant);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
