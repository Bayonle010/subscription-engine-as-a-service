package com.markbay.subscription_engine.notification.email.service.impl;

import com.markbay.subscription_engine.common.exception.BadRequestException;
import com.markbay.subscription_engine.notification.email.dto.RenderedEmail;
import com.markbay.subscription_engine.notification.email.dto.SendEmailCommand;
import com.markbay.subscription_engine.notification.email.service.EmailService;
import com.markbay.subscription_engine.notification.email.template.EmailRenderer;
import com.markbay.subscription_engine.notification.email.transport.MailTransport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final EmailRenderer emailRenderer;
    private final MailTransport mailTransport;

    @Override
    public void send(SendEmailCommand command) {
        validate(command);

        RenderedEmail renderedEmail = emailRenderer.render(command);

        mailTransport.send(command, renderedEmail);
    }

    private void validate(SendEmailCommand command) {
        if (command == null) {
            throw new BadRequestException("Email command is required");
        }

        if (command.to() == null || command.to().isEmpty()) {
            throw new BadRequestException("Email recipient is required");
        }

        if (!hasText(command.subject())) {
            throw new BadRequestException("Email subject is required");
        }

        if (!hasText(command.messageContentOrTemplateName())) {
            throw new BadRequestException("Email content is required");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}