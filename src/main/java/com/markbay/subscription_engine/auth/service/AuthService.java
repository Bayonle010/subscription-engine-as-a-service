package com.markbay.subscription_engine.auth.service;

import com.markbay.subscription_engine.auth.dto.*;

public interface AuthService {
    AuthResponse register(RegisterMerchantRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse refreshToken(RefreshTokenRequest refreshTokenRequest);
    void logout(String refreshToken);
    MerchantUserDto getAuthenticatedUser();
    ApiAccessTokenResponse generateApiAccessToken(
            String accountIdHeader,
            String clientId,
            String secretKey
    );

}
