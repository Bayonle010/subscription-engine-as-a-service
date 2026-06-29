package com.markbay.subscription_engine.notification.email.event;

import com.markbay.subscription_engine.notification.email.dto.SendEmailCommand;

public record EmailSendRequestedEvent(
        SendEmailCommand command
) {
}
