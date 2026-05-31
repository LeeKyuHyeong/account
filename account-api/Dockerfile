# account-api 운영 이미지 (multi-stage build).
#
# 단계:
#   1. build — JDK 21 alpine 에서 ./gradlew :account-api:bootJar 실행
#   2. runtime — JRE 21 alpine 에서 실행 (이미지 크기 ↓, attack surface ↓)
#
# 컨텍스트: 루트(D:/account-app) 에서 빌드 한다.
#   docker build -f account-api/Dockerfile -t account-api:latest .
#
# 가상 스레드 (Loom) 는 application.yml 의 spring.threads.virtual.enabled: true 로
# 자동 활성화 — Dockerfile 에서 별도 JVM 플래그 불필요.
#
# 시크릿 / DB 접속 정보는 모두 런타임 env 로 주입 (이미지에 굽지 않는다).

# ─── build stage ───
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Gradle 캐시 효율을 위해 wrapper / build script 부터 복사 → dependency resolve
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY account-core/build.gradle.kts ./account-core/
COPY account-ai/build.gradle.kts ./account-ai/
COPY account-api/build.gradle.kts ./account-api/
COPY account-batch/build.gradle.kts ./account-batch/

# 의존성 사전 해결 (소스 변경에도 캐시 hit).
# alpine 에는 bash 가 없어 sh 로 wrapper 실행.
RUN chmod +x gradlew && \
    sh ./gradlew --no-daemon :account-api:dependencies > /dev/null 2>&1 || true

# 소스 복사 후 bootJar 빌드.
# account-batch 는 현재 src 없는 빈 모듈(build.gradle.kts 만) — 첫 잡 추가 시 COPY 라인 복원.
COPY account-core/src ./account-core/src
COPY account-ai/src ./account-ai/src
COPY account-api/src ./account-api/src

RUN sh ./gradlew --no-daemon :account-api:bootJar -x test

# ─── runtime stage ───
FROM eclipse-temurin:21-jre-alpine

# tini — PID 1 + signal handling. SIGTERM 으로 graceful shutdown 보장.
RUN apk add --no-cache tini

WORKDIR /app
COPY --from=build /workspace/account-api/build/libs/*.jar /app/app.jar

# 영수증 저장 디렉토리 — docker-compose 에서 호스트 볼륨 마운트.
RUN mkdir -p /mnt/data/receipts
VOLUME ["/mnt/data/receipts"]

EXPOSE 8080

ENTRYPOINT ["/sbin/tini", "--", "java", "-jar", "/app/app.jar"]
