package com.kyuhyeong.account.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 사용자 (docs/account.md §6.1).
 *
 * <p>한 사용자가 여러 가구에 속할 수 있다 ({@link HouseholdMember} 다대다 중간 테이블).
 * MVP 는 사용자=가구 1:1 이지만 v1.5 가구 초대로 확장 시 자연 확장.
 *
 * <p>인증은 카카오 OAuth2 단독 ({@code provider} / {@code providerUserId} 로 식별). 카카오는
 * 닉네임 scope 만 사용하므로 {@code email} / {@code passwordHash} 는 nullable (레거시 시드 보존용).
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class User {

    /** 현재 유일한 인증 제공자. */
    public static final String PROVIDER_KAKAO = "KAKAO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_user_id", length = 64)
    private String providerUserId;

    @Column(name = "email", unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "fcm_token", length = 255)
    private String fcmToken;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /** 카카오 신규 가입 — 닉네임만 받는다 (이메일/비번 없음). 가구는 온보딩에서 별도 결정. */
    public static User createKakao(String providerUserId, String nickname) {
        return User.builder()
                .provider(PROVIDER_KAKAO)
                .providerUserId(providerUserId)
                .name(nickname)
                .build();
    }

    /** 기존(시드) 유저를 카카오 계정에 연결 — 로컬 개발에서 시드 데이터를 이어받기 위함. */
    public void linkKakao(String providerUserId) {
        this.provider = PROVIDER_KAKAO;
        this.providerUserId = providerUserId;
    }

    public void touchLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    /** 비밀번호 변경 — 인코딩된(BCrypt) 해시를 전달받는다. 인코딩 책임은 호출 측(서비스). */
    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }
}
