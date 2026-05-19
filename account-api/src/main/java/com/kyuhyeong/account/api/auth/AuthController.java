package com.kyuhyeong.account.api.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 엔드포인트 (docs/account.md §7.1).
 *
 * <ul>
 *   <li>POST /api/auth/login   — 로그인, access+refresh 발급</li>
 *   <li>POST /api/auth/refresh — refresh 로 access 재발급</li>
 *   <li>GET  /api/auth/me      — 현재 사용자 + 소속 가구 목록</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public AuthDtos.LoginResponse login(@Valid @RequestBody AuthDtos.LoginRequest req) {
        return authService.login(req.email(), req.password());
    }

    @PostMapping("/refresh")
    public AuthDtos.LoginResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest req) {
        return authService.refresh(req.refreshToken());
    }

    @GetMapping("/me")
    public AuthDtos.MeResponse me(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return authService.me(userId);
    }
}
