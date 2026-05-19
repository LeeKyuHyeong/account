package com.kyuhyeong.account.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyuhyeong.account.api.auth.AuthDtos;
import com.kyuhyeong.account.api.controller.CategoryController.CategoryDto;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 본 프로젝트 가장 중요한 검증 (docs/account.md §8.2 Task 4 — Task 5 에서 JWT 로 갱신).
 *
 * <p>가구#1 owner / 가구#2 owner 가 각자 로그인 후 발급받은 access 토큰으로
 * {@code GET /api/categories} 를 호출했을 때 자기 가구 카테고리만 노출되는지 검증한다.
 * V2 시드의 의도적 비대칭 (22 vs 5) + ID 교집합 0 으로 격리 누수를 잡는다.
 *
 * <p>토큰 없이 호출하면 SecurityConfig 가 401 — fail-safe sentinel 까지 가지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
@Disabled(
        "Docker Desktop on Windows 와 Testcontainers 의 알려진 비호환 — Desktop 의 CLI 프록시가 "
                + "named-pipe 응답을 가로채 docker-java 가 BadRequest 로 인식한다. Linux CI 또는 "
                + "Docker Desktop TCP 노출 활성화 환경에서 본 어노테이션 제거하면 자동 실행."
)
class HouseholdIsolationIntegrationTest {

    @Container
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.4");

    @DynamicPropertySource
    static void config(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mariadb::getJdbcUrl);
        r.add("spring.datasource.username", mariadb::getUsername);
        r.add("spring.datasource.password", mariadb::getPassword);
        // 테스트 전용 base64-encoded 32 바이트 (실제로는 0..31 의 byte 0~31).
        r.add("account.jwt.secret", () -> "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void categoriesAreIsolatedByHousehold() throws Exception {
        String tokenH1 = loginAccessToken("owner1@example.com");
        String tokenH2 = loginAccessToken("owner2@example.com");

        List<CategoryDto> h1 = fetchCategories(tokenH1);
        List<CategoryDto> h2 = fetchCategories(tokenH2);

        assertThat(h1).as("우리집 카테고리는 V2 시드대로 22개").hasSize(22);
        assertThat(h2).as("테스트가구 카테고리는 V2 시드대로 5개").hasSize(5);

        Set<Long> h1Ids = h1.stream().map(CategoryDto::id).collect(Collectors.toSet());
        Set<Long> h2Ids = h2.stream().map(CategoryDto::id).collect(Collectors.toSet());
        assertThat(h1Ids).as("가구 간 ID 교집합 = 0 (격리 누수 없음)")
                .doesNotContainAnyElementsOf(h2Ids);
    }

    @Test
    void categoriesEndpointRejectsAnonymous() throws Exception {
        mvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized());
    }

    private String loginAccessToken(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"dev1234!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readValue(body, AuthDtos.LoginResponse.class).accessToken();
    }

    private List<CategoryDto> fetchCategories(String accessToken) throws Exception {
        String body = mvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readValue(body, new TypeReference<List<CategoryDto>>() {
        });
    }
}
