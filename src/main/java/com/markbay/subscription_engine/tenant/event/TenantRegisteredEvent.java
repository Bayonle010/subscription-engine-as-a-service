package com.markbay.subscription_engine.tenant.event;

import java.util.UUID;

public record TenantRegisteredEvent(
        UUID tenantId
) {
}
