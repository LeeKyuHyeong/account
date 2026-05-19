package com.kyuhyeong.account.api.security;

import com.kyuhyeong.account.core.tenant.HouseholdContext;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Bearer JWT 추출 → 검증 → {@link SecurityContextHolder} + {@link HouseholdContext} 바인딩.
 *
 * <p>Task 4 의 {@code HouseholdContextFilter} (X-Household-Id 헤더 기반) 를 대체. 이제 가구
 * ID 는 access 토큰 클레임에서 추출하며 임의 헤더로 가구를 위장하는 경로는 차단된다.
 *
 * <p>유효한 access 토큰만 컨텍스트를 채운다 — 토큰 없음/만료/위조는 컨텍스트 미설정으로
 * 통과시키고 {@link com.kyuhyeong.account.api.config.SecurityConfig} 의
 * {@code authorizeHttpRequests} 가 인증 필요 경로에서 401 로 차단한다.
 *
 * <p>finally 블록에서 양쪽 ThreadLocal 모두 clear — 가상 스레드 재사용 시 누수 방지.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractBearer(request);
        boolean contextSet = false;
        if (token != null) {
            try {
                JwtTokenProvider.AccessClaims claims = tokenProvider.parseAccess(token);
                if (claims.householdId() != null) {
                    HouseholdContext.set(claims.householdId());
                    contextSet = true;
                }
                SimpleGrantedAuthority authority = new SimpleGrantedAuthority(
                        "ROLE_" + (claims.role() == null ? "MEMBER" : claims.role()));
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        claims.userId(), null, List.of(authority));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException e) {
                // 검증 실패 — 컨텍스트 미설정 상태로 진행. SecurityConfig 가 401 처리.
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (contextSet) {
                HouseholdContext.clear();
            }
            SecurityContextHolder.clearContext();
        }
    }

    private String extractBearer(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            return header.substring(BEARER.length());
        }
        return null;
    }
}
