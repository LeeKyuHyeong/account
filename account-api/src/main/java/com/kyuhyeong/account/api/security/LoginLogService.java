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

    private static String truncate(String userAgent) {
        if (userAgent == null || userAgent.length() <= MAX_USER_AGENT_LENGTH) {
            return userAgent;
        }
        return userAgent.substring(0, MAX_USER_AGENT_LENGTH);
    }
}
