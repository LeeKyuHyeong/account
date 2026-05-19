package com.kyuhyeong.account.api.auth;

import com.kyuhyeong.account.api.security.JwtTokenProvider;
import com.kyuhyeong.account.core.entity.HouseholdMember;
import com.kyuhyeong.account.core.entity.User;
import com.kyuhyeong.account.core.repository.HouseholdMemberRepository;
import com.kyuhyeong.account.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 로그인 / 토큰 재발급 / 사용자 정보 조회 비즈니스 로직.
 *
 * <p>로그인 흐름:
 * <ol>
 *   <li>email 로 사용자 조회 — 없으면 BadCredentialsException</li>
 *   <li>password BCrypt 검증 — 실패 시 동일 예외 (사용자 존재 여부 노출 X)</li>
 *   <li>가입된 가구 목록 조회. 빈 목록은 데이터 정합성 오류 — IllegalStateException</li>
 *   <li>MVP 는 첫 번째 가구 자동 활성화 (v1.5 에서 switch-household API 로 변경)</li>
 *   <li>access + refresh 토큰 발급, last_login_at 갱신</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final HouseholdMemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Transactional
    public AuthDtos.LoginResponse login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        List<HouseholdMember> memberships = memberRepository.findByUserId(user.getId());
        if (memberships.isEmpty()) {
            throw new IllegalStateException("User " + user.getId() + " has no household membership");
        }
        HouseholdMember active = memberships.get(0);

        String access = tokenProvider.issueAccessToken(
                user.getId(), active.getHousehold().getId(), active.getRole().name());
        String refresh = tokenProvider.issueRefreshToken(user.getId());
        user.touchLogin();

        return new AuthDtos.LoginResponse(access, refresh);
    }

    @Transactional
    public AuthDtos.LoginResponse refresh(String refreshToken) {
        Long userId = tokenProvider.parseRefreshUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User no longer exists"));
        List<HouseholdMember> memberships = memberRepository.findByUserId(userId);
        if (memberships.isEmpty()) {
            throw new IllegalStateException("User " + userId + " has no household membership");
        }
        HouseholdMember active = memberships.get(0);
        String access = tokenProvider.issueAccessToken(
                user.getId(), active.getHousehold().getId(), active.getRole().name());
        // refresh 는 재발급 안 함 (rotation 정책은 운영 단계에서 결정).
        return new AuthDtos.LoginResponse(access, refreshToken);
    }

    @Transactional(readOnly = true)
    public AuthDtos.MeResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User no longer exists"));
        List<AuthDtos.HouseholdSummary> households = memberRepository.findByUserId(userId).stream()
                .map(m -> new AuthDtos.HouseholdSummary(
                        m.getHousehold().getId(),
                        m.getHousehold().getName(),
                        m.getRole().name()))
                .toList();
        return new AuthDtos.MeResponse(user.getId(), user.getEmail(), user.getName(), households);
    }
}
