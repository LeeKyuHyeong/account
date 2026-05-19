// account-ai 모듈 — 멀티 모듈 편입판
//
// 결정 사항 반영:
//   - Java 21 (가상 스레드 활용) — 루트에서 toolchain 상속
//   - Spring Boot 3.3+ — 루트 BOM 상속
//   - Anthropic SDK 대신 RestClient 직접 호출 (프로토타입, 검증 후 SDK 마이그레이션 검토)
//
// 의존성 정책 (§10.5):
//   - account-core 에 의존하지 않음 (인터페이스 MerchantHistoryProvider 만 공유)
//   - 단방향 결합 — account-api 가 본 모듈을 의존

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // ─── Spring Boot ───
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // ─── 테스트 ───
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.wiremock:wiremock-standalone:3.10.0")
}

// library jar — bootRun/bootJar 비활성. 진입점은 account-api 에 있음.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}
tasks.named<Jar>("jar") {
    enabled = true
}
