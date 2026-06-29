package com.markbay.subscription_engine.notification.email.template;

import java.util.Optional;

public interface EmailTemplateRegistry {

    Optional<String> findTemplate(String templateName);
}