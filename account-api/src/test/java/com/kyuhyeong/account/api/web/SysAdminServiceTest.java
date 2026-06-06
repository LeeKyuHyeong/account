package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.api.web.SysAdminService.HouseholdView;
import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.entity.HouseholdMember;
import com.kyuhyeong.account.core.entity.User;
import com.kyuhyeong.account.core.enums.PlanType;
import com.kyuhyeong.account.core.repository.HouseholdMemberRepository;
import com.kyuhyeong.account.core.repository.HouseholdRepository;
import com.kyuhyeong.account.core.repository.ReceiptRepository;
import com.kyuhyeong.account.core.tenant.HouseholdContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SysAdminService} 단위 테스트 — 가구 횡단 집계.
 *
 * <p>핵심은 컨텍스트 전환: 영수증은 {@code @Filter} 격리 엔티티라 가구별 카운트 전에
 * {@code HouseholdContext} 를 해당 가구로 명시 전환하고, 종료 후 관리자 본인 가구로
 * 복원해야 한다 (요청 잔여 구간의 격리 오염 방지). 영수증 카운트 stub 이 호출 시점의
 * 컨텍스트 값을 반환하게 해 전환 여부를 결과로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class SysAdminServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock HouseholdRepository householdRepository;
    @Mock HouseholdMemberRepository memberRepository;
    @Mock ReceiptRepository receiptRepository;

    @InjectMocks SysAdminService service;

    @BeforeEach
    void bindHousehold() {
        // SessionHouseholdContextFilter 가 관리자 본인 가구(1L)로 채워둔 상태 재현
        HouseholdContext.set(1L);
    }

    @AfterEach
    void clearHousehold() {
        HouseholdContext.clear();
    }

    private static Household household(long id, String name, PlanType plan, String ownerName) {
        return Household.builder()
                .id(id)
                .name(name)
                .planType(plan)
                .owner(User.builder().id(id * 100).name(ownerName).provider(User.PROVIDER_KAKAO).build())
                .createdAt(LocalDateTime.of(2026, 1, 15, 12, 0))
                .build();
    }

    @Test
    @DisplayName("listHouseholds — 가구별 멤버 수·플랜·이번 달 영수증 사용량을 view 로 매핑")
    void listHouseholdsMapsFields() {
        when(householdRepository.findAll()).thenReturn(List.of(
                household(1L, "우리집", PlanType.FREE, "본인"),
                household(2L, "테스트가구", PlanType.PRO, "친구")));
        when(memberRepository.findByHouseholdId(1L)).thenReturn(List.of(
                HouseholdMember.builder().build(), HouseholdMember.builder().build()));
        when(memberRepository.findByHouseholdId(2L)).thenReturn(List.of(
                HouseholdMember.builder().build()));
        // 호출 시점의 HouseholdContext 값 × 10 — 가구별 컨텍스트 전환 여부를 카운트로 노출
        when(receiptRepository.countByCreatedAtGreaterThanEqual(any()))
                .thenAnswer(inv -> HouseholdContext.get() * 10);

        List<HouseholdView> result = service.listHouseholds();

        assertThat(result).hasSize(2);
        HouseholdView first = result.get(0);
        assertThat(first.name()).isEqualTo("우리집");
        assertThat(first.ownerName()).isEqualTo("본인");
        assertThat(first.planType()).isEqualTo("FREE");
        assertThat(first.memberCount()).isEqualTo(2);
        assertThat(first.receiptsThisMonth()).isEqualTo(10L); // 가구 1 컨텍스트에서 집계
        assertThat(first.receiptQuota()).isEqualTo(PlanType.FREE.monthlyReceiptQuota());
        assertThat(first.unlimitedReceipts()).isFalse();
        assertThat(first.createdAt()).isEqualTo(LocalDate.of(2026, 1, 15));

        HouseholdView second = result.get(1);
        assertThat(second.memberCount()).isEqualTo(1);
        assertThat(second.receiptsThisMonth()).isEqualTo(20L); // 가구 2 컨텍스트에서 집계
        assertThat(second.unlimitedReceipts()).isTrue();
    }

    @Test
    @DisplayName("listHouseholds — 이번 달 KST 월초 기준으로 카운트 + 종료 후 원래 컨텍스트 복원")
    void countsFromMonthStartAndRestoresContext() {
        when(householdRepository.findAll()).thenReturn(List.of(
                household(2L, "테스트가구", PlanType.FREE, "친구")));
        when(memberRepository.findByHouseholdId(2L)).thenReturn(List.of());
        when(receiptRepository.countByCreatedAtGreaterThanEqual(any())).thenReturn(0L);

        service.listHouseholds();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(receiptRepository, times(1)).countByCreatedAtGreaterThanEqual(captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(LocalDate.now(KST).withDayOfMonth(1).atStartOfDay());
        assertThat(HouseholdContext.get()).isEqualTo(1L); // 관리자 본인 가구로 복원
    }

    @Test
    @DisplayName("listHouseholds — 집계 중 예외가 나도 원래 컨텍스트로 복원")
    void restoresContextOnFailure() {
        when(householdRepository.findAll()).thenReturn(List.of(
                household(2L, "테스트가구", PlanType.FREE, "친구")));
        when(memberRepository.findByHouseholdId(2L)).thenReturn(List.of());
        when(receiptRepository.countByCreatedAtGreaterThanEqual(any()))
                .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> service.listHouseholds())
                .isInstanceOf(RuntimeException.class);

        assertThat(HouseholdContext.get()).isEqualTo(1L);
    }

    @Test
    @DisplayName("listHouseholds — 컨텍스트 미설정 상태에서 호출되면 종료 후에도 미설정 유지")
    void clearsWhenNoOriginalContext() {
        HouseholdContext.clear();
        when(householdRepository.findAll()).thenReturn(List.of(
                household(2L, "테스트가구", PlanType.FREE, "친구")));
        when(memberRepository.findByHouseholdId(2L)).thenReturn(List.of());
        when(receiptRepository.countByCreatedAtGreaterThanEqual(any())).thenReturn(0L);

        service.listHouseholds();

        assertThat(HouseholdContext.isSet()).isFalse();
    }
}
