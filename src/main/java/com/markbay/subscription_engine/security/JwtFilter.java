package com.markbay.subscription_engine.security;

import com.markbay.subscription_engine.apiKey.entity.ApiKey;
import com.markbay.subscription_engine.apiKey.enums.ApiKeyStatus;
import com.markbay.subscription_engine.apiKey.repository.ApiKeyRepository;
import com.markbay.subscription_engine.merchant.entity.MerchantUser;
import com.markbay.subscription_engine.merchant.repository.MerchantUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.core.context.SecurityContextHolder.getContext;

@RequiredArgsConstructor
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final MerchantUserRepository merchantUserRepository;
    private final ApiKeyRepository apiKeyRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        Jwt jwt = jwtService.decodeJwt(token);

        String tokenType = jwt.getClaimAsString("tokenType");

        if ("ACCESS".equals(tokenType)) {
            authenticateMerchantUser(jwt, request);
            filterChain.doFilter(request, response);
            return;
        }

        if ("API_ACCESS".equals(tokenType)) {
            authenticateApiClient(jwt, request);
            filterChain.doFilter(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateMerchantUser(
            Jwt jwt,
            HttpServletRequest request
    ) {
        String email = jwt.getSubject();

        if (email == null || email.isBlank()) {
            throw new BadCredentialsException("Invalid access token");
        }

        MerchantUser merchantUser = merchantUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Merchant user not found"));

        Long tokenSessionVersion = jwt.getClaim("sessionVersion");

        if (tokenSessionVersion == null ||
                !tokenSessionVersion.equals(merchantUser.getSessionVersion())) {
            throw new BadCredentialsException("Invalid or expired session");
        }

        MerchantPrincipal principal = new MerchantPrincipal(merchantUser);

        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );

        authenticationToken.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
        );

        getContext().setAuthentication(authenticationToken);

        AuthenticationIdentity authenticationIdentity =
                jwtService.buildAuthenticationIdentity(jwt);

        request.setAttribute("AUTH_IDENTITY", authenticationIdentity);
        request.setAttribute("AUTH_TYPE", "MERCHANT_USER");
        request.setAttribute("TENANT_ID", merchantUser.getTenant().getId());
    }

    private void authenticateApiClient(
            Jwt jwt,
            HttpServletRequest request
    ) {
        String apiKeyIdValue = jwt.getClaimAsString("apiKeyId");

        if (apiKeyIdValue == null || apiKeyIdValue.isBlank()) {
            throw new BadCredentialsException("Invalid API access token");
        }

        UUID apiKeyId;

        try {
            apiKeyId = UUID.fromString(apiKeyIdValue);
        } catch (IllegalArgumentException exception) {
            throw new BadCredentialsException("Invalid API access token");
        }

        ApiKey apiKey = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new BadCredentialsException("API key not found"));

        if (apiKey.getStatus() != ApiKeyStatus.ACTIVE) {
            throw new BadCredentialsException("API key has been revoked");
        }

        String tokenClientId = jwt.getClaimAsString("clientId");

        if (tokenClientId == null ||
                !tokenClientId.equals(apiKey.getClientId())) {
            throw new BadCredentialsException("API access token client mismatch");
        }

        String tokenTenantId = jwt.getClaimAsString("tenantId");

        if (tokenTenantId == null ||
                !tokenTenantId.equals(apiKey.getTenant().getId().toString())) {
            throw new BadCredentialsException("API access token tenant mismatch");
        }

        ApiKeyPrincipal principal = new ApiKeyPrincipal(apiKey);

        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT"))
                );

        authenticationToken.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
        );

        getContext().setAuthentication(authenticationToken);

        request.setAttribute("AUTH_TYPE", "API_CLIENT");
        request.setAttribute("API_KEY_ID", apiKey.getId());
        request.setAttribute("TENANT_ID", apiKey.getTenant().getId());
        request.setAttribute("ACCOUNT_ID", apiKey.getTenant().getId());
        request.setAttribute("CLIENT_ID", apiKey.getClientId());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();

        return uri.equals("/api/v1/auth/register")
                || uri.equals("/api/v1/auth/login")
                || uri.equals("/api/v1/auth/refresh-token")
                || uri.equals("/api/v1/auth/logout")
                || uri.equals("/api/v1/auth/token")
                || uri.startsWith("/swagger-ui/")
                || uri.startsWith("/v3/api-docs")
                || uri.equals("/swagger-ui.html")
                || uri.equals("/api/v1/customer-portal/sessions/")
                || uri.startsWith("/webhooks/nomba/");
    }
}