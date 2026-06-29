package com.markbay.subscription_engine.notification.email.template;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class InMemoryEmailTemplateRegistry implements EmailTemplateRegistry {

    private final Map<String, String> templates = Map.of(
            "subscription_activated",
            """
            Hi {{customerName}},
            
            Your subscription to {{planName}} is now active.
            
            Thank you.
            """,

            "payment_succeeded",
            """
            Hi {{customerName}},
            
            Your payment of {{amount}} {{currency}} was successful.
            
            Thank you.
            """,

            "payment_failed",
            """
            Hi {{customerName}},
            
            Your payment of {{amount}} {{currency}} failed.
            
            Please update your payment method or retry payment.
            """
    );

    @Override
    public Optional<String> findTemplate(String templateName) {
        if (templateName == null || templateName.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(templates.get(templateName.trim()));
    }
}