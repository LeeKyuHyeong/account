import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/dio_provider.dart';
import '../models/transaction.dart';

class TransactionApi {
  TransactionApi(this._dio);
  final Dio _dio;

  Future<PageResponse<TransactionItem>> list({
    DateTime? from,
    DateTime? to,
    int? categoryId,
    String? type,
    int page = 0,
    int size = 30,
  }) async {
    final fromStr = from == null ? null : _formatDate(from);
    final toStr = to == null ? null : _formatDate(to);
    final res = await _dio.get<Map<String, dynamic>>(
      '/api/transactions',
      queryParameters: {
        'from': ?fromStr,
        'to': ?toStr,
        'categoryId': ?categoryId,
        'type': ?type,
        'page': page,
        'size': size,
      },
    );
    return PageResponse.fromJson(res.data!, TransactionItem.fromJson);
  }

  Future<TransactionItem> create({
    required int categoryId,
    required double amount,
    required DateTime occurredAt,
    String? merchant,
    String? paymentMethod,
    String? memo,
  }) async {
    final res = await _dio.post<Map<String, dynamic>>(
      '/api/transactions',
      data: {
        'categoryId': categoryId,
        'amount': amount,
        // 서버는 LocalDateTime 으로 받음 → ISO-8601 (zone 없음) 형식 전송.
        'occurredAt': _formatLocalDateTime(occurredAt),
        if (merchant?.isNotEmpty ?? false) 'merchant': merchant,
        if (paymentMethod?.isNotEmpty ?? false) 'paymentMethod': paymentMethod,
        if (memo?.isNotEmpty ?? false) 'memo': memo,
      },
    );
    return TransactionItem.fromJson(res.data!);
  }

  /// 거래 부분 수정 — 영수증 컨펌 흐름에서 사용 (status, categoryId).
  Future<TransactionItem> update({
    required int id,
    int? categoryId,
    String? status,
  }) async {
    final res = await _dio.patch<Map<String, dynamic>>(
      '/api/transactions/$id',
      data: {
        'categoryId': ?categoryId,
        'status': ?status,
      },
    );
    return TransactionItem.fromJson(res.data!);
  }

  Future<List<Category>> listCategories({String? type}) async {
    final res = await _dio.get<List<dynamic>>(
      '/api/categories',
      queryParameters: {'type': ?type},
    );
    return res.data!
        .cast<Map<String, dynamic>>()
        .map(Category.fromJson)
        .toList();
  }

  static String _formatDate(DateTime d) {
    final y = d.year.toString().padLeft(4, '0');
    final m = d.month.toString().padLeft(2, '0');
    final day = d.day.toString().padLeft(2, '0');
    return '$y-$m-$day';
  }

  static String _formatLocalDateTime(DateTime d) {
    final local = d.isUtc ? d.toLocal() : d;
    String two(int n) => n.toString().padLeft(2, '0');
    return '${local.year.toString().padLeft(4, '0')}-${two(local.month)}-${two(local.day)}'
        'T${two(local.hour)}:${two(local.minute)}:${two(local.second)}';
  }
}

final transactionApiProvider = Provider<TransactionApi>((ref) {
  return TransactionApi(ref.watch(dioProvider));
});
