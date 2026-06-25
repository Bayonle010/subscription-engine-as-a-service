package com.markbay.subscription_engine.auth.controller;

import com.markbay.subscription_engine.auth.dto.*;
import com.markbay.subscription_engine.auth.service.AuthService;
import com.markbay.subscription_engine.common.response.ApiResponse;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterMerchantRequest request
    ) {
        AuthResponse response = authService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseUtil.success(
                        00,
                        "Merchant registered successfully",
                        null,
                        response,
                        null
                ));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthResponse response = authService.login(request);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        00,
                        "Login successful",
                        null,
                        response,
                        null
                )
        );
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        AuthResponse response = authService.refreshToken(request);

        return ResponseEntity.ok(
                ResponseUtil.success(
                        0,
                        "Access token refreshed successfully",
                        null,
                        response,
                        null
                )
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Object>> logout(
            @Valid @RequestBody LogoutRequest request
    ) {
        authService.logout(request.refreshToken());

        return ResponseEntity.ok(
                ResponseUtil.success(
                        0,
                        "Logout successful",
                        null,
                        null,
                        null
                )
        );
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'DEVELOPER', 'SUPPORT')")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MerchantUserDto>> me() {
        MerchantUserDto response = authService.getAuthenticatedUser();

        return ResponseEntity.ok(
                ResponseUtil.success(
                        0,
                        "Authenticated merchant user retrieved successfully",
                        null,
                        response,
                        null
                )
        );
    }

    @PostMapping("/token")
    public ResponseEntity<ApiResponse<ApiAccessTokenResponse>> generateApiAccessToken(
            @RequestHeader(value = "accountId", required = false) String accountId,
            @RequestHeader(value = "clientId", required = false) String clientId,
            @RequestHeader(value = "secretKey", required = false) String secretKey
    ) {
        ApiAccessTokenResponse response = authService.generateApiAccessToken(
                accountId,
                clientId,
                secretKey
        );

        return ResponseEntity.ok(
                ResponseUtil.success(
                        "API access token generated successfully",
                        response
                )
        );
    }


}