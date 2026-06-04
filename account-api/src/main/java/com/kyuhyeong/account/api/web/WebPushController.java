package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.api.push.PushDigestService;
import com.kyuhyeong.account.api.push.PushSendService;
import com.kyuhyeong.account.api.push.PushSubscriptionService;
import com.kyuhyeong.account.api.security.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/**
 * 알림 설정 화면 + Web Push 구독 등록/해제/테스트 (푸시 알림 0단계).
 *
 * <p>구독 등록/해제는 브라우저 Push API 의 구독 객체(JSON)를 그대로 받아야 해서
 * 폼 컨벤션(record DTO + @ModelAttribute) 대신 예외적으로 {@code @RequestBody} JSON 을
 * 쓴다 — fetch 호출 시 CSRF 토큰은 {@code X-CSRF-TOKEN} 헤더로 동봉 (템플릿 meta 태그).
 */
@Controller
@RequestMapping("/web/push")
@RequiredArgsConstructor
public class WebPushController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PushSubscriptionService subscriptionService;
    private final PushSendService sendService;
    private final PushDigestService digestService;

    @Value("${account.push.vapid.public-key:}")
    private String vapidPublicKey;

    /** 알림 설정 화면 — 구독 여부는 클라이언트(JS)가 브라우저 상태로 판정. */
    @GetMapping
    public String settings(Model model) {
        model.addAttribute("pushEnabled", sendService.isEnabled());
        model.addAttribute("vapidPublicKey", vapidPublicKey);
        return "push/settings";
    }

    /** 브라우저 PushSubscription.toJSON() 그대로 — {endpoint, keys: {p256dh, auth}}. */
    record SubscribeRequest(@NotBlank String endpoint, @Valid Keys keys) {
        record Keys(@NotBlank String p256dh, @NotBlank String auth) {}
    }

    @PostMapping("/subscribe")
    @ResponseBody
    public Map<String, Object> subscribe(@RequestBody @Valid SubscribeRequest request,
                                         @RequestHeader(value = "User-Agent", required = false) String userAgent,
                                         @AuthenticationPrincipal AccountPrincipal user) {
        subscriptionService.subscribe(user.getUserId(), request.endpoint(),
                request.keys().p256dh(), request.keys().auth(),
                userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 255)));
        return Map.of("ok", true);
    }

    record UnsubscribeRequest(@NotBlank String endpoint) {}

    @PostMapping("/unsubscribe")
    @ResponseBody
    public Map<String, Object> unsubscribe(@RequestBody @Valid UnsubscribeRequest request,
                                           @AuthenticationPrincipal AccountPrincipal user) {
        subscriptionService.unsubscribe(user.getUserId(), request.endpoint());
        return Map.of("ok", true);
    }

    /** 본인 전 기기로 테스트 발송 — 구독→수신 경로 검증용. */
    @PostMapping("/test")
    @ResponseBody
    public Map<String, Object> sendTest(@AuthenticationPrincipal AccountPrincipal user) {
        int sent = sendService.sendToUser(user.getUserId(),
                "가계부 알림 테스트", "이 알림이 보이면 푸시 설정 완료!", "/web/home");
        return Map.of("ok", true, "sent", sent);
    }

    /**
     * 현재 가구의 일일 다이제스트 즉시 발송 — 21시 스케줄을 기다리지 않는 수동 검증 경로
     * ({@code /web/recurring/run-now} 와 같은 성격). 보낼 내용 없으면 발송 자체가 없다.
     */
    @PostMapping("/digest-now")
    @ResponseBody
    public Map<String, Object> sendDigestNow() {
        digestService.sendDailyDigestForCurrentHousehold(LocalDate.now(KST));
        return Map.of("ok", true);
    }
}
