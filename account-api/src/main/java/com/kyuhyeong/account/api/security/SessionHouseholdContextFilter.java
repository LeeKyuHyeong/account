package com.kyuhyeong.account.api.security;

import com.kyuhyeong.account.core.tenant.HouseholdContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 세션 인증 경로(/web/**) 용 {@link HouseholdContext} 주입 + 온보딩 가드 필터.
 *
 * <p>세션에 저장된 {@link AccountPrincipal} 의 activeHouseholdId 를 꺼내 {@code HouseholdContext}
 * 에 바인딩한다. finally 블록에서 ThreadLocal clear — 가상 스레드 재사용 시 누수 방지.
 *
 * <p>가구 미가입(activeHouseholdId=null) 유저가 {@code /web/**}(온보딩 제외) 에 접근하면
 * {@code /web/onboarding} 으로 리다이렉트한다 (성공 핸들러 직후 직접 URL 진입 방어).
 *
 * <p>SecurityConfig 가 본 필터를 web SecurityFilterChain 의 SecurityContextHolderFilter 직후에
 * 배치한다. Spring Boot 의 자동 Filter 등록은 SecurityConfig 의 FilterRegistrationBean
 * (enabled=false) 으로 차단한다.
 */
public class SessionHouseholdContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        boolean contextSet = false;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AccountPrincipal principal) {
            if (principal.getActiveHouseholdId() != null) {
                HouseholdContext.set(principal.getActiveHouseholdId());
                contextSet = true;
            } else if (needsOnboarding(request)) {
                response.sendRedirect(request.getContextPath() + "/web/onboarding");
                return;
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (contextSet) {
                HouseholdContext.clear();
            }
        }
    }

    /** 가구 없는 유저가 온보딩 외 /web 화면에 접근하려는 경우만 가드 대상. */
    private boolean needsOnboarding(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/web/") && !path.startsWith("/web/onboarding");
    }
}
