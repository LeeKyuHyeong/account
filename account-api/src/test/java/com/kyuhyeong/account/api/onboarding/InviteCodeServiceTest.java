package com.kyuhyeong.account.api.onboarding;

import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.entity.InviteCode;
import com.kyuhyeong.account.core.entity.User;
import com.kyuhyeong.account.core.repository.HouseholdRepository;
import com.kyuhyeong.account.core.repository.InviteCodeRepository;
import com.kyuhyeong.account.core.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link InviteCodeService} 단위 테스트 — 3일 만료 발급, 목록에서 만료 제외, 취소의 가구 가드.
 *
 * <p>{@code InviteCode} 는 {@code @Filter} 미적용이라 취소 대상은 householdId 를 조건으로 직접 조회해야 한다.
 */
@ExtendWith(MockitoExtension.class)
class InviteCodeServiceTest {

    @Mock InviteCodeRepository inviteCodeRepository;
    @Mock HouseholdRepository householdRepository;
    @Mock UserRepository userRepository;

    @InjectMocks InviteCodeService service;

    private static final Household HOUSEHOLD = Household.builder().id(10L).build();
    private static final User OWNER = User.builder().id(1L).build();

    private static InviteCode code(String value, LocalDateTime expiresAt) {
        return InviteCode.issue(HOUSEHOLD, OWNER, value, expiresAt);
    }

    @Test
    @DisplayName("generate — 3일 뒤 만료로 발급")
    void generateSetsThreeDayExpiry() {
        when(householdRepository.getReferenceById(10L)).thenReturn(HOUSEHOLD);
        when(userRepository.getReferenceById(1L)).thenReturn(OWNER);
        when(inviteCodeRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(inviteCodeRepository.save(any(InviteCode.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime before = LocalDateTime.now();
        InviteCode issued = service.generate(10L, 1L);
        LocalDateTime after = LocalDateTime.now();

        assertThat(issued.getExpiresAt())
                .isAfterOrEqualTo(before.plusDays(3))
                .isBeforeOrEqualTo(after.plusDays(3));
    }

    @Test
    @DisplayName("listActive — 만료된 코드는 빼고, 무기한(기존 발급분)은 취소할 수 있게 남긴다")
    void listActiveExcludesExpired() {
        InviteCode live = code("LIVE2345", LocalDateTime.now().plusDays(1));
        InviteCode expired = code("OLDX2345", LocalDateTime.now().minusMinutes(1));
        InviteCode legacy = code("NULL2345", null);
        when(inviteCodeRepository.findByHouseholdIdAndRevokedFalseOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(live, expired, legacy));

        assertThat(service.listActive(10L)).extracting(InviteCode::getCode)
                .containsExactly("LIVE2345", "NULL2345");
    }

    @Test
    @DisplayName("revoke — 내 가구 코드는 취소된다")
    void revokeOwnHouseholdCode() {
        InviteCode ic = code("ABCD2345", null);
        when(inviteCodeRepository.findByIdAndHouseholdId(5L, 10L)).thenReturn(Optional.of(ic));

        service.revoke(10L, 5L);

        assertThat(ic.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("revoke — 다른 가구 코드 id 면 거부 (가구 조건으로 조회되지 않음)")
    void revokeRejectsOtherHouseholdCode() {
        when(inviteCodeRepository.findByIdAndHouseholdId(5L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(20L, 5L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("초대코드를 찾을 수 없습니다.");
    }
}
