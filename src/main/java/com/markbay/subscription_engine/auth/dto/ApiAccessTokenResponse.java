package com.markbay.subscription_engine.auth.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiAccessTokenResponse {
    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private UUID accountId;
    private String clientId;
    private String mode;
}