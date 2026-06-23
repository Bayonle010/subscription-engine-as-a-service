package com.markbay.subscription_engine.security;


import com.markbay.subscription_engine.apiKey.entitty.ApiKey;
import com.markbay.subscription_engine.apiKey.service.ApiKeyService;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.core.context.SecurityContextHolder.getContext;

@RequiredArgsConstructor
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String ACCOUNT_ID_HEADER = "accountId";
    public static final String CLIENT_ID_HEADER = "clientId";
    public static final String SECRET_KEY_HEADER = "secretKey";

    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

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

        String accountIdHeader = request.getHeader(ACCOUNT_ID_HEADER);
        String clientId = request.getHeader(CLIENT_ID_HEADER);
        String secretKey = request.getHeader(SECRET_KEY_HEADER);

        boolean apiKeyHeadersPresent =
                hasText(accountIdHeader) || hasText(clientId) || hasText(secretKey);

        if (!apiKeyHeadersPresent) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!hasText(accountIdHeader) || !hasText(clientId) || !hasText(secretKey)) {
            writeUnauthorizedResponse(
                    response,
                    "Missing API authentication headers",
                    "accountId, clientId, and secretKey headers are required"
            );
            return;
        }

        try {
            UUID accountId = UUID.fromString(accountIdHeader.trim());

            ApiKey apiKey = apiKeyService.authenticateApiKey(
                    accountId,
                    clientId.trim(),
                    secretKey.trim()
            );

            ApiKeyPrincipal principal = new ApiKeyPrincipal(apiKey);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT"))
                    );

            getContext().setAuthentication(authentication);

            request.setAttribute("ACCOUNT_ID", accountId);
            request.setAttribute("TENANT_ID", accountId);
            request.setAttribute("CLIENT_ID", clientId);

            filterChain.doFilter(request, response);

        } catch (IllegalArgumentException exception) {
            writeUnauthorizedResponse(
                    response,
                    "Invalid accountId",
                    "accountId must be a valid UUID"
            );
        } catch (BadCredentialsException exception) {
            writeUnauthorizedResponse(
                    response,
                    "Invalid API credentials",
                    "The supplied accountId, clientId, or secretKey is invalid"
            );
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();

        return uri.startsWith("/api/v1/auth/")
                || uri.startsWith("/swagger-ui/")
                || uri.startsWith("/v3/api-docs")
                || uri.equals("/swagger-ui.html")
                || uri.startsWith("/webhooks/nomba/");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private void writeUnauthorizedResponse(
            HttpServletResponse response,
            String message,
            Object details
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        objectMapper.writeValue(
                response.getOutputStream(),
                ResponseUtil.error(
                        401,
                        message,
                        details
                )
        );
    }
}