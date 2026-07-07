package com.markbay.subscription_engine.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterMerchantRequest(

        @NotBlank(message = "Business name is required")
        String businessName,

        @NotBlank(message = "Business email is required")
        @Email(message = "Business email must be valid")
        String businessEmail,

        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @Size(min = 8, message = "Password must be at least 8 characters long")
        @Pattern(
                regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*()\\-_=+{}\\[\\]|;:'\",.<>?/`~])(?=\\S+$).{8,}$",
                message = "Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character")
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password
) {
}