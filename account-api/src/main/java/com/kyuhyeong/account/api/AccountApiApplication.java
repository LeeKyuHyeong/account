package com.kyuhyeong.account.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * account-api 진입점.
 *
 * <p>Task 1: stub — 멀티 모듈 빌드 검증용. account-ai 의 컨트롤러를 스캔하므로
 * com.kyuhyeong.account 패키지 전체를 포함한다. account-core 의 Entity/Repository
 * 스캔은 Task 3 (JPA Entity) 에서 활성화한다.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.kyuhyeong.account")
public class AccountApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountApiApplication.class, args);
    }
}
