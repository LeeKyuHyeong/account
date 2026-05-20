import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/dio_provider.dart';
import '../models/receipt_response.dart';

class ReceiptApi {
  ReceiptApi(this._dio);
  final Dio _dio;

  /// 영수증 이미지를 멀티파트로 업로드. 백엔드가 디스크 저장 + Claude 분석 +
  /// Receipt + DRAFT Transaction 생성 후 결과 반환.
  Future<ReceiptUploadResponse> upload({
    required List<int> bytes,
    required String filename,
    required String contentType,
  }) async {
    final form = FormData.fromMap({
      'image': MultipartFile.fromBytes(
        bytes,
        filename: filename,
        contentType: DioMediaType.parse(contentType),
      ),
    });
    final res = await _dio.post<Map<String, dynamic>>(
      '/api/receipts',
      data: form,
    );
    return ReceiptUploadResponse.fromJson(res.data!);
  }
}

final receiptApiProvider = Provider<ReceiptApi>((ref) {
  return ReceiptApi(ref.watch(dioProvider));
});
