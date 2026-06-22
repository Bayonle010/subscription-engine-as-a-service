package com.markbay.subscription_engine.auth.controller;

import com.markbay.subscription_engine.auth.dto.AuthResponse;
import com.markbay.subscription_engine.auth.dto.LoginRequest;
import com.markbay.subscription_engine.auth.dto.RegisterMerchantRequest;
import com.markbay.subscription_engine.auth.service.AuthService;
import com.markbay.subscription_engine.common.response.ApiResponse;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}