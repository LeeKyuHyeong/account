package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.core.enums.PlanType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;

/**
 * 앱 관리자(시스템 관리) 화면 — 전체 가구 현황 + 가구별 플랜 수동 변경.
 *
 * <p>경로 접근 제어는 {@code SecurityConfig} 의 {@code /web/sysadmin/**} →
 * {@code hasRole("SYSADMIN")} 이 담당 (카카오 providerUserId 화이트리스트 기반 —
 * {@code SysAdminProperties}). 실결제(Phase 2) 유예 동안 플랜 업그레이드의 유일한 경로.
 */
@Controller
@RequiredArgsConstructor
public class WebSysAdminController {

    private final SysAdminService sysAdminService;
    private final PlanService planService;

    @GetMapping("/web/sysadmin")
    public String households(Model model) {
        model.addAttribute("households", sysAdminService.listHouseholds());
        model.addAttribute("planTypes", planOptions());
        return "sysadmin/households";
    }

    @PostMapping("/web/sysadmin/plan")
    public String changePlan(@RequestParam Long householdId,
                             @RequestParam PlanType plan,
                             RedirectAttributes ra) {
        try {
            planService.changePlan(householdId, plan);
            ra.addFlashAttribute("message", "가구 #" + householdId + " 플랜을 "
                    + plan.displayName() + " 으로 변경했습니다.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/web/sysadmin";
    }

    /** 플랜 select 옵션 — enum name + 표시명. */
    private List<PlanOption> planOptions() {
        return Arrays.stream(PlanType.values())
                .map(t -> new PlanOption(t.name(), t.displayName()))
                .toList();
    }

    /** select 옵션 1개. */
    public record PlanOption(String type, String displayName) {}
}
