package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.api.security.CustomUserDetails;
import com.kyuhyeong.account.core.enums.PlanType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 구독 플랜 화면 (OWNER 전용) — 현재 티어 + 이번 달 영수증 AI 사용량 + 티어 변경.
 *
 * <p>경로 접근 제어(OWNER)는 {@code SecurityConfig} 의 {@code /web/plan/**} → {@code hasRole("OWNER")}
 * 가 담당. 가구 단위 구독(Phase 1)이라 변경은 활성 가구 전체에 즉시 반영된다 (결제 비범위).
 */
@Controller
@RequiredArgsConstructor
public class WebPlanController {

    private final PlanService planService;

    @GetMapping("/web/plan")
    public String plan(@AuthenticationPrincipal CustomUserDetails user, Model model) {
        model.addAttribute("plan", planService.view(user.getActiveHouseholdId()));
        return "plan/plan";
    }

    @PostMapping("/web/plan")
    public String changePlan(@RequestParam PlanType plan,
                             @AuthenticationPrincipal CustomUserDetails user,
                             RedirectAttributes ra) {
        planService.changePlan(user.getActiveHouseholdId(), plan);
        ra.addFlashAttribute("message", plan.displayName() + " 플랜으로 변경되었습니다.");
        return "redirect:/web/plan";
    }
}
