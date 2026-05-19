package com.kyuhyeong.account.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 인증 관련 요청/응답 DTO 모음 (record).
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {
    }

    public record RefreshRequest(
            @NotBlank String refreshToken
    ) {
    }

    public record LoginResponse(
            String accessToken,
            String refreshToken
    ) {
    }

    public record MeResponse(
            Long id,
            String email,
            String name,
            List<HouseholdSummary> households
    ) {
    }

    public record HouseholdSummary(
            Long id,
            String name,
            String role
    ) {
    }
}
