package com.markbay.subscription_engine.webhook.event;

import java.util.UUID;

public record InboundWebhookReceivedEvent(
        UUID inboundWebhookEventId
) {
}
