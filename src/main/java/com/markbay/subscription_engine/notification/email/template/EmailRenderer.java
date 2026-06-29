package com.markbay.subscription_engine.notification.email.template;

import com.markbay.subscription_engine.notification.email.dto.RenderedEmail;
import com.markbay.subscription_engine.notification.email.dto.SendEmailCommand;

public interface EmailRenderer {

    RenderedEmail render(SendEmailCommand command);
}