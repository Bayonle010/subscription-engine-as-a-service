package com.markbay.subscription_engine.notification.email.dto;

public record RenderedEmail(
        String subject,
        String textBody,
        String htmlBody
) {
}