package com.kyuhyeong.account.api.config;

import com.kyuhyeong.account.api.security.KakaoOAuth2UserService;
import com.kyuhyeong.account.api.security.OnboardingAwareSuccessHandler;
import com.kyuhyeong.account.api.security.SessionHouseholdContextFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.IOException;

/**
 * Spring Security 설정 — 세션 (SSR) 단일 체인, 카카오 OAuth2 단독 인증.
 *
 * <p>oauth2Login + 세션 + CSRF (기본 활성). 로그인 성공 시 {@link OnboardingAwareSuccessHandler}
 * 가 가구 유무에 따라 /web/home 또는 /web/onboarding 으로 보낸다. {@link SessionHouseholdContextFilter}
 * 가 세션 principal 의 활성 가구 ID 로 {@code HouseholdContext} 를 채운다.
 *
 * <p>{@link SessionHouseholdContextFilter} 는 @Component 가 아니라 @Bean 으로 등록하되,
 * Spring Boot 가 글로벌 {@code /*} 매핑으로 자동 등록하면 SecurityFilterChain 과 글로벌에서
 * 두 번 실행되어 컨텍스트가 오염된다. {@link #sessionFilterRegistration} 가 enabled=false 로
 * 글로벌 자동 등록을 차단해 체인 안에서만 실행되도록 격리한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final RequestMatcher PUSH_API = new AntPathRequestMatcher("/web/push/**", "POST");

    @Bean
    public SecurityFilterChain webChain(HttpSecurity http,
                                        SessionHouseholdContextFilter sessionHouseholdContextFilter,
                                        KakaoOAuth2UserService kakaoOAuth2UserService,
                                        OnboardingAwareSuccessHandler successHandler)
            throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // /sw.js — 브라우저가 Service Worker 갱신 체크를 세션과 무관하게
                        // 수행하므로 익명 허용 (로그인 리다이렉트 HTML 이 오면 SW 가 깨진다).
                        .requestMatchers("/login", "/error", "/webjars/**",
                                "/css/**", "/js/**", "/favicon.ico", "/sw.js",
                                "/manifest.webmanifest", "/icons/**",
                                "/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers("/web/admin/**").hasRole("OWNER")
                        .requestMatchers("/web/plan/**").hasRole("OWNER")
                        // 앱 관리자 — 카카오 providerUserId 화이트리스트 (SysAdminProperties → principal)
                        .requestMatchers("/web/sysadmin/**").hasRole("SYSADMIN")
                        .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo.userService(kakaoOAuth2UserService))
                        .successHandler(successHandler)
                        .failureUrl("/login?error"))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                // 푸시 설정 화면의 fetch(POST /web/push/**) 는 로그인이 끝났으면 401 을 받는다 — 화면이 "로그인 만료" 를
                // 구분해 안내할 수 있게. 세션이 끝나면 CSRF 토큰도 함께 사라져 실제로는 CsrfFilter 가 먼저 거부하므로
                // (인증 검사보다 앞) 접근 거부 핸들러에서도 같은 기준으로 401/403 을 가른다.
                // 그 밖의 요청은 기존 동작 그대로: 미인증은 /login 으로, 접근 거부는 403.
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), PUSH_API)
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"), AnyRequestMatcher.INSTANCE)
                        .defaultAccessDeniedHandlerFor(SecurityConfig::denyPushApi, PUSH_API)
                        .defaultAccessDeniedHandlerFor(new AccessDeniedHandlerImpl(), AnyRequestMatcher.INSTANCE))
                .addFilterAfter(sessionHouseholdContextFilter, SecurityContextHolderFilter.class);
        return http.build();
    }

    /** 로그인하지 않은(세션이 끝난) 요청이면 401, 로그인했는데 거부된 것이면 403. */
    private static void denyPushApi(HttpServletRequest request, HttpServletResponse response,
                                    AccessDeniedException denied) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean loggedIn = auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
        response.sendError(loggedIn ? HttpServletResponse.SC_FORBIDDEN : HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Bean
    public SessionHouseholdContextFilter sessionHouseholdContextFilter() {
        return new SessionHouseholdContextFilter();
    }

    /** SessionHouseholdContextFilter 의 글로벌 자동 등록 차단 — webChain 안에서만 실행되도록. */
    @Bean
    public FilterRegistrationBean<SessionHouseholdContextFilter> sessionFilterRegistration(
            SessionHouseholdContextFilter filter) {
        FilterRegistrationBean<SessionHouseholdContextFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }
}
