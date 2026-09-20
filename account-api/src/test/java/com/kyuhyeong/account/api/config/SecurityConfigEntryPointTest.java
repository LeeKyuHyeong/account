package com.kyuhyeong.account.api.config;

import com.kyuhyeong.account.api.security.KakaoOAuth2UserService;
import com.kyuhyeong.account.api.security.OnboardingAwareSuccessHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인이 끝난(세션 만료) 상태의 요청에 무엇을 돌려주는가.
 *
 * 푸시 설정 화면은 fetch(POST /web/push/**) 응답을 확인하지 않았다 — 세션이 끝난 뒤 "알림 켜기" 를 누르면
 * 서버에는 구독이 저장되지 않았는데 화면은 "켜짐" 으로 표시됐다 (2026-09-19 발견).
 * 수정 전 서버 응답: 세션과 함께 CSRF 토큰도 사라지므로 CsrfFilter 가 먼저 403, 토큰이 유효한 미인증 요청은 /login 302.
 * 둘 다 "로그인 만료" 와 구분되지 않아, 푸시 API 에 한해 미인증이면 401 로 통일했다. 화면 이동은 그대로 /login.
 */
@DisplayName("미인증 요청 응답 — 화면 이동은 로그인 페이지, 푸시 API 는 401")
class SecurityConfigEntryPointTest {

    private MockMvc mockMvc;

    @Controller
    static class TestController {
        @GetMapping("/web/home")
        @ResponseBody
        String home() { return "home"; }

        @GetMapping("/web/push")
        @ResponseBody
        String pushSettingsPage() { return "settings"; }

        @PostMapping("/web/push/subscribe")
        @ResponseBody
        String subscribe() { return "{\"ok\":true}"; }

        @GetMapping("/actuator/health")
        @ResponseBody
        String health() { return "{\"status\":\"UP\"}"; }

        @GetMapping("/actuator/env")
        @ResponseBody
        String env() { return "{}"; }
    }

    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean
        KakaoOAuth2UserService kakaoOAuth2UserService() {
            return mock(KakaoOAuth2UserService.class);
        }

        @Bean
        OnboardingAwareSuccessHandler successHandler() {
            return mock(OnboardingAwareSuccessHandler.class);
        }

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("kakao")
                    .clientId("test-client")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .authorizationUri("https://example.invalid/oauth/authorize")
                    .tokenUri("https://example.invalid/oauth/token")
                    .userInfoUri("https://example.invalid/v2/user/me")
                    .userNameAttributeName("id")
                    .build());
        }

        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @BeforeEach
    void setUp() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("세션이 끝난 뒤의 푸시 API 호출은 로그인 페이지 리다이렉트가 아니라 401 을 받는다")
    void pushApi_withoutLogin_returns401() throws Exception {
        mockMvc.perform(post("/web/push/subscribe").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("세션과 함께 CSRF 토큰도 사라진 경우(토큰 불일치)에도 푸시 API 는 401 을 받는다")
    void pushApi_withStaleCsrfToken_returns401() throws Exception {
        mockMvc.perform(post("/web/push/subscribe").with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("화면 이동은 지금처럼 로그인 페이지로 보낸다 — 푸시 설정 화면(GET) 포함")
    void pageNavigation_withoutLogin_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/web/home").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/web/push").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("Accept 헤더가 없는 미인증 요청도 지금처럼 로그인 페이지로 보낸다 (푸시 API 밖은 동작 불변)")
    void otherRequests_withoutLogin_stillRedirectToLogin() throws Exception {
        mockMvc.perform(get("/web/home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("컨테이너 HEALTHCHECK 는 로그인 없이 /actuator/health 를 부를 수 있다 — 그 밖의 actuator 경로는 열리지 않는다")
    void actuatorHealth_isOpen_butNothingElseUnderActuator() throws Exception {
        // 로그인 리다이렉트(302)가 오면 wget --spider 가 /login 의 200 을 따라가 "항상 healthy" 가 된다
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("로그인한 사용자의 푸시 API 호출은 그대로 통과한다")
    void pushApi_withLogin_passes() throws Exception {
        mockMvc.perform(post("/web/push/subscribe").with(user("member")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인한 사용자라도 CSRF 토큰이 틀리면 403 이다 (401 로 바뀌지 않는다)")
    void pushApi_withLoginButInvalidCsrf_isForbidden() throws Exception {
        mockMvc.perform(post("/web/push/subscribe").with(user("member")).with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }
}
