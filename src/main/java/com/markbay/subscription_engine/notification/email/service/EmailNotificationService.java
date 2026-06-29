package com.markbay.subscription_engine.notification.email.service;

import com.markbay.subscription_engine.notification.email.dto.SendEmailCommand;

public interface EmailNotificationService {

    void sendNow(SendEmailCommand command);

    void sendAsync(SendEmailCommand command);
}