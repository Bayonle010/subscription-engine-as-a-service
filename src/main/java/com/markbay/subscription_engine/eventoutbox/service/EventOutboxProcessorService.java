package com.markbay.subscription_engine.eventoutbox.service;
import java.util.UUID;

public interface EventOutboxProcessorService {

    void processDueEvents();

    void processEvent(UUID eventId);
}