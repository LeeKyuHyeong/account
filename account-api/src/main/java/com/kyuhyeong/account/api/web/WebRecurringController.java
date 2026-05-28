package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.api.recurring.RecurringTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 반복 거래 룰 관리 화면 + "지금 실행" 버튼. 가구 멤버 누구나 접근.
 *
 * <p>매일 KST 05:00 에 {@code RecurringTransactionScheduler} 가 발화하지만, 사용자가 즉시 결과를
 * 보고 싶을 때 (테스트·검증·이번 달 누락 catch-up) "지금 실행" 으로 트리거 가능. 멱등성 보장.
 */
@Controller
@RequestMapping("/web/recurring")
@RequiredArgsConstructor
public class WebRecurringController {

    private final RecurringTransactionService recurringService;
    private final CategoryQueryService categoryQueryService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("rules", recurringService.findAllInCurrentHousehold());
        model.addAttribute("categories", categoryQueryService.findAllSorted());
        return "recurring/list";
    }

    @PostMapping
    public String create(@RequestParam Long categoryId,
                         @RequestParam BigDecimal amount,
                         @RequestParam(required = false) String merchant,
                         @RequestParam(required = false) String paymentMethod,
                         @RequestParam(required = false) String memo,
                         @RequestParam int dayOfMonth,
                         @RequestParam(required = false, defaultValue = "false") boolean active,
                         RedirectAttributes ra) {
        recurringService.create(categoryId, amount, emptyToNull(merchant),
                emptyToNull(paymentMethod), emptyToNull(memo), dayOfMonth, active, LocalDate.now());
        ra.addFlashAttribute("message", "반복 거래가 추가되었습니다.");
        return "redirect:/web/recurring";
    }

    @PostMapping("/{id}")
    public String edit(@PathVariable Long id,
                       @RequestParam Long categoryId,
                       @RequestParam BigDecimal amount,
                       @RequestParam(required = false) String merchant,
                       @RequestParam(required = false) String paymentMethod,
                       @RequestParam(required = false) String memo,
                       @RequestParam int dayOfMonth,
                       @RequestParam(required = false, defaultValue = "false") boolean active,
                       RedirectAttributes ra) {
        recurringService.edit(id, categoryId, amount, emptyToNull(merchant),
                emptyToNull(paymentMethod), emptyToNull(memo), dayOfMonth, active);
        ra.addFlashAttribute("message", "반복 거래가 수정되었습니다.");
        return "redirect:/web/recurring";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        recurringService.delete(id);
        ra.addFlashAttribute("message", "반복 거래가 삭제되었습니다.");
        return "redirect:/web/recurring";
    }

    /** "지금 실행" — 현재 가구의 due 룰을 즉시 발화. 멱등 (이미 그 달 발화한 룰은 skip). */
    @PostMapping("/run-now")
    public String runNow(RedirectAttributes ra) {
        int fired = recurringService.runDueForCurrentHousehold(LocalDate.now());
        ra.addFlashAttribute("message",
                fired > 0
                        ? fired + "건의 반복 거래가 자동 생성되었습니다."
                        : "지금 발화할 반복 거래가 없습니다.");
        return "redirect:/web/recurring";
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
