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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 가구 초대코드.
 *
 * <p>OWNER 가 가구 설정에서 발급하고, 신규 멤버가 온보딩의 "초대코드 입력하기" 에서 사용한다.
 *
 * <p><b>비격리 엔티티</b> — 코드 사용 시점에는 가입자가 아직 어느 가구에도 속하지 않아
 * {@code HouseholdContext} 가 없다. 따라서 {@code @Filter} 를 붙이지 않고
 * (User / Household / HouseholdMember 와 동일 비격리군) 코드 값으로 직접 조회·가드한다.
 */
@Entity
@Table(
        name = "invite_codes",
        uniqueConstraints = @UniqueConstraint(name = "uk_invite_codes_code", columnNames = "code"),
        indexes = @Index(name = "idx_invite_household", columnList = "household_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class InviteCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 16)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 초대코드 발급. code 는 호출 측(서비스)에서 충돌 없는 값을 생성해 전달. expiresAt=null 이면 무기한. */
    public static InviteCode issue(Household household, User createdBy, String code, LocalDateTime expiresAt) {
        return InviteCode.builder()
                .household(household)
                .createdBy(createdBy)
                .code(code)
                .expiresAt(expiresAt)
                .revoked(false)
                .usedCount(0)
                .build();
    }

    public void revoke() {
        this.revoked = true;
    }

    public void markUsed() {
        this.usedCount++;
    }

    /** 사용 가능 여부 — 미취소 + 미만료. (다회 사용 허용 — used_count 는 기록용.) */
    public boolean isValid(LocalDateTime now) {
        return !revoked && (expiresAt == null || now.isBefore(expiresAt));
    }
}
