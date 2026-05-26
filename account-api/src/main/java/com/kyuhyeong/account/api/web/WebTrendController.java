package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.api.summary.MonthlySummaryDtos.MonthlySummaryResponse;
import com.kyuhyeong.account.api.summary.MonthlySummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 수입·지출·잉여 추이 차트 (Chart.js). {@link MonthlySummaryService#series} 를 재사용해
 * 최근 6개월 합계를 라인 차트로 그린다. 차트 데이터는 th:inline javascript 로 주입한다.
 */
@Controller
@RequiredArgsConstructor
public class WebTrendController {

    private static final int MONTHS = 6;

    private final MonthlySummaryService monthlySummaryService;

    @GetMapping("/web/trend")
    public String trend(Model model) {
        YearMonth to = YearMonth.now().plusMonths(1);   // 반-개구간 (exclusive)
        YearMonth from = to.minusMonths(MONTHS);
        List<MonthlySummaryResponse> series = monthlySummaryService.series(from, to);

        List<String> labels = new ArrayList<>();
        List<BigDecimal> incomes = new ArrayList<>();
        List<BigDecimal> expenses = new ArrayList<>();
        List<BigDecimal> surpluses = new ArrayList<>();
        for (MonthlySummaryResponse m : series) {
            labels.add(m.yearMonth());
            incomes.add(m.income());
            expenses.add(m.totalExpense());
            surpluses.add(m.surplus());
        }
        model.addAttribute("labels", labels);
        model.addAttribute("incomes", incomes);
        model.addAttribute("expenses", expenses);
        model.addAttribute("surpluses", surpluses);
        return "trend";
    }
}
