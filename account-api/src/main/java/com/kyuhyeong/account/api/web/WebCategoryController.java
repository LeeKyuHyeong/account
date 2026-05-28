package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.core.enums.CategoryType;
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

/**
 * 카테고리 관리 화면 — 가구 멤버 누구나 접근 가능 (OWNER 차등은 docs/account.md §10 v1.5 항목으로
 * 별도 결정). raw-SQL 로 카테고리 시드를 만지던 운영 작업을 UI 로 대체한다.
 *
 * <p>삭제 안전 가드 → {@link CategoryQueryService#delete} 가 거래 사용 카운트 체크.
 * 사용 중이면 {@link IllegalStateException} 을 던지므로 본 컨트롤러가 catch 해 flash 에러로 노출.
 */
@Controller
@RequestMapping("/web/categories")
@RequiredArgsConstructor
public class WebCategoryController {

    private final CategoryQueryService categoryQueryService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", categoryQueryService.findAllSorted());
        return "categories/list";
    }

    @PostMapping
    public String create(@RequestParam String name,
                         @RequestParam CategoryType type,
                         @RequestParam BigDecimal budgetMonthly,
                         @RequestParam int sortOrder,
                         RedirectAttributes ra) {
        categoryQueryService.create(name, type, budgetMonthly, sortOrder);
        ra.addFlashAttribute("message", "카테고리가 추가되었습니다.");
        return "redirect:/web/categories";
    }

    @PostMapping("/{id}")
    public String edit(@PathVariable Long id,
                       @RequestParam String name,
                       @RequestParam CategoryType type,
                       @RequestParam BigDecimal budgetMonthly,
                       @RequestParam int sortOrder,
                       RedirectAttributes ra) {
        categoryQueryService.edit(id, name, type, budgetMonthly, sortOrder);
        ra.addFlashAttribute("message", "카테고리가 수정되었습니다.");
        return "redirect:/web/categories";
    }

    /**
     * 카테고리 삭제 — 사용 중 카테고리는 {@link IllegalStateException} 으로 거부. catch 해서
     * flash error 로 보여주고 같은 페이지로 돌려보낸다 (도메인 정상 흐름의 거부지 시스템 에러가 아님).
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            categoryQueryService.delete(id);
            ra.addFlashAttribute("message", "카테고리가 삭제되었습니다.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/web/categories";
    }
}
