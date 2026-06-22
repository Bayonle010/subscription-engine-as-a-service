package com.markbay.subscription_engine.auth.service;

import com.markbay.subscription_engine.auth.dto.AuthResponse;
import com.markbay.subscription_engine.auth.dto.LoginRequest;
import com.markbay.subscription_engine.auth.dto.RegisterMerchantRequest;

public interface AuthService {
    AuthResponse register(RegisterMerchantRequest request);
    AuthResponse login(LoginRequest request);
}
