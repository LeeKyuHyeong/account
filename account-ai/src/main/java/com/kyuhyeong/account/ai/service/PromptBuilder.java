package com.kyuhyeong.account.ai.service;

import com.kyuhyeong.account.ai.model.MerchantHistoryContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 영수증 분석 프롬프트를 조립한다.
 *
 * <p>기본 템플릿은 {@code classpath:prompts/receipt-analysis.txt} 에서 로드.
 * 가구별 가맹점 학습 이력을 {@code {{MERCHANT_HISTORY}}} 자리표시자에 주입한다.
 *
 * <p>템플릿은 애플리케이션 시작 시 한 번만 메모리에 로드하여 호출당 I/O 비용을
 * 피한다. 프롬프트 수정은 jar 재배포가 필요하지만, 가계부 앱 규모에서는
 * 운영 빈도가 낮아 트레이드오프 OK.
 */
@Component
public class PromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(PromptBuilder.class);

    private static final String TEMPLATE_PATH = "classpath:prompts/receipt-analysis.txt";
    private static final String MERCHANT_HISTORY_PLACEHOLDER = "{{MERCHANT_HISTORY}}";
    private static final String EMPTY_HISTORY_NOTICE = "(아직 학습된 가맹점 이력이 없습니다.)";

    private final ResourceLoader resourceLoader;
    private String template;

    public PromptBuilder(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    void loadTemplate() throws IOException {
        Resource resource = resourceLoader.getResource(TEMPLATE_PATH);
        this.template = resource.getContentAsString(StandardCharsets.UTF_8);
        log.info("Loaded receipt analysis prompt template: {} chars", template.length());
        if (!template.contains(MERCHANT_HISTORY_PLACEHOLDER)) {
            throw new IllegalStateException(
                    "Prompt template missing placeholder: " + MERCHANT_HISTORY_PLACEHOLDER);
        }
    }

    /**
     * 가구의 가맹점 이력을 프롬프트에 주입하여 최종 프롬프트 텍스트를 반환한다.
     *
     * @param context 가구별 가맹점 학습 이력 (빈 컨텍스트면 "이력 없음"으로 주입)
     * @return Claude에 전달할 최종 프롬프트
     */
    public String build(MerchantHistoryContext context) {
        String historyBlock = renderHistory(context);
        return template.replace(MERCHANT_HISTORY_PLACEHOLDER, historyBlock);
    }

    private String renderHistory(MerchantHistoryContext context) {
        if (context == null || context.entries().isEmpty()) {
            return EMPTY_HISTORY_NOTICE;
        }

        StringBuilder sb = new StringBuilder();
        for (var entry : context.entries()) {
            sb.append("- \"")
                    .append(entry.merchantName())
                    .append("\" → \"")
                    .append(entry.categoryName())
                    .append("\" (")
                    .append(entry.count())
                    .append("회)\n");
        }
        // 끝의 개행 1개만 남기고 trim
        return sb.toString().stripTrailing();
    }
}
