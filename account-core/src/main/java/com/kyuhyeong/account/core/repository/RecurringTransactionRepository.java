package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.RecurringTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * 반복 거래 룰 Repository (가구 격리 대상).
 *
 * <p>가구 격리는 Hibernate {@code householdFilter} 가 SQL 레벨에서 자동 적용.
 */
public interface RecurringTransactionRepository
        extends JpaRepository<RecurringTransaction, Long>, JpaSpecificationExecutor<RecurringTransaction> {

    /** 활성 룰만 — 스케줄러가 발화 후보를 찾을 때. */
    List<RecurringTransaction> findAllByActiveTrue();

    /** 특정 카테고리를 참조하는 룰 수 — 카테고리 삭제 안전 가드용 (활성/비활성 모두 카운트). */
    long countByCategoryId(Long categoryId);
}
