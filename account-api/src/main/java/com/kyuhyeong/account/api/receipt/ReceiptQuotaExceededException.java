package com.kyuhyeong.account.api.receipt;

/**
 * 가구의 이번 달 영수증 AI 분석 한도(플랜 티어별)를 초과했을 때 발생 (구독 Phase 1).
 *
 * <p>{@link ReceiptIngestionService#ingest} 가 디스크 저장·Claude 호출 전에 fail-fast 로 던진다 —
 * 한도 초과 요청은 비용을 발생시키지 않는다. {@code WebReceiptController} 가 잡아 업로드 화면에
 * 친절 안내 + 플랜 업그레이드 링크로 재렌더한다.
 */
public class ReceiptQuotaExceededException extends RuntimeException {

    private final int limit;

    public ReceiptQuotaExceededException(int limit) {
        super("Monthly receipt AI analysis quota exceeded (limit=" + limit + ")");
        this.limit = limit;
    }

    /** 초과한 월 한도 (사용자 안내 메시지에 노출). */
    public int getLimit() {
        return limit;
    }
}
