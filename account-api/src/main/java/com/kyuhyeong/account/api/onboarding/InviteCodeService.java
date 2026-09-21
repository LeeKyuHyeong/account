package com.kyuhyeong.account.api.onboarding;

import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.entity.InviteCode;
import com.kyuhyeong.account.core.entity.User;
import com.kyuhyeong.account.core.repository.HouseholdRepository;
import com.kyuhyeong.account.core.repository.InviteCodeRepository;
import com.kyuhyeong.account.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 초대코드 발급/조회/취소 — OWNER 가 가구 설정에서 코드를 만들고 취소한다.
 *
 * <p>코드는 혼동 문자(0/O/1/I 등)를 뺀 8자 영숫자. 충돌 시 재시도. 발급 후 {@link #VALID_FOR} 동안 유효
 * (카카오톡으로 공유되므로 메시지가 퍼져도 노출 기간을 제한한다). {@link InviteCode} 는
 * 비격리 엔티티라 가입 전(가구 컨텍스트 없음)에도 코드로 조회된다 — 가구 조건은 코드에서 직접 건다.
 */
@Service
@RequiredArgsConstructor
public class InviteCodeService {

    /** 혼동되기 쉬운 0/O/1/I/L 제외. */
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final int MAX_ATTEMPTS = 10;
    /** 초대코드 유효 기간 (개발자 결정 2026-09-21). */
    static final Duration VALID_FOR = Duration.ofDays(3);

    private final InviteCodeRepository inviteCodeRepository;
    private final HouseholdRepository householdRepository;
    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    /** 가구의 사용 가능한 초대코드 목록 (최신순) — 취소·만료 제외. 만료 없는 기존 발급분은 취소할 수 있게 남는다. */
    @Transactional(readOnly = true)
    public List<InviteCode> listActive(Long householdId) {
        LocalDateTime now = LocalDateTime.now();
        return inviteCodeRepository.findByHouseholdIdAndRevokedFalseOrderByCreatedAtDesc(householdId).stream()
                .filter(ic -> ic.isValid(now))
                .toList();
    }

    /** 신규 초대코드 발급 ({@link #VALID_FOR} 뒤 만료). 충돌하지 않는 코드를 생성해 저장한다. */
    @Transactional
    public InviteCode generate(Long householdId, Long creatorUserId) {
        Household household = householdRepository.getReferenceById(householdId);
        User creator = userRepository.getReferenceById(creatorUserId);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = randomCode();
            if (inviteCodeRepository.findByCode(code).isEmpty()) {
                return inviteCodeRepository.save(InviteCode.issue(household, creator, code,
                        LocalDateTime.now().plus(VALID_FOR)));
            }
        }
        throw new IllegalStateException("초대코드 생성에 실패했습니다. 잠시 후 다시 시도하세요.");
    }

    /** 초대코드 취소 — 내 가구의 코드만. 다른 가구 코드 id 면 {@link IllegalArgumentException}. */
    @Transactional
    public void revoke(Long householdId, Long inviteCodeId) {
        inviteCodeRepository.findByIdAndHouseholdId(inviteCodeId, householdId)
                .orElseThrow(() -> new IllegalArgumentException("초대코드를 찾을 수 없습니다."))
                .revoke();
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
