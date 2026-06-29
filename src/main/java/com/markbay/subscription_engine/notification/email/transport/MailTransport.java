package com.markbay.subscription_engine.notification.email.transport;

import com.markbay.subscription_engine.notification.email.dto.RenderedEmail;
import com.markbay.subscription_engine.notification.email.dto.SendEmailCommand;

public interface MailTransport {

    void send(
            SendEmailCommand command,
            RenderedEmail renderedEmail
    );
}