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
 * Web Push 구독 — 브라우저 Push API 가 발급한 endpoint + 암호화 키 (V8).
 *
 * <p><b>비격리 엔티티</b> ({@code @Filter} 미적용) — 푸시 구독은 가구가 아니라
 * 사람+브라우저(기기) 단위 자산이라 {@code User} 와 같은 전역 식별군에 속한다.
 * 조회는 코드에서 항상 user_id 가드 ({@code findAllByUserId} 등) — 임의로
 * 가구 필터 대상으로 바꾸지 말 것 (가구 멤버 전체 발송은 멤버 user_id 목록으로 조회).
 *
 * <p>한 사용자가 폰+PC 를 쓰면 브라우저당 1행. 발송 시 푸시 서비스가 404/410 을
 * 반환하면 만료 구독이므로 발송 코드가 행을 삭제한다.
 */
@Entity
@Table(
        name = "push_subscriptions",
        indexes = @Index(name = "idx_push_user", columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 푸시 서비스(FCM/APNs/Mozilla)가 발급한 구독 URL — 브라우저당 유일. */
    @Column(name = "endpoint", nullable = false, length = 512, unique = true)
    private String endpoint;

    /** 클라이언트 ECDH 공개키 (base64url) — 페이로드 암호화용. */
    @Column(name = "p256dh", nullable = false, length = 255)
    private String p256dh;

    /** 인증 시크릿 (base64url) — 페이로드 암호화용. */
    @Column(name = "auth", nullable = false, length = 255)
    private String auth;

    /** 구독 당시 User-Agent (어느 기기인지 식별용 참고 정보). */
    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 같은 브라우저(endpoint)가 다른 사용자로 재구독하거나 키가 회전된 경우 재바인딩.
     * 브라우저가 구독을 갱신하면 endpoint 는 같아도 키 쌍이 바뀔 수 있다.
     */
    public void rebind(User user, String p256dh, String auth, String userAgent) {
        this.user = user;
        this.p256dh = p256dh;
        this.auth = auth;
        this.userAgent = userAgent;
    }
}
