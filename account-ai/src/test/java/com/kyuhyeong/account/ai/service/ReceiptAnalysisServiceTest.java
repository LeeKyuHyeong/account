package com.kyuhyeong.account.ai.service;

import com.kyuhyeong.account.ai.client.ClaudeVisionClient;
import com.kyuhyeong.account.ai.model.MerchantHistoryContext;
import com.kyuhyeong.account.ai.model.ReceiptAnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link ReceiptAnalysisService}의 단위 테스트.
 *
 * <p>Claude API는 모킹하여 실제 호출 없이 핵심 로직을 검증:
 *
 * <ul>
 *     <li>응답이 순수 JSON일 때 정상 파싱</li>
 *     <li>응답이 코드 펜스(```json ... ```)로 감싸졌을 때 추출</li>
 *     <li>응답이 앞뒤 설명 텍스트 포함 시 JSON만 추출</li>
 *     <li>응답이 JSON이 아닐 때 명확한 예외</li>
 *     <li>신뢰도 기반 분기 플래그 (자동 확정 / 수동 분류) 정확</li>
 * </ul>
 *
 * <p>실제 Claude API 호출은 별도 {@code @IntegrationTest}에서 수행 (API 키 + 실제 영수증 이미지 필요).
 */
@ExtendWith(MockitoExtension.class)
class ReceiptAnalysisServiceTest {

    @Mock
    ClaudeVisionClient visionClient;

    @InjectMocks
    ReceiptAnalysisService service;

    @BeforeEach
    void setUp() {
        // PromptBuilder는 @PostConstruct에서 템플릿을 로드하므로 Mock으로 대체.
        PromptBuilder mockBuilder = new PromptBuilder(null) {
            @Override
            public String build(MerchantHistoryContext context) {
                return "MOCK_PROMPT";
            }
        };
        ReflectionTestUtils.setField(service, "promptBuilder", mockBuilder);
    }

    @Test
    @DisplayName("순수 JSON 응답 — 정상 파싱되어 모든 필드 매핑")
    void parsesCleanJson() {
        when(visionClient.analyzeImage(anyString(), anyString(), anyString()))
                .thenReturn("""
                        {
                          "date": "2026-05-18",
                          "merchant": "스타벅스 강남역사거리점",
                          "merchant_type": "카페",
                          "category": "외식/카페",
                          "subcategory": "커피",
                          "total": 8500,
                          "payment_method": "신용카드",
                          "items": [
                            {"name": "아이스 아메리카노 T", "price": 4500, "quantity": 1},
                            {"name": "치즈케이크", "price": 4000, "quantity": 1}
                          ],
                          "confidence": 0.95
                        }
                        """);

        ReceiptAnalysisResult result = service.analyze(
                "fake".getBytes(),
                "image/jpeg",
                MerchantHistoryContext.empty(1L));

        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 5, 18));
        assertThat(result.merchant()).isEqualTo("스타벅스 강남역사거리점");
        assertThat(result.category()).isEqualTo("외식/카페");
        assertThat(result.total()).isEqualByComparingTo(new BigDecimal("8500"));
        assertThat(result.items()).hasSize(2);
        assertThat(result.confidence()).isEqualTo(0.95);
        assertThat(result.isAutoConfirmable()).isTrue();
        assertThat(result.requiresManualClassification()).isFalse();
    }

    @Test
    @DisplayName("코드 펜스로 감싸진 응답 — 펜스 제거 후 파싱")
    void stripsCodeFence() {
        when(visionClient.analyzeImage(anyString(), anyString(), anyString()))
                .thenReturn("""
                        ```json
                        {
                          "date": "2026-05-18",
                          "merchant": "이마트 송파점",
                          "merchant_type": "마트",
                          "category": "식비",
                          "subcategory": null,
                          "total": 47800,
                          "payment_method": "체크카드",
                          "items": [],
                          "confidence": 0.92
                        }
                        ```
                        """);

        ReceiptAnalysisResult result = service.analyze(
                "fake".getBytes(),
                "image/jpeg",
                MerchantHistoryContext.empty(1L));

        assertThat(result.merchant()).isEqualTo("이마트 송파점");
        assertThat(result.category()).isEqualTo("식비");
        assertThat(result.subcategory()).isNull();
    }

    @Test
    @DisplayName("앞뒤 설명 텍스트가 붙은 응답 — 첫 { 부터 마지막 } 까지 추출")
    void extractsJsonFromSurroundingText() {
        when(visionClient.analyzeImage(anyString(), anyString(), anyString()))
                .thenReturn("""
                        분석 결과는 다음과 같습니다:
                        {
                          "date": "2026-05-18",
                          "merchant": "GS25 잠실역점",
                          "merchant_type": "편의점",
                          "category": "외식/카페",
                          "total": 3200,
                          "payment_method": "간편결제",
                          "items": [],
                          "confidence": 0.7
                        }
                        분류 신뢰도가 다소 낮습니다.
                        """);

        ReceiptAnalysisResult result = service.analyze(
                "fake".getBytes(),
                "image/jpeg",
                MerchantHistoryContext.empty(1L));

        assertThat(result.merchant()).isEqualTo("GS25 잠실역점");
        assertThat(result.confidence()).isEqualTo(0.7);
        // 0.7 → 자동 확정 X, 수동 분류 요구 X (중간 신뢰도)
        assertThat(result.isAutoConfirmable()).isFalse();
        assertThat(result.requiresManualClassification()).isFalse();
    }

    @Test
    @DisplayName("저신뢰도 응답 — 수동 분류 플래그 활성")
    void flagsLowConfidenceForManualClassification() {
        when(visionClient.analyzeImage(anyString(), anyString(), anyString()))
                .thenReturn("""
                        {
                          "date": "2026-05-18",
                          "merchant": "ALPHA STORE",
                          "merchant_type": "기타",
                          "category": "기타 변동",
                          "total": 15000,
                          "items": [],
                          "confidence": 0.35
                        }
                        """);

        ReceiptAnalysisResult result = service.analyze(
                "fake".getBytes(),
                "image/jpeg",
                MerchantHistoryContext.empty(1L));

        assertThat(result.requiresManualClassification()).isTrue();
        assertThat(result.isAutoConfirmable()).isFalse();
    }

    @Test
    @DisplayName("JSON이 아닌 응답 — AnalysisException")
    void throwsOnNonJsonResponse() {
        when(visionClient.analyzeImage(anyString(), anyString(), anyString()))
                .thenReturn("죄송합니다. 영수증을 인식할 수 없습니다.");

        assertThatThrownBy(() -> service.analyze(
                "fake".getBytes(),
                "image/jpeg",
                MerchantHistoryContext.empty(1L)))
                .isInstanceOf(ReceiptAnalysisService.AnalysisException.class)
                .hasMessageContaining("No JSON object");
    }

    @Test
    @DisplayName("빈 이미지 바이트 — AnalysisException")
    void throwsOnEmptyImage() {
        assertThatThrownBy(() -> service.analyze(
                new byte[0],
                "image/jpeg",
                MerchantHistoryContext.empty(1L)))
                .isInstanceOf(ReceiptAnalysisService.AnalysisException.class)
                .hasMessageContaining("Empty image");
    }
}
