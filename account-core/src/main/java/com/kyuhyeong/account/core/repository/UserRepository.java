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

    /** 로그인 시 이메일로 사용자 조회 (Task 5). */
    Optional<User> findByEmail(String email);
}
