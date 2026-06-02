package com.kyuhyeong.account.api.onboarding;

import com.kyuhyeong.account.api.security.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 온보딩 SSR — 카카오 로그인 후 가구가 없는 유저를 가구에 합류시킨다.
 *
 * <ul>
 *   <li>{@code GET /web/onboarding} — 가족 만들기 / 초대코드 입력하기 선택</li>
 *   <li>{@code GET·POST /web/onboarding/create} — 가족 만들기 (가구명 입력)</li>
 *   <li>{@code GET·POST /web/onboarding/join} — 초대코드 입력하기</li>
 * </ul>
 *
 * <p>가구 합류 직후 세션 principal 의 activeHouseholdId/role 이 여전히 비어 있으므로,
 * {@link #refreshPrincipal} 로 principal 을 새로 구성해 SecurityContext + 세션에 저장한 뒤
 * /web/home 으로 보낸다.
 */
@Controller
@RequestMapping("/web/onboarding")
@RequiredArgsConstructor
public class WebOnboardingController {

    private static final int MAX_NAME_LENGTH = 100;

    private final HouseholdOnboardingService onboardingService;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    @GetMapping
    public String choose(@AuthenticationPrincipal AccountPrincipal user, Model model) {
        if (user != null && user.getActiveHouseholdId() != null) {
            return "redirect:/web/home";
        }
        model.addAttribute("nickname", user == null ? null : user.getNickname());
        return "onboarding/choose";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("form", Map.of());
        model.addAttribute("errors", Map.of());
        return "onboarding/create";
    }

    @PostMapping("/create")
    public String create(@RequestParam(required = false) String name,
                         @AuthenticationPrincipal AccountPrincipal user,
                         HttpServletRequest request,
                         HttpServletResponse response,
                         Model model) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            model.addAttribute("form", Map.of("name", name == null ? "" : name));
            model.addAttribute("errors", Map.of("name", "가구 이름을 1~" + MAX_NAME_LENGTH + "자로 입력하세요."));
            return "onboarding/create";
        }
        Long householdId = onboardingService.createHousehold(user.getUserId(), trimmed);
        refreshPrincipal(request, response, user, householdId, "OWNER");
        return "redirect:/web/home";
    }

    @GetMapping("/join")
    public String joinForm(Model model) {
        model.addAttribute("form", Map.of());
        model.addAttribute("errors", Map.of());
        return "onboarding/join";
    }

    @PostMapping("/join")
    public String join(@RequestParam(required = false) String code,
                       @AuthenticationPrincipal AccountPrincipal user,
                       HttpServletRequest request,
                       HttpServletResponse response,
                       Model model) {
        try {
            Long householdId = onboardingService.joinByInviteCode(user.getUserId(), code);
            refreshPrincipal(request, response, user, householdId, "MEMBER");
            return "redirect:/web/home";
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("form", Map.of("code", code == null ? "" : code));
            model.addAttribute("errors", Map.of("code", e.getMessage()));
            return "onboarding/join";
        }
    }

    /**
     * 가구 합류 후 세션 principal 갱신 — activeHouseholdId/role 을 채운 새 {@link AccountPrincipal} 로
     * OAuth2AuthenticationToken 을 재구성해 SecurityContext + HttpSession 에 저장한다.
     * (Spring Security 6 의 SecurityContextHolderFilter 는 요청 종료 시 자동 저장하지 않으므로 명시 저장.)
     */
    private void refreshPrincipal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  AccountPrincipal current,
                                  Long householdId,
                                  String role) {
        AccountPrincipal updated = new AccountPrincipal(
                current.getUserId(), householdId, role, current.getNickname(), current.getAttributes());
        OAuth2AuthenticationToken old =
                (OAuth2AuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        OAuth2AuthenticationToken token = new OAuth2AuthenticationToken(
                updated, updated.getAuthorities(), old.getAuthorizedClientRegistrationId());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(token);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
