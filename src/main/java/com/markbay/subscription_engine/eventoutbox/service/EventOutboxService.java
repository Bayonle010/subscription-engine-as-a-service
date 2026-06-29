package com.markbay.subscription_engine.eventoutbox.service;

import com.markbay.subscription_engine.eventoutbox.dto.CreateEventOutboxCommand;
import com.markbay.subscription_engine.eventoutbox.entity.EventOutbox;

public interface EventOutboxService {

    EventOutbox recordEvent(CreateEventOutboxCommand command);
}