package com.kyuhyeong.account.api.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 카카오 로그인 성공 후 분기.
 *
 * <ul>
 *   <li>가구 있음 → {@code /web/home}</li>
 *   <li>가구 없음(가입 직후) → {@code /web/onboarding}</li>
 * </ul>
 */
@Component
public class OnboardingAwareSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {
        String target = "/web/home";
        if (authentication.getPrincipal() instanceof AccountPrincipal p
                && p.getActiveHouseholdId() == null) {
            target = "/web/onboarding";
        }
        getRedirectStrategy().sendRedirect(request, response, target);
    }
}
