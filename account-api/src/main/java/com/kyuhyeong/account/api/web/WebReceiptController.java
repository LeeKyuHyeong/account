package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.ai.model.ReceiptAnalysisResult;
import com.kyuhyeong.account.ai.service.ReceiptAnalysisService;
import com.kyuhyeong.account.api.receipt.ReceiptAccuracyService;
import com.kyuhyeong.account.api.receipt.ReceiptIngestionService;
import com.kyuhyeong.account.api.receipt.ReceiptQuotaExceededException;
import com.kyuhyeong.account.api.security.AccountPrincipal;
import com.kyuhyeong.account.api.transaction.TransactionDtos.TransactionResponse;
import com.kyuhyeong.account.api.transaction.TransactionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 영수증 SSR 화면 — 업로드 → 분석 → 컨펌.
 *
 * <p>분석/저장은 {@link ReceiptIngestionService#ingest} 를 그대로 재사용한다 (기존 REST
 * /api/receipts 와 동일). 컨펌(카테고리 확정 + DRAFT→CONFIRMED)은 별도 엔드포인트를 만들지
 * 않고 {@code POST /web/transactions/{id}} ({@link WebTransactionController#update}) 를 재사용한다 —
 * 컨펌 화면은 영수증 분석 컨텍스트가 붙은 거래 수정 화면이다.
 */
@Controller
@RequestMapping("/web/receipts")
@RequiredArgsConstructor
public class WebReceiptController {

    private static final Logger log = LoggerFactory.getLogger(WebReceiptController.class);
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final int ANALYSIS_HISTORY_ROWS = 30;

    private final ReceiptIngestionService ingestionService;
    private final ReceiptAccuracyService accuracyService;
    private final TransactionService transactionService;
    private final CategoryQueryService categoryQueryService;

    @GetMapping("/new")
    public String uploadForm() {
        return "receipts/new";
    }

    /** AI 분석값 vs 최종 저장값 비교 — 분석 정확도 모니터링 (최근 30건). */
    @GetMapping("/analysis")
    public String analysisHistory(Model model) {
        var rows = accuracyService.listRecent(ANALYSIS_HISTORY_ROWS);
        model.addAttribute("rows", rows);
        model.addAttribute("changedCount",
                rows.stream().filter(ReceiptAccuracyService.Row::hasDiff).count());
        model.addAttribute("summary", accuracyService.summarize(rows));
        return "receipts/analysis";
    }

    @PostMapping
    public String upload(@RequestParam(value = "image", required = false) MultipartFile image,
                         @AuthenticationPrincipal AccountPrincipal user,
                         Model model) throws IOException {
        String validationError = validateImage(image);
        if (validationError != null) {
            model.addAttribute("error", validationError);
            return "receipts/new";
        }

        ReceiptIngestionService.IngestResult ingested;
        try {
            ingested = ingestionService.ingest(image, user.getUserId());
        } catch (ReceiptQuotaExceededException e) {
            model.addAttribute("error", "이번 달 영수증 AI 분석 " + e.getLimit()
                    + "회를 모두 사용했어요. 플랜을 업그레이드하면 더 분석할 수 있어요.");
            model.addAttribute("quotaExceeded", true);
            return "receipts/new";
        } catch (ReceiptAnalysisService.AnalysisException e) {
            log.warn("Receipt analysis failed for userId={}", user.getUserId(), e);
            model.addAttribute("error", "영수증 분석에 실패했습니다. 다시 시도하거나 직접 입력해 주세요.");
            return "receipts/new";
        }

        ReceiptAnalysisResult analysis = ingested.analysis();
        TransactionResponse tx = transactionService.get(ingested.transactionId());
        populateConfirmModel(model, tx, analysis);
        return "receipts/confirm";
    }

    private void populateConfirmModel(Model model, TransactionResponse tx, ReceiptAnalysisResult analysis) {
        model.addAttribute("tx", tx);
        model.addAttribute("analysis", analysis);
        model.addAttribute("confidencePct", Math.round(analysis.confidence() * 100));
        model.addAttribute("autoConfirmable", analysis.isAutoConfirmable());
        model.addAttribute("requiresManual", analysis.requiresManualClassification());
        model.addAttribute("categories", categoryQueryService.findAllSorted());
    }

    private String validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return "영수증 이미지를 선택하세요.";
        }
        if (image.getSize() > MAX_FILE_SIZE_BYTES) {
            return "이미지가 너무 큽니다 (최대 10MB).";
        }
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return "이미지 파일만 업로드할 수 있습니다.";
        }
        return null;
    }
}
