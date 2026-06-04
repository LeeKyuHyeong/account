package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Web Push 구독 Repository — 비격리 (User 군).
 *
 * <p>가구 필터가 없으므로 모든 조회는 user_id 가드 메서드로만 — endpoint 단독 조회는
 * 구독 upsert(같은 브라우저 재구독) 시에만 사용하고, 삭제는 본인 것만
 * ({@code deleteByUserIdAndEndpoint}).
 */
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    List<PushSubscription> findAllByUserId(Long userId);

    /** 가구 멤버 일괄 발송용 — 호출자가 멤버십으로 검증한 user_id 목록만 넘긴다. */
    List<PushSubscription> findAllByUserIdIn(Collection<Long> userIds);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    /** 본인 구독만 해제 — 타인 endpoint 를 넘겨도 영향 없음. */
    long deleteByUserIdAndEndpoint(Long userId, String endpoint);
}
