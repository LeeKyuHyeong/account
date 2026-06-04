package com.kyuhyeong.account.api.receipt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyuhyeong.account.ai.model.ReceiptAnalysisResult;
import com.kyuhyeong.account.core.entity.Transaction;
import com.kyuhyeong.account.core.enums.TransactionStatus;
import com.kyuhyeong.account.core.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 영수증 AI 분석값 vs 현재 저장값 비교 (분석 정확도 모니터링).
 *
 * <p>AI 가 처음 분석한 원본은 {@code receipts.ocr_raw_json} 에 그대로 보관되므로
 * (감사/재학습용), 그걸 파싱해 현재 거래 필드와 필드별로 대조한다. 사용자가 컨펌
 * 화면에서 뭘 고쳤는지가 그대로 드러나 프롬프트 개선 포인트를 찾는 데 쓴다.
 *
 * <p>읽기 전용 — 새 테이블 없음. 가구 격리는 {@code householdFilter} 자동 적용.
 * raw JSON 파싱 실패(과거 포맷 등) 시 해당 행의 AI 측은 빈 값으로 표시하고 넘어간다.
 */
@Service
@RequiredArgsConstructor
public class ReceiptAccuracyService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptAccuracyService.class);

    /** 요약 블록의 일별 통계 표시 일수. */
    private static final int SUMMARY_DAYS = 7;

    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    /** 영수증으로 생성된 거래 최근 {@code limit} 건의 비교 행 (업로드 역순). */
    @Transactional(readOnly = true)
    public List<Row> listRecent(int limit) {
        Specification<Transaction> receiptBacked = (root, query, cb) -> cb.and(
                cb.isNotNull(root.get("receipt")),
                cb.isNull(root.get("deletedAt")));
        return transactionRepository
                .findAll(receiptBacked, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "id")))
                .getContent().stream()
                .map(this::toRow)
                .toList();
    }

    private Row toRow(Transaction tx) {
        ReceiptAnalysisResult analysis = parse(tx.getReceipt().getOcrRawJson());
        Side ai = analysis == null
                ? new Side(null, null, null, null, null)
                : new Side(analysis.merchant(), analysis.category(), analysis.total(),
                        analysis.paymentMethod(), analysis.date());
        Side saved = new Side(tx.getMerchant(), tx.getCategory().getName(), tx.getAmount(),
                tx.getPaymentMethod(), tx.getOccurredAt().toLocalDate());
        Diff diff = analysis == null
                ? new Diff(false, false, false, false, false)
                : new Diff(
                        !Objects.equals(ai.merchant(), saved.merchant()),
                        !Objects.equals(ai.category(), saved.category()),
                        ai.amount() == null || ai.amount().compareTo(saved.amount()) != 0,
                        !Objects.equals(ai.paymentMethod(), saved.paymentMethod()),
                        !Objects.equals(ai.date(), saved.date()));
        int confidencePct = tx.getConfidence() == null ? 0
                : tx.getConfidence().multiply(BigDecimal.valueOf(100)).intValue();
        return new Row(tx.getId(), tx.getReceipt().getId(), tx.getCreatedAt(),
                tx.getStatus(), confidencePct, ai, saved, diff);
    }

    /**
     * 조회된 비교 행들의 요약 — 화면 상단 블록용. 같은 행 리스트에서 파생하므로
     * 추가 쿼리 없음 (집계는 on-the-fly — V6 에서 월말 집계 잡을 제거한 것과 같은 원칙).
     */
    public Summary summarize(List<Row> rows) {
        List<DailyStat> daily = rows.stream()
                .collect(Collectors.groupingBy(r -> r.uploadedAt().toLocalDate()))
                .entrySet().stream()
                .map(e -> new DailyStat(
                        e.getKey(),
                        e.getValue().size(),
                        e.getValue().stream().filter(Row::hasDiff).count()))
                .sorted(Comparator.comparing(DailyStat::date).reversed())
                .limit(SUMMARY_DAYS)
                .toList();
        FieldStats fields = new FieldStats(
                rows.stream().filter(r -> r.diff().merchant()).count(),
                rows.stream().filter(r -> r.diff().category()).count(),
                rows.stream().filter(r -> r.diff().amount()).count(),
                rows.stream().filter(r -> r.diff().paymentMethod()).count(),
                rows.stream().filter(r -> r.diff().date()).count());
        return new Summary(daily, fields);
    }

    private ReceiptAnalysisResult parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ReceiptAnalysisResult.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse stored ocr_raw_json; showing empty AI side", e);
            return null;
        }
    }

    /** 비교 한 행 — 거래 1건 = 영수증 1장. */
    public record Row(
            Long transactionId,
            Long receiptId,
            LocalDateTime uploadedAt,
            TransactionStatus status,
            int confidencePct,
            Side ai,
            Side saved,
            Diff diff
    ) {
        /** 한 필드라도 수정됐는지 — 화면 상단 요약용. */
        public boolean hasDiff() {
            return diff.merchant() || diff.category() || diff.amount()
                    || diff.paymentMethod() || diff.date();
        }
    }

    /** 한쪽(AI 분석 또는 현재 저장값)의 비교 대상 필드. */
    public record Side(
            String merchant,
            String category,
            BigDecimal amount,
            String paymentMethod,
            LocalDate date
    ) {}

    /** 필드별 변경 여부 — 템플릿이 행 하이라이트에 사용. */
    public record Diff(
            boolean merchant,
            boolean category,
            boolean amount,
            boolean paymentMethod,
            boolean date
    ) {}

    /** 화면 상단 요약 — 일별 추이 + 필드별 누적 수정 횟수. */
    public record Summary(List<DailyStat> daily, FieldStats fields) {}

    /** 하루치 통계 (업로드 일자 기준). */
    public record DailyStat(LocalDate date, long total, long changed) {}

    /** 필드별 수정 누적 횟수 — 어느 필드가 프롬프트 보강 대상인지 가리킨다. */
    public record FieldStats(
            long merchant,
            long category,
            long amount,
            long paymentMethod,
            long date
    ) {}
}
