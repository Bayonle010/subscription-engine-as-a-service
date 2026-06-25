package com.markbay.subscription_engine.nomba.service.impl;

import com.markbay.subscription_engine.nomba.dto.request.NombaIssueTokenRequest;
import com.markbay.subscription_engine.nomba.dto.request.NombaRefreshTokenRequest;
import com.markbay.subscription_engine.nomba.dto.response.NombaApiResponse;
import com.markbay.subscription_engine.nomba.dto.response.NombaTokenData;
import com.markbay.subscription_engine.nomba.exception.NombaApiException;
import com.markbay.subscription_engine.nomba.service.NombaAuthService;
import com.markbay.subscription_engine.nomba.support.NombaRestClientErrorHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;

@Slf4j
@Service
public class NombaAuthServiceImpl implements NombaAuthService {

    private final RestClient nombaRestClient;
    private final String clientId;
    private final String clientSecret;
    private final NombaRestClientErrorHandler nombaErrorHandler;

    private volatile String accessToken;
    private volatile String cachedRefreshToken;
    private volatile Instant accessTokenExpiresAt;

    public NombaAuthServiceImpl(
            @Qualifier("nombaRestClient") RestClient nombaRestClient,
            @Value("${payment.nomba.client-id}") String clientId,
            @Value("${payment.nomba.client-secret}") String clientSecret,
            NombaRestClientErrorHandler nombaErrorHandler
    ) {
        this.nombaRestClient = nombaRestClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.nombaErrorHandler = nombaErrorHandler;
    }

    @Override
    public String getAccessToken() {
        if (accessToken == null || accessToken.isBlank() || isAccessTokenExpiredOrNearExpiry()) {
            synchronized (this) {
                if (accessToken == null || accessToken.isBlank() || isAccessTokenExpiredOrNearExpiry()) {
                    refreshOrReissueToken();
                }
            }
        }

        return accessToken;
    }

    private boolean isAccessTokenExpiredOrNearExpiry() {
        return accessTokenExpiresAt == null
                || Instant.now().isAfter(accessTokenExpiresAt.minusSeconds(300));
    }

    private void refreshOrReissueToken() {
        try {
            if (cachedRefreshToken != null && !cachedRefreshToken.isBlank()) {
                refreshToken();
                return;
            }

            issueToken();
        } catch (Exception exception) {
            log.warn(
                    "Nomba token refresh failed. Falling back to issue token. reason={}",
                    exception.getMessage()
            );

            issueToken();
        }
    }

    @Override
    public synchronized void issueToken() {
        try {
            log.info("Issuing new Nomba access token");

            NombaIssueTokenRequest request = new NombaIssueTokenRequest(
                    "client_credentials",
                    clientId,
                    clientSecret
            );

            NombaApiResponse<NombaTokenData> response = nombaRestClient.post()
                    .uri("/v1/auth/token/issue")
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, nombaErrorHandler::handle)
                    .body(new ParameterizedTypeReference<>() {
                    });

            validateAndCacheToken(response, "issue");

            log.info("Nomba access token issued and cached successfully");

        } catch (NombaApiException exception) {
            throw exception;

        } catch (ResourceAccessException exception) {
            log.error(
                    "Nomba issue token network error. message={}",
                    exception.getMessage()
            );

            throw new NombaApiException("Nomba issue token network error", exception);

        } catch (Exception exception) {
            log.error("Nomba issue token failed", exception);

            throw new NombaApiException("Nomba issue token unexpected error", exception);
        }
    }

    @Override
    public synchronized void refreshToken() {
        if (cachedRefreshToken == null || cachedRefreshToken.isBlank()) {
            throw new NombaApiException("No Nomba refresh token available");
        }

        try {
            log.info("Refreshing Nomba access token");

            NombaRefreshTokenRequest request = new NombaRefreshTokenRequest(
                    "refresh_token",
                    cachedRefreshToken
            );

            NombaApiResponse<NombaTokenData> response = nombaRestClient.post()
                    .uri("/v1/auth/token/refresh")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, nombaErrorHandler::handle)
                    .body(new ParameterizedTypeReference<>() {
                    });

            validateAndCacheToken(response, "refresh");

            log.info("Nomba access token refreshed and cached successfully");

        } catch (NombaApiException exception) {
            throw exception;
            
        } catch (ResourceAccessException exception) {
            log.error(
                    "Nomba refresh token network error. message={}",
                    exception.getMessage()
            );

            throw new NombaApiException("Nomba refresh token network error", exception);

        } catch (Exception exception) {
            log.error("Nomba refresh token failed", exception);

            throw new NombaApiException("Nomba refresh token failed", exception);
        }
    }

    private void validateAndCacheToken(
            NombaApiResponse<NombaTokenData> response,
            String operation
    ) {
        if (response == null) {
            throw new NombaApiException("Nomba " + operation + " token response is null");
        }

        if (!response.isSuccessful()) {
            throw new NombaApiException(
                    "Nomba " + operation + " token failed: " + response.description()
            );
        }

        NombaTokenData data = response.data();

        if (data == null) {
            throw new NombaApiException("Nomba " + operation + " token response missing data");
        }

        if (data.accessToken() == null || data.accessToken().isBlank()) {
            throw new NombaApiException("Nomba " + operation + " token response missing access token");
        }

        this.accessToken = data.accessToken();

        if (data.refreshToken() != null && !data.refreshToken().isBlank()) {
            this.cachedRefreshToken = data.refreshToken();
        }

        if (data.expiresAt() != null) {
            this.accessTokenExpiresAt = data.expiresAt();
        } else if (data.expiresIn() != null && data.expiresIn() > 0) {
            this.accessTokenExpiresAt = Instant.now().plusSeconds(data.expiresIn());
        } else {
            throw new NombaApiException("Nomba " + operation + " token response missing expiry");
        }

        log.debug("Nomba token cached. expiresAt={}", accessTokenExpiresAt);
    }
}