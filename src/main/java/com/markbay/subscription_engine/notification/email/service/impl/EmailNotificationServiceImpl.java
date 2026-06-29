package com.markbay.subscription_engine.notification.email.service.impl;

import com.markbay.subscription_engine.notification.email.dto.SendEmailCommand;
import com.markbay.subscription_engine.notification.email.event.EmailSendRequestedEvent;
import com.markbay.subscription_engine.notification.email.service.EmailNotificationService;
import com.markbay.subscription_engine.notification.email.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailNotificationServiceImpl implements EmailNotificationService {

    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void sendNow(SendEmailCommand command) {
        emailService.send(command);
    }

    @Override
    public void sendAsync(SendEmailCommand command) {
        eventPublisher.publishEvent(
                new EmailSendRequestedEvent(command)
        );
    }
}