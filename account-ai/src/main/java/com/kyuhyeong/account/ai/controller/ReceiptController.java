package com.kyuhyeong.account.ai.controller;

import com.kyuhyeong.account.ai.model.MerchantHistoryContext;
import com.kyuhyeong.account.ai.model.ReceiptAnalysisResult;
import com.kyuhyeong.account.ai.service.MerchantHistoryProvider;
import com.kyuhyeong.account.ai.service.ReceiptAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 영수증 OCR + AI 분류 REST 엔드포인트.
 *
 * <p>본 컨트롤러는 분석 결과만 반환하며, DB 저장({@code receipts}, {@code transactions})은
 * 후속 PR(account-api 모듈)에서 추가한다. 본 프로토타입의 책임:
 *
 * <ol>
 *     <li>multipart 이미지 업로드 수신</li>
 *     <li>가구 컨텍스트 식별 (현재는 헤더, 추후 JWT 클레임)</li>
 *     <li>학습 이력 조회 → 분석 서비스 호출 → JSON 반환</li>
 * </ol>
 *
 * <p>인증/인가는 {@code account-api} 모듈의 Spring Security 통합 시점에 추가.
 * 현재는 {@code X-Household-Id} 헤더만으로 가구 식별 (개발 모드 전용).
 */
@RestController
@RequestMapping(value = "/api/receipts", produces = MediaType.APPLICATION_JSON_VALUE)
public class ReceiptController {

    private static final Logger log = LoggerFactory.getLogger(ReceiptController.class);
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;  // 10MB
    private static final String HOUSEHOLD_ID_HEADER = "X-Household-Id";

    private final ReceiptAnalysisService analysisService;
    private final MerchantHistoryProvider historyProvider;

    public ReceiptController(
            ReceiptAnalysisService analysisService,
            MerchantHistoryProvider historyProvider) {
        this.analysisService = analysisService;
        this.historyProvider = historyProvider;
    }

    /**
     * 영수증 이미지를 받아 Claude로 분석하고 결과를 반환.
     *
     * <p>POST /api/receipts (multipart/form-data)
     * <pre>
     *   image: (file) 영수증 이미지
     *   X-Household-Id: (header) 가구 ID
     * </pre>
     *
     * @param image 업로드된 영수증 이미지 (JPEG/PNG/WebP/GIF)
     * @param householdId 가구 식별자 (헤더)
     * @return 분석 결과 + 분류 신뢰도 + 다음 단계 안내 플래그
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnalyzeResponse> analyze(
            @RequestPart("image") MultipartFile image,
            @RequestHeader(HOUSEHOLD_ID_HEADER) Long householdId) throws IOException {

        validateImage(image);

        log.info("Analyzing receipt: householdId={}, filename='{}', size={} bytes, contentType={}",
                householdId, image.getOriginalFilename(), image.getSize(), image.getContentType());

        // 가구별 학습 이력 조회 (없으면 빈 컨텍스트)
        MerchantHistoryContext history = historyProvider.getRecentHistory(
                householdId, MerchantHistoryContext.DEFAULT_MAX_ENTRIES);

        // Claude 분석
        ReceiptAnalysisResult result = analysisService.analyze(
                image.getBytes(),
                image.getContentType(),
                history);

        // 사용자 측 후속 처리 힌트 동봉
        AnalyzeResponse response = new AnalyzeResponse(
                result,
                result.isAutoConfirmable(),
                result.requiresManualClassification()
        );

        return ResponseEntity.ok(response);
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }
        if (image.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Image too large: " + image.getSize() + " bytes (max " + MAX_FILE_SIZE_BYTES + ")");
        }
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException(
                    "Unsupported content type: " + contentType + " (must be image/*)");
        }
    }

    /**
     * 분석 결과 + 후속 처리 힌트.
     *
     * @param result        Claude 분석 결과
     * @param autoConfirmable  true이면 클라이언트가 즉시 CONFIRMED 거래 생성해도 됨
     * @param requiresManualClassification  true이면 사용자에게 카테고리 수동 선택 요구
     */
    public record AnalyzeResponse(
            ReceiptAnalysisResult result,
            boolean autoConfirmable,
            boolean requiresManualClassification
    ) {}

    // ───────────────────────────────────────────────────────────
    // 예외 핸들러 (간단 버전 — 추후 @ControllerAdvice로 분리)
    // ───────────────────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(ReceiptAnalysisService.AnalysisException.class)
    public ResponseEntity<ErrorResponse> handleAnalysisFailure(
            ReceiptAnalysisService.AnalysisException e) {
        log.warn("Receipt analysis failed", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("AI_ANALYSIS_FAILED", e.getMessage()));
    }

    public record ErrorResponse(String code, String message) {}
}
