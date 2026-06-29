package com.markbay.subscription_engine.notification.email.service;

import com.markbay.subscription_engine.eventoutbox.entity.EventOutbox;

public interface OutboxEmailNotificationService {

    void handle(EventOutbox event);
}