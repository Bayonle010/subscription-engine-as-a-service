package com.markbay.subscription_engine.eventoutbox.dto;

import com.markbay.subscription_engine.eventoutbox.enums.EventOutboxType;
import com.markbay.subscription_engine.tenant.entity.Tenant;
import lombok.Builder;

@Builder
public record CreateEventOutboxCommand(
        Tenant tenant,
        EventOutboxType eventType,
        String eventReference,
        String aggregateType,
        String aggregateId,
        Object payload
) {
}