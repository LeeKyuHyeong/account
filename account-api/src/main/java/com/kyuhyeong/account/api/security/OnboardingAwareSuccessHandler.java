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
            String ip = LoginLogService.resolveClientIp(
                    request.getHeader("X-Forwarded-For"), request.getRemoteAddr());
            loginLogService.record(principal.getUserId(), ip, request.getHeader("User-Agent"));
        } catch (Exception e) {
            log.warn("Failed to record login log for user {}", principal.getUserId(), e);
        }
    }
}
