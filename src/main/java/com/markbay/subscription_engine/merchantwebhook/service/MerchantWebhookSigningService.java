package com.markbay.subscription_engine.merchantwebhook.service;

public interface MerchantWebhookSigningService {

    String sign(
            String payload,
            String secret,
            String timestamp
    );
}