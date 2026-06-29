package com.markbay.subscription_engine.notification.email.transport.zepto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ZeptoMailRecipient(
        @JsonProperty("email_address")
        ZeptoMailAddress emailAddress
) {
}