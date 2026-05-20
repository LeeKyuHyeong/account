import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../config/api_config.dart';
import '../storage/token_storage.dart';
import 'auth_interceptor.dart';

/// 인증 인터셉터가 부착된 메인 Dio 인스턴스.
///
/// 일반 API 호출은 본 provider 의 dio 를 사용한다. login / refresh 자체는
/// [unauthenticatedDioProvider] 의 dio (인터셉터 없음) 를 사용해 무한 재귀를 피한다.
final dioProvider = Provider<Dio>((ref) {
  final dio = _buildBaseDio();
  final tokenStorage = ref.watch(tokenStorageProvider);
  dio.interceptors.add(
    AuthInterceptor(
      tokenStorage: tokenStorage,
      refreshDioFactory: _buildBaseDio,
      onAuthFailure: () {
        // 라우터가 AuthState 를 watch 하므로 토큰 폐기만 하면 자동 redirect.
        // 별도 액션 없음 — 토큰 폐기는 인터셉터 내부에서 이미 처리.
      },
    ),
  );
  return dio;
});

/// 인증 인터셉터 없는 dio — 로그인 / 토큰 refresh 전용.
final unauthenticatedDioProvider = Provider<Dio>((ref) => _buildBaseDio());

Dio _buildBaseDio() {
  // validateStatus 는 Dio 기본값 (2xx 만 통과) 사용 — 401/4xx 는 DioException 으로
  // 흘러 AuthInterceptor.onError 에서 refresh 분기가 발동한다.
  return Dio(
    BaseOptions(
      baseUrl: ApiConfig.baseUrl,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 30),
      contentType: 'application/json',
    ),
  );
}
