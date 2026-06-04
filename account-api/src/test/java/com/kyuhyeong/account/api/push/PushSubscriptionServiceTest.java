package com.kyuhyeong.account.api.push;

import com.kyuhyeong.account.core.entity.PushSubscription;
import com.kyuhyeong.account.core.entity.User;
import com.kyuhyeong.account.core.repository.PushSubscriptionRepository;
import com.kyuhyeong.account.core.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PushSubscriptionService} 단위 테스트 — 구독 upsert / 본인 가드 해제.
 */
@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTest {

    @Mock
    PushSubscriptionRepository subscriptionRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    PushSubscriptionService service;

    @Test
    @DisplayName("새 endpoint 구독 — insert")
    void subscribesNewEndpoint() {
        User user = mock(User.class);
        when(userRepository.getReferenceById(1L)).thenReturn(user);
        when(subscriptionRepository.findByEndpoint("https://fcm/ep1")).thenReturn(Optional.empty());

        service.subscribe(1L, "https://fcm/ep1", "p256dh-key", "auth-secret", "Mozilla/5.0");

        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getEndpoint()).isEqualTo("https://fcm/ep1");
        assertThat(captor.getValue().getP256dh()).isEqualTo("p256dh-key");
        assertThat(captor.getValue().getAuth()).isEqualTo("auth-secret");
    }

    @Test
    @DisplayName("기존 endpoint 재구독 — insert 대신 재바인딩 (키 회전/계정 전환)")
    void rebindsExistingEndpoint() {
        User newUser = mock(User.class);
        when(userRepository.getReferenceById(2L)).thenReturn(newUser);
        PushSubscription existing = PushSubscription.builder()
                .user(mock(User.class))
                .endpoint("https://fcm/ep1")
                .p256dh("old-key")
                .auth("old-auth")
                .build();
        when(subscriptionRepository.findByEndpoint("https://fcm/ep1")).thenReturn(Optional.of(existing));

        service.subscribe(2L, "https://fcm/ep1", "new-key", "new-auth", null);

        verify(subscriptionRepository, never()).save(any());
        assertThat(existing.getUser()).isSameAs(newUser);
        assertThat(existing.getP256dh()).isEqualTo("new-key");
        assertThat(existing.getAuth()).isEqualTo("new-auth");
    }

    @Test
    @DisplayName("구독 해제 — 본인 user_id 가드가 걸린 delete 호출")
    void unsubscribesOwnOnly() {
        service.unsubscribe(1L, "https://fcm/ep1");

        verify(subscriptionRepository).deleteByUserIdAndEndpoint(1L, "https://fcm/ep1");
    }
}
