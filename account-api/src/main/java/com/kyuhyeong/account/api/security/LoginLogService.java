package com.kyuhyeong.account.api.security;

import com.kyuhyeong.account.core.entity.LoginLog;
import com.kyuhyeong.account.core.repository.LoginLogRepository;
import com.kyuhyeong.account.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 성공 이력 적재 — {@link OnboardingAwareSuccessHandler} 가 호출.
 *
 * <p>기록 실패가 로그인을 막으면 안 되므로 호출부에서 try-catch (본 서비스는 던진다).
 */
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private static final int MAX_USER_AGENT_LENGTH = 255;

    private final UserRepository userRepository;
    private final LoginLogRepository loginLogRepository;

    /** 로그인 성공 1건 기록. ip/userAgent 는 추출 실패 시 null 허용. */
    @Transactional
    public void record(Long userId, String ip, String userAgent) {
        loginLogRepository.save(LoginLog.builder()
                .user(userRepository.getReferenceById(userId))
                .ip(ip)
                .userAgent(truncate(userAgent))
                .build());
    }

    /**
     * 클라이언트 IP 추출 — X-Forwarded-For 첫 값 우선, 없으면 remoteAddr.
     *
     * <p>{@code forward-headers-strategy: framework}(ForwardedHeaderFilter) 는 URL 재구성만
     * 담당하고 {@code getRemoteAddr()} 를 바꾸지 않으므로, 운영(nginx 뒤)에선 remoteAddr 가
     * 프록시(127.0.0.1)로 나온다 — XFF 헤더를 직접 읽어야 실 클라이언트 IP.
     */
    public static String resolveClientIp(String xForwardedFor, String remoteAddr) {
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return remoteAddr;
    }

    private static String truncate(String userAgent) {
        if (userAgent == null || userAgent.length() <= MAX_USER_AGENT_LENGTH) {
            return userAgent;
        }
        return userAgent.substring(0, MAX_USER_AGENT_LENGTH);
    }
}
