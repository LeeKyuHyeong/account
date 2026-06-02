package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.api.web.AdminUserService.MemberView;
import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.entity.HouseholdMember;
import com.kyuhyeong.account.core.entity.User;
import com.kyuhyeong.account.core.enums.HouseholdRole;
import com.kyuhyeong.account.core.repository.HouseholdMemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link AdminUserService} 단위 테스트 — Repository 모킹.
 *
 * <p>핵심은 가구 격리 가드: {@code User} / {@code HouseholdMember} 는 {@code @Filter} 미적용이라
 * 서비스가 householdId 멤버십을 직접 조건으로 멤버를 조회해 타 가구 사용자가 섞이지 않게 한다.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock HouseholdMemberRepository memberRepository;

    @InjectMocks AdminUserService service;

    private static User user(long id, String name) {
        return User.builder().id(id).name(name).provider(User.PROVIDER_KAKAO).build();
    }

    private static HouseholdMember member(User u, HouseholdRole role) {
        return HouseholdMember.builder()
                .household(Household.builder().id(1L).build())
                .user(u)
                .role(role)
                .build();
    }

    @Test
    @DisplayName("listMembers — 멤버를 userId 오름차순 view 로 매핑")
    void listMembersMapsAndSorts() {
        User spouse = user(2L, "배우자");
        User owner = user(1L, "본인");
        when(memberRepository.findByHouseholdId(1L)).thenReturn(List.of(
                member(spouse, HouseholdRole.MEMBER),
                member(owner, HouseholdRole.OWNER)));

        List<MemberView> result = service.listMembers(1L);

        assertThat(result).extracting(MemberView::userId).containsExactly(1L, 2L);
        assertThat(result.get(0).role()).isEqualTo("OWNER");
        assertThat(result.get(1).name()).isEqualTo("배우자");
    }
}
