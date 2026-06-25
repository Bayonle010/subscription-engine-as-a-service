package com.markbay.subscription_engine.nomba.service;

public interface NombaAuthService {

    String getAccessToken();

    void issueToken();

    void refreshToken();
}