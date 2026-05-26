package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.api.networth.NetWorthDtos.CreateRequest;
import com.kyuhyeong.account.api.networth.NetWorthDtos.HistoryPoint;
import com.kyuhyeong.account.api.networth.NetWorthDtos.SnapshotResponse;
import com.kyuhyeong.account.api.networth.NetWorthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 순자산 — 한 달 스냅샷(자산/부채 목록 + 합계) + 12개월 추이 차트 + 자산/부채 추가·삭제.
 *
 * <p>편집은 월별 스냅샷 모델상 삭제 후 재추가로 갈음 (M3 범위 — TODO 참조). 모든 CRUD 는
 * {@link NetWorthService} 재사용이며 가구 격리는 Hibernate filter 가 자동 적용한다.
 */
@Controller
@RequestMapping("/web/networth")
@RequiredArgsConstructor
public class WebNetWorthController {

    private static final int HISTORY_MONTHS = 12;

    private final NetWorthService netWorthService;

    @GetMapping
    public String networth(@RequestParam(required = false) String ym, Model model) {
        YearMonth month = safeYearMonth(ym);
        SnapshotResponse snapshot = netWorthService.snapshot(month);

        YearMonth to = month.plusMonths(1);
        List<HistoryPoint> history = netWorthService.history(to.minusMonths(HISTORY_MONTHS), to);
        List<String> labels = new ArrayList<>();
        List<BigDecimal> assetsSeries = new ArrayList<>();
        List<BigDecimal> liabilitiesSeries = new ArrayList<>();
        List<BigDecimal> netWorthSeries = new ArrayList<>();
        for (HistoryPoint h : history) {
            labels.add(h.yearMonth());
            assetsSeries.add(h.assetsTotal());
            liabilitiesSeries.add(h.liabilitiesTotal());
            netWorthSeries.add(h.netWorth());
        }

        model.addAttribute("month", month.toString());
        model.addAttribute("snapshot", snapshot);
        model.addAttribute("labels", labels);
        model.addAttribute("assetsSeries", assetsSeries);
        model.addAttribute("liabilitiesSeries", liabilitiesSeries);
        model.addAttribute("netWorthSeries", netWorthSeries);
        return "networth";
    }

    @PostMapping("/assets")
    public String createAsset(@RequestParam String name, @RequestParam String type,
                              @RequestParam BigDecimal balance, @RequestParam String yearMonth) {
        netWorthService.createAsset(new CreateRequest(name, type, balance, yearMonth));
        return "redirect:/web/networth?ym=" + yearMonth;
    }

    @PostMapping("/assets/{id}/delete")
    public String deleteAsset(@PathVariable Long id, @RequestParam String ym) {
        netWorthService.deleteAsset(id);
        return "redirect:/web/networth?ym=" + ym;
    }

    @PostMapping("/liabilities")
    public String createLiability(@RequestParam String name, @RequestParam String type,
                                  @RequestParam BigDecimal balance, @RequestParam String yearMonth) {
        netWorthService.createLiability(new CreateRequest(name, type, balance, yearMonth));
        return "redirect:/web/networth?ym=" + yearMonth;
    }

    @PostMapping("/liabilities/{id}/delete")
    public String deleteLiability(@PathVariable Long id, @RequestParam String ym) {
        netWorthService.deleteLiability(id);
        return "redirect:/web/networth?ym=" + ym;
    }

    private static YearMonth safeYearMonth(String ym) {
        if (ym == null || ym.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(ym);
        } catch (RuntimeException e) {
            return YearMonth.now();
        }
    }
}
