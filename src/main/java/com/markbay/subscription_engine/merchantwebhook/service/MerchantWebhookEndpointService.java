package com.markbay.subscription_engine.merchantwebhook.service;

import com.markbay.subscription_engine.merchantwebhook.dto.CreateMerchantWebhookEndpointRequest;
import com.markbay.subscription_engine.merchantwebhook.dto.MerchantWebhookEndpointResponse;
import com.markbay.subscription_engine.merchantwebhook.dto.UpdateMerchantWebhookEndpointRequest;

import java.util.List;
import java.util.UUID;

public interface MerchantWebhookEndpointService {

    MerchantWebhookEndpointResponse createEndpoint(
            CreateMerchantWebhookEndpointRequest request
    );

    List<MerchantWebhookEndpointResponse> listEndpoints();

    MerchantWebhookEndpointResponse updateEndpoint(
            UUID endpointId,
            UpdateMerchantWebhookEndpointRequest request
    );

    MerchantWebhookEndpointResponse disableEndpoint(UUID endpointId);
}