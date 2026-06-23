package com.markbay.subscription_engine.tenant.service;

import com.markbay.subscription_engine.tenant.dto.TenantResponse;
import com.markbay.subscription_engine.tenant.dto.UpdateTenantRequest;

public interface TenantService {
    TenantResponse getCurrentTenant();
    TenantResponse updateCurrentTenant(UpdateTenantRequest request);

}
