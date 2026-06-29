package com.markbay.subscription_engine.notification.email.service;

import com.markbay.subscription_engine.notification.email.dto.SendEmailCommand;

public interface EmailService {

    void send(SendEmailCommand command);
}