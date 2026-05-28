package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 거래 Repository (가구 격리 대상).
 *
 * <p>{@link JpaSpecificationExecutor} 를 함께 상속해 동적 필터 (날짜 범위 / 카테고리 /
 * 상태 / soft-delete) 를 Specification 으로 조립한다. 가구 격리는 Hibernate
 * {@code householdFilter} 가 SQL 레벨에서 자동 적용.
 */
public interface TransactionRepository
        extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    /**
     * 특정 카테고리를 참조하는 거래 수 — 카테고리 삭제 안전 가드용. soft-delete 된 거래도 포함
     * (DB FK 가 RESTRICT 라 soft-delete 여부와 무관하게 어차피 막힘 → 사용자에게 동일 결과 보고).
     * 가구 격리는 Hibernate {@code householdFilter} 가 자동 적용.
     */
    long countByCategoryId(Long categoryId);
}
