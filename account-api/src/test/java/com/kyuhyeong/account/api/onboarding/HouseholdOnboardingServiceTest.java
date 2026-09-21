package com.kyuhyeong.account.api.onboarding;

import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.entity.HouseholdMember;
import com.kyuhyeong.account.core.entity.InviteCode;
import com.kyuhyeong.account.core.entity.User;
import com.kyuhyeong.account.core.repository.HouseholdMemberRepository;
import com.kyuhyeong.account.core.repository.HouseholdRepository;
import com.kyuhyeong.account.core.repository.InviteCodeRepository;
import com.kyuhyeong.account.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HouseholdOnboardingService#joinByInviteCode} — 만료·취소된 초대코드로는 합류할 수 없다.
 */
@ExtendWith(MockitoExtension.class)
class HouseholdOnboardingServiceTest {

    @Mock HouseholdRepository householdRepository;
    @Mock HouseholdMemberRepository memberRepository;
    @Mock UserRepository userRepository;
    @Mock InviteCodeRepository inviteCodeRepository;
    @Mock DefaultCategorySeedService categorySeedService;

    @InjectMocks HouseholdOnboardingService service;

    private static final Household HOUSEHOLD = Household.builder().id(10L).build();
    private static final User OWNER = User.builder().id(1L).build();

    @BeforeEach
    void newUserWithoutHousehold() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).build()));
        when(memberRepository.findByUserId(2L)).thenReturn(List.of());
    }

    @Test
    @DisplayName("만료 전 코드 — 합류하고 사용 횟수가 오른다 (입력 공백·소문자 정규화)")
    void joinsWithValidCode() {
        InviteCode ic = InviteCode.issue(HOUSEHOLD, OWNER, "ABCD2345", LocalDateTime.now().plusDays(1));
        when(inviteCodeRepository.findByCode("ABCD2345")).thenReturn(Optional.of(ic));

        Long householdId = service.joinByInviteCode(2L, " abcd2345 ");

        assertThat(householdId).isEqualTo(10L);
        assertThat(ic.getUsedCount()).isEqualTo(1);
        verify(memberRepository).save(any(HouseholdMember.class));
    }

    @Test
    @DisplayName("만료된 코드 — 거부되고 멤버가 추가되지 않는다")
    void rejectsExpiredCode() {
        InviteCode ic = InviteCode.issue(HOUSEHOLD, OWNER, "ABCD2345", LocalDateTime.now().minusMinutes(1));
        when(inviteCodeRepository.findByCode("ABCD2345")).thenReturn(Optional.of(ic));

        assertThatThrownBy(() -> service.joinByInviteCode(2L, "ABCD2345"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않거나 만료된 초대코드입니다.");
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("취소된 코드 — 만료 전이어도 거부")
    void rejectsRevokedCode() {
        InviteCode ic = InviteCode.issue(HOUSEHOLD, OWNER, "ABCD2345", LocalDateTime.now().plusDays(1));
        ic.revoke();
        when(inviteCodeRepository.findByCode("ABCD2345")).thenReturn(Optional.of(ic));

        assertThatThrownBy(() -> service.joinByInviteCode(2L, "ABCD2345"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않거나 만료된 초대코드입니다.");
        verify(memberRepository, never()).save(any());
    }
}
