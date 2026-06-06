package com.kyuhyeong.account.api.config;

import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 웹서버 (Tomcat) 일반 설정 — 보안 외 영역.
 */
@Configuration
public class WebConfig {

    /**
     * {@code .webmanifest} 를 표준 MIME {@code application/manifest+json} 으로 서빙.
     * Tomcat 기본 매핑에 없어 {@code application/octet-stream} 으로 내려가던 것을 보정한다.
     */
    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> manifestMimeMapping() {
        return factory -> {
            MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);
            mappings.add("webmanifest", "application/manifest+json");
            factory.setMimeMappings(mappings);
        };
    }
}
