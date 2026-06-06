package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.entity.HouseholdMember;
import com.kyuhyeong.account.core.repository.HouseholdMemberRepository;
import com.kyuhyeong.account.core.repository.HouseholdRepository;
import com.kyuhyeong.account.core.repository.JobRunRepository;
import com.kyuhyeong.account.core.repository.LoginLogRepository;
import com.kyuhyeong.account.core.repository.PushSubscriptionRepository;
import com.kyuhyeong.account.core.repository.ReceiptRepository;
import com.kyuhyeong.account.core.tenant.HouseholdContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 앱 관리자(시스템 관리) 화면용 얇은 어댑터 — 가구 횡단 현황 집계.
 *
 * <p>가구장 화면({@code AdminUserService} — 본인 가구 한정)과 달리 전체 가구를 조회한다.
 * 접근은 {@code SecurityConfig} 의 {@code ROLE_SYSADMIN} 게이트가 막는다.
 *
 * <p>영수증은 {@code @Filter} 격리 엔티티 — sysadmin 요청 중 {@code HouseholdContext} 는
 * 관리자 본인 가구로 세팅돼 있으므로, 가구별 카운트 전에 컨텍스트를 해당 가구로 명시 전환한다
 * ({@code RecurringTransactionService.runDueForHousehold} 의 검증된 패턴 —
 * {@code HouseholdFilterAspect} 가 repository 프록시 호출마다 현재 컨텍스트로 필터를 재바인딩).
 * 종료 후 원래 컨텍스트로 복원해 요청 잔여 구간의 격리 오염을 막는다.
 * (푸시 구독/로그인 이력/잡 실행은 비격리 — 컨텍스트와 무관.)
 *
 * <p>플랜 변경은 기존 {@link PlanService#changePlan} 재사용 — 본 서비스는 조회만 담당.
 */
@Service
@RequiredArgsConstructor
public class SysAdminService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository memberRepository;
    private final ReceiptRepository receiptRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final LoginLogRepository loginLogRepository;
    private final JobRunRepository jobRunRepository;

    /** 전체 가구 현황 — 멤버 수·플랜·이번 달 영수증 AI 사용량/한도·푸시 기기 수. */
    @Transactional(readOnly = true)
    public List<HouseholdView> listHouseholds() {
        LocalDateTime monthStart = monthStart();
        Long original = HouseholdContext.isSet() ? HouseholdContext.get() : null;
        try {
            return householdRepository.findAll().stream()
                    .map(h -> toView(h, monthStart))
                    .toList();
        } finally {
            if (original != null) {
                HouseholdContext.set(original);
            } else {
                HouseholdContext.clear();
            }
        }
    }

    /** 최근 로그인 30건 — 카카오 로그인 성공 이력 (비격리, admin 전용 조회). */
    @Transactional(readOnly = true)
    public List<LoginView> listRecentLogins() {
        return loginLogRepository.findTop30ByOrderByIdDesc().stream()
                .map(l -> new LoginView(l.getUser().getName(), l.getIp(), l.getUserAgent(), l.getCreatedAt()))
                .toList();
    }

    /** 최근 잡 실행 15건 — 반복거래/푸시 다이제스트/월말 결산 스케줄 발화 결과. */
    @Transactional(readOnly = true)
    public List<JobRunView> listRecentJobRuns() {
        return jobRunRepository.findTop15ByOrderByIdDesc().stream()
                .map(r -> new JobRunView(r.getJobName(), r.isOk(), r.getDetail(), r.getRanAt()))
                .toList();
    }

    private HouseholdView toView(Household h, LocalDateTime monthStart) {
        // HouseholdMember / PushSubscription 은 비격리 — id 직접 조건으로 조회 (코드 가드 패턴)
        List<Long> memberUserIds = memberRepository.findByHouseholdId(h.getId()).stream()
                .map(HouseholdMember::getUser)
                .map(u -> u.getId())
                .toList();
        int pushDevices = pushSubscriptionRepository.findAllByUserIdIn(memberUserIds).size();
        // Receipt 는 격리 — 해당 가구 컨텍스트로 전환 후 카운트 (필터 재바인딩)
        HouseholdContext.set(h.getId());
        long receiptsThisMonth = receiptRepository.countByCreatedAtGreaterThanEqual(monthStart);
        return new HouseholdView(
                h.getId(),
                h.getName(),
                h.getOwner().getName(),
                h.getPlanType().name(),
                h.getPlanType().displayName(),
                memberUserIds.size(),
                receiptsThisMonth,
                h.getPlanType().monthlyReceiptQuota(),
                h.getPlanType().isUnlimitedReceipts(),
                pushDevices,
                h.getCreatedAt().toLocalDate());
    }

    private LocalDateTime monthStart() {
        return LocalDate.now(KST).withDayOfMonth(1).atStartOfDay();
    }

    /** 가구 현황 행 1개. */
    public record HouseholdView(
            Long id,
            String name,
            String ownerName,
            String planType,
            String planDisplayName,
            int memberCount,
            long receiptsThisMonth,
            int receiptQuota,
            boolean unlimitedReceipts,
            int pushDevices,
            LocalDate createdAt) {}

    /** 접속 로그 행 1개. */
    public record LoginView(String userName, String ip, String userAgent, LocalDateTime at) {}

    /** 잡 실행 행 1개. */
    public record JobRunView(String jobName, boolean ok, String detail, LocalDateTime ranAt) {}
}
