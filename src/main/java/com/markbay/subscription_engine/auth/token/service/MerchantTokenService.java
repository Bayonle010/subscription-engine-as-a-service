package com.markbay.subscription_engine.auth.token.service;

import com.markbay.subscription_engine.merchant.entity.MerchantUser;

public interface MerchantTokenService {
    void saveRefreshToken(MerchantUser merchantUser, String refreshToken);

    void revokeAllUserRefreshTokens(MerchantUser merchantUser);

    void validateRefreshToken(String refreshToken, MerchantUser merchantUser);

    String getTokenIdFromRefreshToken(String refreshToken);

    void revokeRefreshToken(String refreshToken);

}
