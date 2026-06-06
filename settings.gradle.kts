// Java 21 toolchain 자동 다운로드 (개발자가 JDK 21 미설치여도 빌드 가능)
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "account"

include("account-core", "account-api", "account-ai", "account-batch")
