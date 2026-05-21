import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../summary/data/summary_api.dart';
import '../../summary/models/monthly_summary.dart';
import '../data/budget_api.dart';

/// 예산 설정 화면.
///
/// 데이터 소스: 이번 달 monthlySummary (총사용액 + 현재 예산을 같이 받아옴). 별도 fetch 없음.
/// 지출 카테고리 (FIXED / VARIABLE) 만 표시 — INCOME / INVEST 는 예산 개념과 무관.
/// 수정 시 PUT /api/categories/{id}/budget 후 summary 와 자신을 invalidate.
class BudgetScreen extends ConsumerWidget {
  const BudgetScreen({super.key});

  static final _currency = NumberFormat.currency(
    locale: 'ko_KR',
    symbol: '₩',
    decimalDigits: 0,
  );

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(currentMonthSummaryProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('예산 설정')),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(currentMonthSummaryProvider),
        child: async.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => ListView(
            children: [
              const SizedBox(height: 80),
              Center(child: Text('로드 실패: $e')),
              const SizedBox(height: 16),
              Center(
                child: TextButton(
                  onPressed: () => ref.invalidate(currentMonthSummaryProvider),
                  child: const Text('다시 시도'),
                ),
              ),
            ],
          ),
          data: (summary) {
            final expenseCategories = summary.byCategory
                .where((c) => c.type == 'FIXED' || c.type == 'VARIABLE')
                .toList();
            if (expenseCategories.isEmpty) {
              return ListView(
                children: const [
                  SizedBox(height: 80),
                  Center(child: Text('지출 카테고리가 없습니다.')),
                ],
              );
            }
            return ListView.builder(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
              itemCount: expenseCategories.length,
              itemBuilder: (context, i) => _CategoryRow(
                category: expenseCategories[i],
                currency: _currency,
              ),
            );
          },
        ),
      ),
    );
  }
}

class _CategoryRow extends ConsumerWidget {
  const _CategoryRow({required this.category, required this.currency});
  final CategoryAmount category;
  final NumberFormat currency;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final hasBudget = category.budgetMonthly > 0;
    final ratio = hasBudget ? (category.total / category.budgetMonthly) : 0.0;
    final overBudget = hasBudget && category.total > category.budgetMonthly;
    final barColor = overBudget
        ? theme.colorScheme.error
        : (ratio > 0.8 ? Colors.orange : Colors.green);
    return Card(
      child: InkWell(
        onTap: () => _editBudget(context, ref),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(category.name, style: theme.textTheme.titleMedium),
                        const SizedBox(height: 2),
                        Text(_typeLabel(category.type),
                            style: theme.textTheme.bodySmall),
                      ],
                    ),
                  ),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Text(
                        hasBudget
                            ? '${currency.format(category.total)} / ${currency.format(category.budgetMonthly)}'
                            : currency.format(category.total),
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: overBudget ? theme.colorScheme.error : null,
                          fontWeight: overBudget ? FontWeight.w600 : null,
                        ),
                      ),
                      if (hasBudget)
                        Text(
                          '${(ratio * 100).toStringAsFixed(0)}%',
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: overBudget ? theme.colorScheme.error : null,
                          ),
                        ),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 8),
              if (hasBudget)
                ClipRRect(
                  borderRadius: BorderRadius.circular(4),
                  child: LinearProgressIndicator(
                    value: ratio.clamp(0.0, 1.0),
                    minHeight: 6,
                    backgroundColor: theme.colorScheme.surfaceContainerHighest,
                    valueColor: AlwaysStoppedAnimation<Color>(barColor),
                  ),
                )
              else
                Text('예산 미설정 — 탭하여 설정',
                    style: theme.textTheme.bodySmall
                        ?.copyWith(color: theme.hintColor)),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _editBudget(BuildContext context, WidgetRef ref) async {
    final controller = TextEditingController(
      text: category.budgetMonthly > 0
          ? category.budgetMonthly.toStringAsFixed(0)
          : '',
    );
    final result = await showDialog<double>(
      context: context,
      builder: (ctx) {
        String? error;
        return StatefulBuilder(
          builder: (ctx, setState) => AlertDialog(
            title: Text('${category.name} 예산'),
            content: TextField(
              controller: controller,
              keyboardType: const TextInputType.numberWithOptions(decimal: false),
              autofocus: true,
              decoration: InputDecoration(
                labelText: '월 예산 (원)',
                hintText: '예: 300000 (0 = 미설정)',
                errorText: error,
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(ctx).pop(),
                child: const Text('취소'),
              ),
              FilledButton(
                onPressed: () {
                  final raw = controller.text.replaceAll(',', '').trim();
                  if (raw.isEmpty) {
                    setState(() => error = '금액을 입력해주세요');
                    return;
                  }
                  final value = double.tryParse(raw);
                  if (value == null || value < 0) {
                    setState(() => error = '0 이상의 숫자를 입력해주세요');
                    return;
                  }
                  Navigator.of(ctx).pop(value);
                },
                child: const Text('저장'),
              ),
            ],
          ),
        );
      },
    );
    if (result == null) return;
    try {
      await ref
          .read(budgetApiProvider)
          .updateBudget(categoryId: category.categoryId, budgetMonthly: result);
      ref.invalidate(currentMonthSummaryProvider);
    } on DioException catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('저장 실패: ${e.message ?? e}')),
        );
      }
    }
  }

  static String _typeLabel(String type) => switch (type) {
        'FIXED' => '고정지출',
        'VARIABLE' => '변동지출',
        'INCOME' => '수입',
        'INVEST' => '투자/저축',
        _ => type,
      };
}
