package com.kyuhyeong.account.api.push;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyuhyeong.account.core.entity.PushSubscription;
import com.kyuhyeong.account.core.repository.HouseholdMemberRepository;
import com.kyuhyeong.account.core.repository.PushSubscriptionRepository;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Security;
import java.util.List;
import java.util.Map;

/**
 * Web Push 발송 (푸시 알림 0단계).
 *
 * <p>VAPID 키 미설정이면 비활성 ({@link #isEnabled()} false — 화면은 안내만 표시,
 * 발송 호출은 no-op). 발송 시 푸시 서비스가 404/410(만료 구독)을 반환하면 해당
 * 구독 행을 삭제해 자연 정리한다. 발송 실패는 업무 흐름을 막지 않는다 (warn 로그만).
 *
 * <p>페이로드는 {@code {title, body, url}} JSON — {@code static/sw.js} 가 파싱해
 * 알림 표시 + 클릭 시 url 오픈.
 */
@Service
public class PushSendService {

    private static final Logger log = LoggerFactory.getLogger(PushSendService.class);

    private final PushSubscriptionRepository subscriptionRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final ObjectMapper objectMapper;

    /** VAPID 키 미설정이면 null — 푸시 전체 비활성. */
    private final PushService pushService;

    public PushSendService(
            PushSubscriptionRepository subscriptionRepository,
            HouseholdMemberRepository householdMemberRepository,
            ObjectMapper objectMapper,
            @Value("${account.push.vapid.public-key:}") String publicKey,
            @Value("${account.push.vapid.private-key:}") String privateKey,
            @Value("${account.push.vapid.subject:mailto:admin@example.com}") String subject) {
        this.subscriptionRepository = subscriptionRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.objectMapper = objectMapper;
        this.pushService = buildPushService(publicKey, privateKey, subject);
    }

    private static PushService buildPushService(String publicKey, String privateKey, String subject) {
        if (publicKey == null || publicKey.isBlank() || privateKey == null || privateKey.isBlank()) {
            log.info("Web Push disabled — VAPID keys not configured (ACCOUNT_PUSH_VAPID_*)");
            return null;
        }
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            return new PushService(publicKey, privateKey, subject);
        } catch (Exception e) {
            // 키 형식 오류 — 기동은 막지 않고 푸시만 비활성 (알림은 부가 기능)
            log.error("Web Push disabled — invalid VAPID keys", e);
            return null;
        }
    }

    public boolean isEnabled() {
        return pushService != null;
    }

    /**
     * 한 사용자의 모든 기기(구독)로 발송. 만료 구독은 삭제.
     *
     * @return 발송 성공 수 (비활성/구독 없음이면 0)
     */
    @Transactional
    public int sendToUser(Long userId, String title, String body, String url) {
        if (!isEnabled()) {
            return 0;
        }
        List<PushSubscription> subscriptions = subscriptionRepository.findAllByUserId(userId);
        String payload = toPayload(title, body, url);
        int sent = 0;
        for (PushSubscription sub : subscriptions) {
            if (sendOne(sub, payload)) {
                sent++;
            }
        }
        return sent;
    }

    /**
     * 가구 멤버 전원(행위자 제외)의 모든 기기로 발송 — "배우자 알림"의 본체.
     *
     * <p>{@code PushSubscription} 은 비격리 엔티티지만, 수신자 목록을 호출자가 신뢰하는
     * householdId 의 멤버십({@code HouseholdMember})으로 한정하므로 가구 경계가 지켜진다.
     *
     * @param exceptUserId 제외할 행위자 — {@code null} 이면 전원 발송 (다이제스트 등)
     * @return 발송 성공 수 (비활성/수신자 없음이면 0)
     */
    @Transactional
    public int sendToHouseholdExcept(Long householdId, Long exceptUserId,
                                     String title, String body, String url) {
        if (!isEnabled()) {
            return 0;
        }
        List<Long> recipientIds = householdMemberRepository.findByHouseholdId(householdId).stream()
                .map(m -> m.getUser().getId())
                .filter(id -> !id.equals(exceptUserId))
                .toList();
        if (recipientIds.isEmpty()) {
            return 0;
        }
        String payload = toPayload(title, body, url);
        int sent = 0;
        for (PushSubscription sub : subscriptionRepository.findAllByUserIdIn(recipientIds)) {
            if (sendOne(sub, payload)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean sendOne(PushSubscription sub, String payload) {
        try {
            Notification notification =
                    new Notification(sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payload);
            int status = pushService.send(notification).getStatusLine().getStatusCode();
            if (status == 404 || status == 410) {
                // 만료/해지된 구독 — 푸시 서비스가 영구 거부하므로 행 삭제
                log.info("Push subscription expired (HTTP {}) — removing id={}", status, sub.getId());
                subscriptionRepository.delete(sub);
                return false;
            }
            if (status >= 400) {
                log.warn("Push send failed: HTTP {} (subscriptionId={})", status, sub.getId());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Push send failed (subscriptionId={})", sub.getId(), e);
            return false;
        }
    }

    private String toPayload(String title, String body, String url) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "title", title,
                    "body", body,
                    "url", url == null ? "/web/home" : url));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize push payload", e);
        }
    }
}
