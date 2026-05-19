package com.kyuhyeong.account.api.tools;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt 해시 발급용 유틸 테스트. 새 시드 비밀번호 추가 / 변경 시:
 *
 * <pre>
 *   ./gradlew :account-api:test --tests "BcryptHashToolTest" -i
 * </pre>
 *
 * 로 실행하고 콘솔에 찍힌 해시를 V_n__seed.sql 등에 붙여 넣는다.
 *
 * <p>본 클래스는 운영 코드 아님 — Spring Context 도 띄우지 않는다.
 */
class BcryptHashToolTest {

    @Test
    void printDevPasswordHash() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        String plain = "dev1234!";
        String hash = encoder.encode(plain);
        System.out.println("===BCRYPT_HASH_BEGIN===");
        System.out.println("plain: " + plain);
        System.out.println("hash : " + hash);
        System.out.println("===BCRYPT_HASH_END===");
    }
}
