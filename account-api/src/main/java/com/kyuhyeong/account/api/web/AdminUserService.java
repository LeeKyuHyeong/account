package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.core.entity.User;
import com.kyuhyeong.account.core.repository.HouseholdMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 가구 설정 페이지(OWNER 전용)의 멤버 조회.
 *
 * <p><b>격리 주의</b>: {@code User} / {@code HouseholdMember} 는 Hibernate {@code @Filter} 적용
 * 대상이 아니므로(전역 식별 단위) 가구 경계를 코드로 직접 강제한다 — 멤버 조회 시 householdId 를
 * 명시 조건으로 검증해 타 가구 사용자 접근을 차단한다.
 *
 * <p>웹 컨트롤러는 @Transactional 경계가 없으므로(open-in-view=false) 지연 로딩 연관
 * (HouseholdMember → User) 접근을 본 서비스의 @Transactional 안에서 끝낸다.
 *
 * <p>(카카오 단독 인증 전환으로 비밀번호 재설정 기능은 제거됨 — 비번 로그인이 더는 없음.)
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final HouseholdMemberRepository memberRepository;

    /** 가구 멤버 목록 (userId 오름차순). */
    @Transactional(readOnly = true)
    public List<MemberView> listMembers(Long householdId) {
        return memberRepository.findByHouseholdId(householdId).stream()
                .map(m -> {
                    User u = m.getUser();
                    return new MemberView(u.getId(), u.getName(),
                            m.getRole().name(), u.getLastLoginAt());
                })
                .sorted(Comparator.comparing(MemberView::userId))
                .toList();
    }

    /** 멤버 목록 표시용 view. (카카오 닉네임=name. 이메일은 더 이상 수집 안 함.) */
    public record MemberView(Long userId, String name, String role,
                             LocalDateTime lastLoginAt) {
    }
}
