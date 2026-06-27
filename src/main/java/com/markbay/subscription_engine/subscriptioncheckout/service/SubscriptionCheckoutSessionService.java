package com.markbay.subscription_engine.subscriptioncheckout.service;

import com.markbay.subscription_engine.subscriptioncheckout.dto.CreateSubscriptionCheckoutSessionRequest;
import com.markbay.subscription_engine.subscriptioncheckout.dto.SubscriptionCheckoutSessionResponse;

import java.util.UUID;

public interface SubscriptionCheckoutSessionService {

    SubscriptionCheckoutSessionResponse createCheckoutSession(
            CreateSubscriptionCheckoutSessionRequest request
    );

    SubscriptionCheckoutSessionResponse getCheckoutSession(UUID sessionId);
}