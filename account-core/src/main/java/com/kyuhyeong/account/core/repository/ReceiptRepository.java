package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

/** 영수증 Repository (가구 격리 대상). */
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    /**
     * {@code start} 이후 생성된 영수증 수 — 영수증 AI 분석 횟수(구독 한도 메터링)와 동일.
     *
     * <p>{@code Receipt} 는 {@code householdFilter} 격리 대상이므로 {@code @Transactional} +
     * {@code HouseholdContext} 활성 상태에서 호출하면 자동으로 현재 가구 한정 카운트.
     */
    long countByCreatedAtGreaterThanEqual(LocalDateTime start);
}
