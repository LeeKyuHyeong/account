import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../transaction/data/transaction_api.dart';
import '../../transaction/models/transaction.dart' as tx;
import '../../transaction/providers/transaction_providers.dart';
import '../models/receipt_response.dart';

/// 영수증 분석 결과 컨펌 화면.
///
/// 신뢰도 분기 (docs/account.md §3.2):
/// <ul>
///   <li>≥ 0.8 — 자동 확정 가능. "이대로 확정" 단일 버튼.</li>
///   <li>0.5 ~ 0.8 — 카테고리 확인 요청. 사용자 변경 가능 + 확정 버튼.</li>
///   <li>< 0.5 — 카테고리 수동 선택 강조. 변경하지 않으면 확정 불가.</li>
/// </ul>
class ReceiptConfirmationScreen extends ConsumerStatefulWidget {
  const ReceiptConfirmationScreen({required this.upload, super.key});

  final ReceiptUploadResponse upload;

  @override
  ConsumerState<ReceiptConfirmationScreen> createState() =>
      _ReceiptConfirmationScreenState();
}

class _ReceiptConfirmationScreenState
    extends ConsumerState<ReceiptConfirmationScreen> {
  static final _currency = NumberFormat.currency(
    locale: 'ko_KR',
    symbol: '₩',
    decimalDigits: 0,
  );
  static final _dateFmt = DateFormat('yyyy. M. d.', 'ko_KR');

  int? _selectedCategoryId;
  int? _originalCategoryId;
  bool _submitting = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    // 분석 결과의 카테고리명을 받았지만, 백엔드가 이미 매칭한 categoryId 가 더 신뢰할
    // 만하다 (가구 fallback 적용 후 값). 카테고리 목록 로드 후 거래의 현재 categoryId
    // 로 초기화 — 그 전엔 null 로 두고 dropdown 미선택 상태.
  }

  Future<void> _confirm() async {
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      final api = ref.read(transactionApiProvider);
      final categoryChanged = _selectedCategoryId != null &&
          _selectedCategoryId != _originalCategoryId;
      await api.update(
        id: widget.upload.transactionId,
        categoryId: categoryChanged ? _selectedCategoryId : null,
        status: 'CONFIRMED',
      );
      // 거래 목록 새로고침
      await ref.read(transactionListProvider.notifier).refresh();
      if (!mounted) return;
      // 컨펌 후 거래 목록으로 이동 (홈 위에 push).
      context.go('/transactions');
    } on DioException catch (e) {
      setState(() => _error = _formatError(e));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  String _formatError(DioException e) {
    final data = e.response?.data;
    if (data is Map<String, dynamic>) {
      final message = data['message'] as String?;
      if (message != null) return message;
    }
    return e.message ?? '저장에 실패했습니다';
  }

  /// 신뢰도 ≥ 0.5 (자동 또는 컨펌 요청) → 카테고리 변경은 선택. < 0.5 (수동) → 강제 변경.
  bool _canSubmit() {
    if (widget.upload.requiresManualClassification) {
      return _selectedCategoryId != null &&
          _selectedCategoryId != _originalCategoryId;
    }
    return true;
  }

  @override
  Widget build(BuildContext context) {
    final analysis = widget.upload.analysis;
    final theme = Theme.of(context);
    final categoriesAsync = ref.watch(expenseCategoriesProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('영수증 확인')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _ConfidenceBanner(upload: widget.upload),
              const SizedBox(height: 24),
              _ResultCard(
                rows: [
                  _Row(label: '가맹점', value: analysis.merchant),
                  _Row(
                    label: '카테고리(AI)',
                    value: analysis.subcategory != null && analysis.subcategory!.isNotEmpty
                        ? '${analysis.category} · ${analysis.subcategory}'
                        : analysis.category,
                  ),
                  _Row(label: '금액', value: _currency.format(analysis.total)),
                  if (analysis.date != null)
                    _Row(label: '일시', value: _dateFmt.format(analysis.date!)),
                  if (analysis.paymentMethod != null && analysis.paymentMethod!.isNotEmpty)
                    _Row(label: '결제수단', value: analysis.paymentMethod!),
                  _Row(
                    label: '신뢰도',
                    value: '${(analysis.confidence * 100).toStringAsFixed(0)}%',
                  ),
                ],
              ),
              const SizedBox(height: 24),
              Text(
                widget.upload.requiresManualClassification
                    ? '신뢰도가 낮습니다 — 카테고리를 직접 선택해주세요'
                    : '필요하면 카테고리를 변경할 수 있습니다',
                style: theme.textTheme.titleSmall,
              ),
              const SizedBox(height: 8),
              categoriesAsync.when(
                loading: () => const LinearProgressIndicator(),
                error: (e, _) => Text('카테고리 로드 실패: $e'),
                data: (categories) {
                  _ensureOriginalCategoryInitialized(categories, analysis.category);
                  return DropdownButtonFormField<int>(
                    initialValue: _selectedCategoryId,
                    items: categories
                        .map((c) => DropdownMenuItem<int>(
                              value: c.id,
                              child: Text('${c.name} · ${c.type}'),
                            ))
                        .toList(),
                    onChanged: _submitting
                        ? null
                        : (value) => setState(() => _selectedCategoryId = value),
                    decoration: const InputDecoration(labelText: '카테고리'),
                  );
                },
              ),
              if (_error != null) ...[
                const SizedBox(height: 16),
                Text(_error!, style: TextStyle(color: theme.colorScheme.error)),
              ],
              const SizedBox(height: 32),
              FilledButton(
                onPressed: _submitting || !_canSubmit() ? null : _confirm,
                child: _submitting
                    ? const SizedBox(
                        height: 20,
                        width: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : Text(widget.upload.autoConfirmable ? '이대로 확정' : '확인 후 확정'),
              ),
              const SizedBox(height: 8),
              TextButton(
                onPressed: _submitting ? null : () => context.go('/transactions'),
                child: const Text('나중에 확인 (DRAFT 로 두기)'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// 카테고리 목록 로드되면 분석 결과의 카테고리명과 일치하는 첫 항목으로 초기 선택.
  /// 백엔드가 같은 매칭 규칙으로 categoryId 를 정했으므로 이름 비교만으로 충분.
  void _ensureOriginalCategoryInitialized(
    List<tx.Category> categories,
    String analysisCategoryName,
  ) {
    if (_originalCategoryId != null) return;
    final match = categories.firstWhere(
      (c) => c.name == analysisCategoryName,
      orElse: () => categories.isNotEmpty
          ? categories.first
          : const tx.Category(id: 0, name: '', type: '', budgetMonthly: 0),
    );
    if (match.id == 0) return;
    _originalCategoryId = match.id;
    _selectedCategoryId = match.id;
  }
}

class _ConfidenceBanner extends StatelessWidget {
  const _ConfidenceBanner({required this.upload});
  final ReceiptUploadResponse upload;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final (color, icon, message) = _bannerSpec(theme);
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          Icon(icon, color: color),
          const SizedBox(width: 12),
          Expanded(child: Text(message, style: theme.textTheme.bodyMedium)),
        ],
      ),
    );
  }

  (Color, IconData, String) _bannerSpec(ThemeData theme) {
    if (upload.autoConfirmable) {
      return (
        Colors.green.shade700,
        Icons.check_circle,
        '신뢰도가 높습니다. 그대로 확정해도 좋습니다.'
      );
    }
    if (upload.requiresManualClassification) {
      return (
        theme.colorScheme.error,
        Icons.error_outline,
        '신뢰도가 낮습니다. 카테고리를 직접 선택해주세요.'
      );
    }
    return (
      Colors.amber.shade800,
      Icons.help_outline,
      '카테고리가 맞는지 확인 후 확정해주세요.'
    );
  }
}

class _ResultCard extends StatelessWidget {
  const _ResultCard({required this.rows});
  final List<_Row> rows;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            for (var i = 0; i < rows.length; i++) ...[
              rows[i],
              if (i < rows.length - 1) const Divider(height: 16),
            ],
          ],
        ),
      ),
    );
  }
}

class _Row extends StatelessWidget {
  const _Row({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 96,
          child: Text(label, style: theme.textTheme.labelLarge),
        ),
        Expanded(child: Text(value, style: theme.textTheme.bodyLarge)),
      ],
    );
  }
}
