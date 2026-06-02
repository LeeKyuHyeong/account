package com.kyuhyeong.account.api.onboarding;

import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.entity.HouseholdMember;
import com.kyuhyeong.account.core.entity.InviteCode;
import com.kyuhyeong.account.core.entity.User;
import com.kyuhyeong.account.core.enums.HouseholdRole;
import com.kyuhyeong.account.core.enums.PlanType;
import com.kyuhyeong.account.core.repository.HouseholdMemberRepository;
import com.kyuhyeong.account.core.repository.HouseholdRepository;
import com.kyuhyeong.account.core.repository.InviteCodeRepository;
import com.kyuhyeong.account.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 온보딩 — 카카오 로그인 후 가구가 없는 유저가 "가족 만들기" 또는 "초대코드 입력하기" 로 가구에 합류.
 *
 * <p>가구/멤버십/초대코드는 모두 비격리 엔티티라 가구 컨텍스트 없이 동작한다. 가구 생성 시 기본
 * 카테고리({@link DefaultCategorySeedService})를 같은 트랜잭션에서 시드한다.
 */
@Service
@RequiredArgsConstructor
public class HouseholdOnboardingService {

    private static final int DEFAULT_RETENTION_MONTHS = 60;
    private static final int DEFAULT_MAX_MEMBERS = 20;

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final DefaultCategorySeedService categorySeedService;

    /** 가족 만들기 — 유저를 OWNER 로 새 가구를 만들고 기본 카테고리를 시드한다. 반환=새 가구 id. */
    @Transactional
    public Long createHousehold(Long userId, String name) {
        User user = requireUserWithoutHousehold(userId);
        Household household = householdRepository.save(Household.builder()
                .name(name)
                .planType(PlanType.FREE)
                .owner(user)
                .dataRetentionMonths(DEFAULT_RETENTION_MONTHS)
                .maxMembers(DEFAULT_MAX_MEMBERS)
                .build());
        memberRepository.save(HouseholdMember.builder()
                .household(household)
                .user(user)
                .role(HouseholdRole.OWNER)
                .build());
        categorySeedService.seed(household);
        return household.getId();
    }

    /** 초대코드 입력하기 — 유효한 코드면 유저를 MEMBER 로 해당 가구에 합류시킨다. 반환=합류한 가구 id. */
    @Transactional
    public Long joinByInviteCode(Long userId, String rawCode) {
        User user = requireUserWithoutHousehold(userId);
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase();
        InviteCode invite = inviteCodeRepository.findByCode(code)
                .filter(ic -> ic.isValid(LocalDateTime.now()))
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않거나 만료된 초대코드입니다."));
        Household household = invite.getHousehold();
        memberRepository.save(HouseholdMember.builder()
                .household(household)
                .user(user)
                .role(HouseholdRole.MEMBER)
                .invitedBy(invite.getCreatedBy())
                .build());
        invite.markUsed();
        return household.getId();
    }

    /** 유저 존재 + 아직 가구 미가입 검증. (User/HouseholdMember 는 비격리라 findById 안전.) */
    private User requireUserWithoutHousehold(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + userId));
        if (!memberRepository.findByUserId(userId).isEmpty()) {
            throw new IllegalStateException("이미 가구에 속해 있습니다.");
        }
        return user;
    }
}
