package com.markbay.subscription_engine.customerportal.service;

import com.markbay.subscription_engine.customerportal.dto.CustomerPortalTokenPair;

public interface CustomerPortalTokenService {

    CustomerPortalTokenPair generateToken();

    String hashToken(String rawToken);
}