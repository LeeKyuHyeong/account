package com.kyuhyeong.account.api.security;

import com.kyuhyeong.account.core.entity.LoginLog;
import com.kyuhyeong.account.core.entity.User;
import com.kyuhyeong.account.core.repository.LoginLogRepository;
import com.kyuhyeong.account.core.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LoginLogService} 단위 테스트 — 로그인 성공 기록.
 *
 * <p>클라이언트 IP 추출은 {@link OnboardingAwareSuccessHandlerTest} 가 ForwardedHeaderFilter 를 거쳐 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class LoginLogServiceTest {

    @Mock UserRepository userRepository;
    @Mock LoginLogRepository loginLogRepository;

    @InjectMocks LoginLogService service;

    @Test
    @DisplayName("record — user reference + ip/userAgent 로 LoginLog 저장")
    void recordSavesLog() {
        User user = User.builder().id(7L).build();
        when(userRepository.getReferenceById(7L)).thenReturn(user);

        service.record(7L, "1.2.3.4", "Mozilla/5.0");

        ArgumentCaptor<LoginLog> captor = ArgumentCaptor.forClass(LoginLog.class);
        verify(loginLogRepository).save(captor.capture());
        LoginLog saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getIp()).isEqualTo("1.2.3.4");
        assertThat(saved.getUserAgent()).isEqualTo("Mozilla/5.0");
    }

    @Test
    @DisplayName("record — userAgent 가 255자 초과면 잘라서 저장 (DB 컬럼 한도)")
    void recordTruncatesLongUserAgent() {
        when(userRepository.getReferenceById(7L)).thenReturn(User.builder().id(7L).build());
        String longUa = "U".repeat(300);

        service.record(7L, "1.2.3.4", longUa);

        ArgumentCaptor<LoginLog> captor = ArgumentCaptor.forClass(LoginLog.class);
        verify(loginLogRepository).save(captor.capture());
        assertThat(captor.getValue().getUserAgent()).hasSize(255);
    }

    @Test
    @DisplayName("record — ip/userAgent null 허용 (추출 실패해도 기록은 남긴다)")
    void recordAllowsNullIpAndUserAgent() {
        when(userRepository.getReferenceById(7L)).thenReturn(User.builder().id(7L).build());

        service.record(7L, null, null);

        ArgumentCaptor<LoginLog> captor = ArgumentCaptor.forClass(LoginLog.class);
        verify(loginLogRepository).save(captor.capture());
        assertThat(captor.getValue().getIp()).isNull();
        assertThat(captor.getValue().getUserAgent()).isNull();
    }
}
