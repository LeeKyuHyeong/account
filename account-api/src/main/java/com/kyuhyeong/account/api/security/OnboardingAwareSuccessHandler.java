package com.kyuhyeong.account.api.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 카카오 로그인 성공 후 분기 + 접속 로그 기록.
 *
 * <ul>
 *   <li>가구 있음 → {@code /web/home}</li>
 *   <li>가구 없음(가입 직후) → {@code /web/onboarding}</li>
 * </ul>
 *
 * <p>접속 로그({@link LoginLogService})는 기록 실패가 로그인을 막지 않도록 try-catch.
 *
 * <p>IP 는 {@code getRemoteAddr()} — {@code forward-headers-strategy: framework}(ForwardedHeaderFilter) 가
 * X-Forwarded-For 첫 값으로 바꿔 두고 XFF 헤더는 지운다. 위조 방지는 nginx 가 XFF 를
 * {@code $remote_addr} 로 덮어쓰는 것에 달려 있다({@code infra/nginx/account.kyuhyeong.com.conf.example}).
 */
@Component
@RequiredArgsConstructor
public class OnboardingAwareSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OnboardingAwareSuccessHandler.class);

    private final LoginLogService loginLogService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {
        String target = "/web/home";
        if (authentication.getPrincipal() instanceof AccountPrincipal p) {
            recordLogin(p, request);
            if (p.getActiveHouseholdId() == null) {
                target = "/web/onboarding";
            }
        }
        getRedirectStrategy().sendRedirect(request, response, target);
    }

    private void recordLogin(AccountPrincipal principal, HttpServletRequest request) {
        try {
            loginLogService.record(principal.getUserId(), request.getRemoteAddr(), request.getHeader("User-Agent"));
        } catch (Exception e) {
            log.warn("Failed to record login log for user {}", principal.getUserId(), e);
        }
    }
}
