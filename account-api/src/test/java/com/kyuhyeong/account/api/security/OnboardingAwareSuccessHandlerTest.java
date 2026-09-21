package com.kyuhyeong.account.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * {@link OnboardingAwareSuccessHandler} 접속 로그 IP — 운영과 같은 순서로
 * {@link ForwardedHeaderFilter}({@code forward-headers-strategy: framework}) 를 먼저 통과시킨다.
 *
 * <p>이 필터는 {@code getRemoteAddr()} 를 X-Forwarded-For <b>첫 값</b>으로 바꾸고 XFF 헤더를 하류에서 지운다.
 * 그래서 위조 방지는 nginx 가 XFF 를 {@code $remote_addr} 로 <b>덮어쓰는</b> 쪽이 맡는다
 * (records/2026-09-21_account-d-investigation §2-1).
 */
@ExtendWith(MockitoExtension.class)
class OnboardingAwareSuccessHandlerTest {

    private static final String REAL_IP = "203.0.113.9";

    @Mock LoginLogService loginLogService;

    @Test
    @DisplayName("nginx 가 XFF 를 실 IP 로 덮어쓴 요청 — 실 IP 가 기록된다")
    void recordsRealIpWhenNginxOverwritesForwardedFor() throws Exception {
        MockHttpServletRequest req = proxiedRequest(REAL_IP);

        loginThroughForwardedFilter(req);

        verify(loginLogService).record(eq(1L), eq(REAL_IP), eq("Mozilla/5.0"));
    }

    @Test
    @DisplayName("nginx 가 XFF 를 덧붙이면 클라이언트 위조값이 기록된다 — nginx 덮어쓰기가 필요한 이유")
    void appendedForwardedForLetsClientSpoofIp() throws Exception {
        MockHttpServletRequest req = proxiedRequest("1.2.3.4, " + REAL_IP);

        loginThroughForwardedFilter(req);

        verify(loginLogService).record(eq(1L), eq("1.2.3.4"), eq("Mozilla/5.0"));
    }

    @Test
    @DisplayName("프록시 없이 직접 접속(로컬) — remoteAddr 가 기록된다")
    void recordsRemoteAddrWithoutProxyHeaders() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/login/oauth2/code/kakao");
        req.setRemoteAddr("127.0.0.1");
        req.addHeader("User-Agent", "Mozilla/5.0");

        loginThroughForwardedFilter(req);

        verify(loginLogService).record(eq(1L), eq("127.0.0.1"), eq("Mozilla/5.0"));
    }

    @Test
    @DisplayName("가구 없는 사용자는 온보딩으로 보낸다")
    void redirectsUserWithoutHouseholdToOnboarding() throws Exception {
        MockHttpServletRequest req = proxiedRequest(REAL_IP);
        MockHttpServletResponse res = new MockHttpServletResponse();
        OnboardingAwareSuccessHandler handler = new OnboardingAwareSuccessHandler(loginLogService);
        AccountPrincipal noHousehold = new AccountPrincipal(1L, null, null, "닉네임", false, Map.of("id", 12345L));

        new ForwardedHeaderFilter().doFilter(req, res, (rq, rs) -> handler.onAuthenticationSuccess(
                (HttpServletRequest) rq, (HttpServletResponse) rs, new TestingAuthenticationToken(noHousehold, null)));

        assertThat(res.getRedirectedUrl()).endsWith("/web/onboarding");
    }

    /** nginx 뒤 요청 모양 — remoteAddr 는 nginx(127.0.0.1), 실 IP 는 헤더로. */
    private static MockHttpServletRequest proxiedRequest(String forwardedFor) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/login/oauth2/code/kakao");
        req.setServerName("account.kyuhyeong.com");
        req.setScheme("https");
        req.setServerPort(443);
        req.setRemoteAddr("127.0.0.1");
        req.addHeader("X-Forwarded-For", forwardedFor);
        req.addHeader("X-Real-IP", REAL_IP);
        req.addHeader("X-Forwarded-Proto", "https");
        req.addHeader("User-Agent", "Mozilla/5.0");
        return req;
    }

    private void loginThroughForwardedFilter(MockHttpServletRequest req) throws Exception {
        OnboardingAwareSuccessHandler handler = new OnboardingAwareSuccessHandler(loginLogService);
        AccountPrincipal principal = new AccountPrincipal(1L, 10L, "OWNER", "닉네임", false, Map.of("id", 12345L));
        new ForwardedHeaderFilter().doFilter(req, new MockHttpServletResponse(), (rq, rs) -> handler.onAuthenticationSuccess(
                (HttpServletRequest) rq, (HttpServletResponse) rs, new TestingAuthenticationToken(principal, null)));
    }
}
