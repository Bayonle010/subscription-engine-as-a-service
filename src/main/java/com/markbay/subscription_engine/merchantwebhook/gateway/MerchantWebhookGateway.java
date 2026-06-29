package com.markbay.subscription_engine.merchantwebhook.gateway;

import com.markbay.subscription_engine.merchantwebhook.dto.MerchantWebhookDispatchResult;
import com.markbay.subscription_engine.merchantwebhook.entity.MerchantWebhookEndpoint;

public interface MerchantWebhookGateway {

    MerchantWebhookDispatchResult send(
            MerchantWebhookEndpoint endpoint,
            String payloadJson,
            String eventName,
            String deliveryReference
    );
}