package com.markbay.subscription_engine.notification.email.dto;

import lombok.Builder;

@Builder
public record EmailParam(
        String name,
        String value
) {
}