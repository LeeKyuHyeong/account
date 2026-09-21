package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.InviteCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 초대코드 Repository.
 *
 * <p>{@link InviteCode} 는 @Filter 미적용(비격리)이므로 가입 전(가구 컨텍스트 없음)에도 코드로 직접 조회된다.
 * 가구별 목록 조회는 household_id 를 명시 조건으로 가드한다.
 */
public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

    /** 온보딩 "초대코드 입력하기" — 코드로 단건 조회. 유효성(미취소/미만료)은 서비스에서 isValid 로 판정. */
    Optional<InviteCode> findByCode(String code);

    /** 가구 설정의 미취소 초대코드 목록 (최신순). 만료 제외는 서비스에서 isValid 로. */
    List<InviteCode> findByHouseholdIdAndRevokedFalseOrderByCreatedAtDesc(Long householdId);

    /** 초대코드 취소 — 가구 조건을 함께 걸어 다른 가구의 코드 id 로는 조회되지 않게 한다(비격리 엔티티 가드). */
    Optional<InviteCode> findByIdAndHouseholdId(Long id, Long householdId);
}
