// account-batch 모듈
//
// 책임 (Week 4+ 구체화):
//   - 월말 카테고리 집계 (monthly_summaries 사전 계산)
//   - 영수증 이미지 단계적 압축/삭제
//   - FCM 알림 발송
//
// 본 Week 1에서는 빈 모듈로만 셋업. Spring Batch 의존성도 추후 추가.

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":account-core"))

    implementation("org.springframework.boot:spring-boot-starter")
    // 추후: implementation("org.springframework.boot:spring-boot-starter-batch")
}

// 별도 부팅 entrypoint 아직 없음. library jar로 취급.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}
tasks.named<Jar>("jar") {
    enabled = true
}
