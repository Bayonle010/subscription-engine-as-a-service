package com.markbay.subscription_engine.nomba.dto.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NombaTokenData(

        @JsonAlias({"accessToken", "access_token"})
        String accessToken,

        @JsonAlias({"refreshToken", "refresh_token"})
        String refreshToken,

        @JsonAlias({"expiresAt", "expires_at"})
        Instant expiresAt,

        @JsonAlias({"expiresIn", "expires_in"})
        Long expiresIn
) {
}