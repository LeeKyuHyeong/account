package com.kyuhyeong.account.api.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * "더보기" 화면 — 하단 탭바에 두지 않은 메뉴(추이·예산·구독·가구설정)와 테마/로그아웃을 모은 페이지.
 *
 * <p>구독/가구설정 링크의 OWNER 노출은 템플릿의 {@code sec:authorize="hasRole('OWNER')"} 가 담당한다.
 */
@Controller
public class WebMoreController {

    @GetMapping("/web/more")
    public String more() {
        return "more";
    }
}
