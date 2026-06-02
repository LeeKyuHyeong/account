package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.api.onboarding.InviteCodeService;
import com.kyuhyeong.account.api.security.AccountPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 가구 설정 페이지 (OWNER 전용) — 가구 멤버 목록 + 초대코드 발급.
 *
 * <p>경로 접근 제어(OWNER)는 {@code SecurityConfig} 의 {@code /web/admin/**} → {@code hasRole("OWNER")}
 * 가 담당. 멤버 조회의 가구 격리는 {@link AdminUserService}, 초대코드 발급은 {@link InviteCodeService}.
 */
@Controller
@RequiredArgsConstructor
public class WebAdminController {

    private final AdminUserService adminUserService;
    private final InviteCodeService inviteCodeService;

    @GetMapping("/web/admin")
    public String settings(@AuthenticationPrincipal AccountPrincipal user, Model model) {
        model.addAttribute("members", adminUserService.listMembers(user.getActiveHouseholdId()));
        model.addAttribute("inviteCodes", inviteCodeService.listActive(user.getActiveHouseholdId()));
        return "admin/users";
    }

    @PostMapping("/web/admin/invite")
    public String generateInvite(@AuthenticationPrincipal AccountPrincipal user, RedirectAttributes ra) {
        String code = inviteCodeService.generate(user.getActiveHouseholdId(), user.getUserId()).getCode();
        ra.addFlashAttribute("message", "초대코드가 발급되었습니다: " + code);
        return "redirect:/web/admin";
    }
}
