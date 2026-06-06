package com.kyuhyeong.account.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 카카오 로그인 성공 이력 (V9).
 *
 * <p><b>비격리 엔티티</b> ({@code @Filter} 미적용) — 사람 단위 기록이라 가구 컨텍스트와
 * 무관하며 {@code User} 와 같은 전역 식별군. 조회는 앱 관리자 화면(/web/sysadmin,
 * ROLE_SYSADMIN) 전용 — 일반 화면에 노출하지 말 것.
 *
 * <p>기록 시점은 {@code OnboardingAwareSuccessHandler} (로그인 성공 직후). 실패 로그인은
 * principal 이 없어 비범위.
 */
@Entity
@Table(
        name = "login_logs",
        indexes = @Index(name = "idx_loginlog_user", columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class LoginLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 클라이언트 IP (IPv6 최대 45자). 운영은 X-Forwarded-For 첫 값 — 추출 실패 시 null. */
    @Column(name = "ip", length = 45)
    private String ip;

    /** 로그인 당시 User-Agent (어느 기기인지 식별용 참고 정보). */
    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
