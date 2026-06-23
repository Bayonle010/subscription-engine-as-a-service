package com.markbay.subscription_engine.security;

import com.markbay.subscription_engine.common.exception.ResourceNotFoundException;
import com.markbay.subscription_engine.merchant.entity.MerchantUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.core.context.SecurityContextHolder.getContext;

@RequiredArgsConstructor
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final com.markbay.subscription_engine.merchant.repository.MerchantUserRepository merchantUserRepository;

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
        if (!"ACCESS".equals(tokenType)) {
            filterChain.doFilter(request, response);
            return;
        }

        String email = jwt.getSubject();

        if (email != null && getContext().getAuthentication() == null) {
            MerchantUser merchantUser = merchantUserRepository.findByEmailIgnoreCase(email)
                    .orElseThrow(() -> new ResourceNotFoundException("Merchant user not found"));

            Long tokenSessionVersion = jwt.getClaim("sessionVersion");

            if (tokenSessionVersion == null ||
                    !tokenSessionVersion.equals(merchantUser.getSessionVersion())) {
                throw new RuntimeException("Invalid or expired session");
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
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();

        return uri.equals("/api/v1/auth/register")
                || uri.equals("/api/v1/auth/login")
                || uri.equals("/api/v1/auth/refresh-token")
                || uri.equals("/api/v1/auth/logout")
                || uri.startsWith("/swagger-ui/")
                || uri.startsWith("/v3/api-docs")
                || uri.equals("/swagger-ui.html")
                || uri.startsWith("/webhooks/nomba/");
    }
}