package com.kyuhyeong.account.api.push;

import com.kyuhyeong.account.core.entity.PushSubscription;
import com.kyuhyeong.account.core.entity.User;
import com.kyuhyeong.account.core.repository.PushSubscriptionRepository;
import com.kyuhyeong.account.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Web Push 구독 등록/해제 (푸시 알림 0단계).
 *
 * <p>구독은 비격리 엔티티({@code PushSubscription}) — 모든 접근은 세션 principal 의
 * userId 로 가드한다. endpoint 는 브라우저당 유일하므로, 같은 브라우저가 다른 계정으로
 * 재구독하면 기존 행을 그 계정으로 재바인딩한다 (한 기기 = 한 수신자).
 */
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private final PushSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    /** 구독 upsert — 새 endpoint 면 insert, 기존 endpoint 면 키/사용자 재바인딩. */
    @Transactional
    public void subscribe(Long userId, String endpoint, String p256dh, String auth, String userAgent) {
        User user = userRepository.getReferenceById(userId);
        subscriptionRepository.findByEndpoint(endpoint).ifPresentOrElse(
                existing -> existing.rebind(user, p256dh, auth, userAgent),
                () -> subscriptionRepository.save(PushSubscription.builder()
                        .user(user)
                        .endpoint(endpoint)
                        .p256dh(p256dh)
                        .auth(auth)
                        .userAgent(userAgent)
                        .build()));
    }

    /** 본인 구독만 해제 — 타인 endpoint 는 영향 없음 (user_id 가드). */
    @Transactional
    public void unsubscribe(Long userId, String endpoint) {
        subscriptionRepository.deleteByUserIdAndEndpoint(userId, endpoint);
    }
}
