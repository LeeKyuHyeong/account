import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../summary/data/summary_api.dart';

/// 홈 화면 상단의 "예산 초과" 경고 banner — currentMonthSummaryProvider 데이터 재활용.
///
/// FIXED / VARIABLE 카테고리 중 budgetMonthly > 0 이면서 total > budget 인 항목을 카운트.
/// 0 이면 banner 자체를 그리지 않는다 (이번 달 깔끔하면 화면도 깔끔). 탭하면 예산 설정 화면.
class OverBudgetBanner extends ConsumerWidget {
  const OverBudgetBanner({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(currentMonthSummaryProvider);
    return async.when(
      loading: () => const SizedBox.shrink(),
      error: (_, _) => const SizedBox.shrink(),
      data: (summary) {
        final over = summary.byCategory
            .where((c) =>
                (c.type == 'FIXED' || c.type == 'VARIABLE') &&
                c.budgetMonthly > 0 &&
                c.total > c.budgetMonthly)
            .toList();
        if (over.isEmpty) return const SizedBox.shrink();

        final theme = Theme.of(context);
        // 가장 많이 초과한 카테고리 1개 이름을 미리보기로 노출 — 사용자가 바로 어디인지 인지.
        over.sort((a, b) =>
            (b.total - b.budgetMonthly).compareTo(a.total - a.budgetMonthly));
        final preview = over.first.name;
        final extra = over.length - 1;
        final label = extra > 0
            ? '예산 초과: $preview 외 $extra개'
            : '예산 초과: $preview';
        return Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
          child: Material(
            color: theme.colorScheme.errorContainer,
            borderRadius: BorderRadius.circular(12),
            child: InkWell(
              borderRadius: BorderRadius.circular(12),
              onTap: () => context.push('/settings/budget'),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                child: Row(
                  children: [
                    Icon(Icons.warning_amber_rounded,
                        color: theme.colorScheme.onErrorContainer),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        label,
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.colorScheme.onErrorContainer,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                    Icon(Icons.chevron_right,
                        color: theme.colorScheme.onErrorContainer),
                  ],
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}
