import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/dio_provider.dart';

/// 카테고리 예산 수정 — `PUT /api/categories/{id}/budget`.
///
/// 별도 모델 클래스는 두지 않는다 (응답은 budget 외 정보 변경 없음 → 호출 측은 success 만
/// 확인하고 summary provider 를 invalidate 하면 충분).
class BudgetApi {
  BudgetApi(this._dio);
  final Dio _dio;

  Future<void> updateBudget({
    required int categoryId,
    required double budgetMonthly,
  }) async {
    await _dio.put<Map<String, dynamic>>(
      '/api/categories/$categoryId/budget',
      data: {'budgetMonthly': budgetMonthly},
    );
  }
}

final budgetApiProvider = Provider<BudgetApi>((ref) {
  return BudgetApi(ref.watch(dioProvider));
});
