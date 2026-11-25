package com.miayang.ecommerce.user.internal;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and response bodies for the user module's REST API (JSON in, JSON out). */
public final class AuthDtos {

    private AuthDtos() {
    }

    // BCrypt only uses the first 72 bytes of a password, so cap the length there.
    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String password,
            @NotBlank @Size(max = 100) String fullName) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn) {
    }

    public record UserResponse(
            Long id,
            String email,
            String fullName,
            List<String> roles,
            Instant createdAt) {

        public static UserResponse from(User user) {
            return new UserResponse(
                    user.getId(), user.getEmail(), user.getFullName(), user.roleNames(), user.getCreatedAt());
        }
    }
}
