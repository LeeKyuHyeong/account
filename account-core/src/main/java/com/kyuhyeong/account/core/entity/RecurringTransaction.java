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
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 반복 거래 룰 — 매월 정해진 일자에 거래를 자동 생성한다 (월세/통신/구독 등).
 *
 * <p>{@code RecurringTransactionScheduler} 가 매일 새벽 KST 5시에 due 룰을 발화. 멱등은
 * {@link #lastRunYearMonth} (YYYY-MM) 비교로 보장 — 같은 달에 2번 발화하지 않는다.
 *
 * <p>가구 격리 대상.
 */
@Entity
@Table(
        name = "recurring_transactions",
        indexes = @Index(name = "idx_recurring_hid_active", columnList = "household_id, active")
)
@Filter(name = "householdFilter", condition = "household_id = :currentHouseholdId")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RecurringTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "merchant", length = 255)
    private String merchant;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "memo", length = 500)
    private String memo;

    /** 매월 발화 일자 (1~31). 31 인데 해당 월에 31일 없으면 서비스가 말일로 클램프. */
    @Column(name = "day_of_month", nullable = false)
    private int dayOfMonth;

    @Column(name = "active", nullable = false)
    private boolean active;

    /** 마지막 발화한 YYYY-MM. NULL = 한 번도 발화 안 함. 같은 달 재발화 방지용. */
    @Column(name = "last_run_year_month", length = 7)
    private String lastRunYearMonth;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 룰 전체 편집 — 카테고리 / 금액 / 상점 / 결제수단 / 메모 / 일자 / 활성. */
    public void edit(Category category, BigDecimal amount, String merchant, String paymentMethod,
                     String memo, int dayOfMonth, boolean active) {
        this.category = category;
        this.amount = amount;
        this.merchant = merchant;
        this.paymentMethod = paymentMethod;
        this.memo = memo;
        this.dayOfMonth = dayOfMonth;
        this.active = active;
    }

    /** 발화 성공 표시 — 같은 달 재발화 방지. */
    public void markRun(String yearMonth) {
        this.lastRunYearMonth = yearMonth;
    }
}
