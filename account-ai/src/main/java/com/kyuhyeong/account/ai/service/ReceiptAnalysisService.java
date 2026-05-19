package com.kyuhyeong.account.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kyuhyeong.account.ai.client.ClaudeVisionClient;
import com.kyuhyeong.account.ai.model.MerchantHistoryContext;
import com.kyuhyeong.account.ai.model.ReceiptAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 영수증 분석의 단일 진입점.
 *
 * <p>호출자(예: 컨트롤러)는 이미지 바이트 + 가구 컨텍스트만 넘기면 되고,
 * 다음 단계를 본 서비스가 일괄 처리한다:
 *
 * <ol>
 *     <li>이미지 base64 인코딩</li>
 *     <li>프롬프트 조립 (가구별 가맹점 학습 이력 주입)</li>
 *     <li>Claude Vision API 호출</li>
 *     <li>응답 텍스트에서 JSON 추출 (Claude가 가끔 코드 펜스 ```json 등을 붙임)</li>
 *     <li>JSON → {@link ReceiptAnalysisResult} 역직렬화</li>
 * </ol>
 *
 * <p>이미지 자체의 압축·리사이즈는 본 서비스 책임이 아님 — Flutter 클라이언트에서
 * 업로드 전 1280px 이하로 압축하는 것을 표준으로 한다. 서버 측 추가 압축은 별도
 * 이미지 처리 서비스에서 수행.
 */
@Service
public class ReceiptAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptAnalysisService.class);

    /**
     * Claude가 가끔 응답을 ```json ... ``` 코드 펜스로 감싸는 케이스를 처리.
     * 프롬프트에서 "코드 펜스 금지"라고 명시했지만 100% 보장되진 않음 — 방어적으로 제거.
     */
    private static final Pattern CODE_FENCE_PATTERN = Pattern.compile(
            "(?s)```(?:json)?\\s*(\\{.*?\\})\\s*```");

    private final ClaudeVisionClient visionClient;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public ReceiptAnalysisService(ClaudeVisionClient visionClient, PromptBuilder promptBuilder) {
        this.visionClient = visionClient;
        this.promptBuilder = promptBuilder;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())  // LocalDate 역직렬화 위해
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 영수증 이미지를 분석하여 구조화된 결과를 반환한다.
     *
     * @param imageBytes      영수증 이미지 바이트 (JPEG/PNG/WebP/GIF)
     * @param mediaType       이미지 MIME 타입 (예: "image/jpeg")
     * @param historyContext  가구별 가맹점 학습 이력 (없으면
     *                        {@link MerchantHistoryContext#empty(Long)} 전달)
     * @return Claude가 분석한 거래 정보 + 신뢰도
     * @throws AnalysisException 이미지 처리/API 호출/JSON 파싱 중 어느 단계든 실패 시
     */
    public ReceiptAnalysisResult analyze(
            byte[] imageBytes,
            String mediaType,
            MerchantHistoryContext historyContext) {

        if (imageBytes == null || imageBytes.length == 0) {
            throw new AnalysisException("Empty image bytes");
        }

        // 1) 이미지 base64 인코딩
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        // 2) 프롬프트 조립 (가구 학습 이력 주입)
        String prompt = promptBuilder.build(historyContext);

        // 3) Claude Vision API 호출
        String responseText;
        try {
            responseText = visionClient.analyzeImage(base64Image, mediaType, prompt);
        } catch (ClaudeVisionClient.ClaudeApiException e) {
            throw new AnalysisException("Claude API call failed", e);
        }

        // 4) JSON 추출 (코드 펜스 방어)
        String json = extractJson(responseText);

        // 5) 역직렬화
        ReceiptAnalysisResult result;
        try {
            result = objectMapper.readValue(json, ReceiptAnalysisResult.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Claude response as JSON. Raw response: {}", responseText);
            throw new AnalysisException(
                    "Claude response is not valid JSON. Raw: " + truncate(responseText, 500), e);
        }

        log.info("Receipt analyzed: merchant='{}', category='{}', total={}, confidence={}",
                result.merchant(), result.category(), result.total(), result.confidence());

        return result;
    }

    /**
     * Claude 응답에서 JSON 본문만 추출.
     * <p>프롬프트에서 코드 펜스 금지를 명시했지만, 모델이 가끔 ```json ... ``` 으로
     * 감싸거나 앞뒤에 설명 텍스트를 붙이는 경우를 방어적으로 처리한다.
     */
    private String extractJson(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            throw new AnalysisException("Empty Claude response");
        }

        String trimmed = responseText.trim();

        // 케이스 A: 이미 순수 JSON
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }

        // 케이스 B: 코드 펜스로 감싸짐
        Matcher matcher = CODE_FENCE_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 케이스 C: 첫 { 부터 마지막 } 까지 추출 시도
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }

        throw new AnalysisException("No JSON object found in Claude response: " + truncate(trimmed, 200));
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...(truncated)";
    }

    /**
     * 영수증 분석 실패를 통합 표현하는 예외. 컨트롤러에서 4xx 또는 5xx로 변환.
     */
    public static class AnalysisException extends RuntimeException {
        public AnalysisException(String message) {
            super(message);
        }

        public AnalysisException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
