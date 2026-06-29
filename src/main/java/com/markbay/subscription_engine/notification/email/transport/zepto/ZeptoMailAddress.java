package com.markbay.subscription_engine.notification.email.transport.zepto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ZeptoMailAddress(
        String address,
        String name
) {
}