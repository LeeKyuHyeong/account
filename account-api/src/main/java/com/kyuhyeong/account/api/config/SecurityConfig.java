package com.kyuhyeong.account.api.config;

import com.kyuhyeong.account.api.security.KakaoOAuth2UserService;
import com.kyuhyeong.account.api.security.OnboardingAwareSuccessHandler;
import com.kyuhyeong.account.api.security.SessionHouseholdContextFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

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
                                "/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers("/web/admin/**").hasRole("OWNER")
                        .requestMatchers("/web/plan/**").hasRole("OWNER")
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
                .addFilterAfter(sessionHouseholdContextFilter, SecurityContextHolderFilter.class);
        return http.build();
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
