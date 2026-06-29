package com.markbay.subscription_engine.merchantwebhook.service;

import com.markbay.subscription_engine.eventoutbox.entity.EventOutbox;

public interface MerchantWebhookDeliveryService {

    void dispatchEvent(EventOutbox event);
}