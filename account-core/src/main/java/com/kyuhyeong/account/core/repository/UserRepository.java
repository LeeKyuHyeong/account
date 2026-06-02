package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 사용자 Repository.
 *
 * <p>가구 격리 대상 외 (사용자 자체는 글로벌 식별 단위).
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** 로그인 시 이메일로 사용자 조회 (Task 5). 레거시 — 카카오 전환 후엔 로컬 시드 링크 lookup 에만 사용. */
    Optional<User> findByEmail(String email);

    /** 카카오 OAuth2 로그인 시 (provider, providerUserId) 로 사용자 조회. */
    Optional<User> findByProviderAndProviderUserId(String provider, String providerUserId);
}
