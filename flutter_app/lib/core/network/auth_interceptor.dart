import 'package:dio/dio.dart';

import '../storage/token_storage.dart';

/// 요청에 Bearer 토큰 자동 부착 + 401 응답 시 refresh 토큰으로 1회 재시도.
///
/// <pre>
///   Request →  Authorization: Bearer {access}
///   Response 401 → refresh 호출 → access 재발급 → 원 요청 1회 재시도
///   재시도도 401 → 토큰 폐기 후 그대로 전파 (호출자 가 unauthenticated 처리)
/// </pre>
///
/// refresh 자체가 실패하면 저장된 토큰을 모두 폐기하고 401 을 전파해 라우터가
/// `/login` 으로 redirect 하도록 한다.
class AuthInterceptor extends QueuedInterceptor {
  AuthInterceptor({
    required this.tokenStorage,
    required this.refreshDioFactory,
    required this.onAuthFailure,
  });

  final TokenStorage tokenStorage;

  /// 인터셉터가 붙지 않은 dio (재귀 호출 방지).
  final Dio Function() refreshDioFactory;

  /// refresh 까지 실패해서 인증 만료를 알릴 때 호출.
  final void Function() onAuthFailure;

  @override
  Future<void> onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    if (options.extra['skipAuth'] == true) {
      handler.next(options);
      return;
    }
    final access = await tokenStorage.readAccess();
    if (access != null && access.isNotEmpty) {
      options.headers['Authorization'] = 'Bearer $access';
    }
    handler.next(options);
  }

  @override
  Future<void> onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    final response = err.response;
    final isRetry = err.requestOptions.extra['authRetry'] == true;
    if (response?.statusCode != 401 || isRetry) {
      handler.next(err);
      return;
    }

    final refresh = await tokenStorage.readRefresh();
    if (refresh == null || refresh.isEmpty) {
      onAuthFailure();
      handler.next(err);
      return;
    }

    try {
      final refreshDio = refreshDioFactory();
      final res = await refreshDio.post<Map<String, dynamic>>(
        '/api/auth/refresh',
        data: {'refreshToken': refresh},
      );
      final newAccess = res.data?['accessToken'] as String?;
      final newRefresh = res.data?['refreshToken'] as String? ?? refresh;
      if (newAccess == null || newAccess.isEmpty) {
        await tokenStorage.clear();
        onAuthFailure();
        handler.next(err);
        return;
      }
      await tokenStorage.save(access: newAccess, refresh: newRefresh);

      final retryOptions = err.requestOptions
        ..headers['Authorization'] = 'Bearer $newAccess'
        ..extra['authRetry'] = true;
      final retryDio = refreshDioFactory();
      final retried = await retryDio.fetch<dynamic>(retryOptions);
      handler.resolve(retried);
    } on DioException {
      await tokenStorage.clear();
      onAuthFailure();
      handler.next(err);
    }
  }
}
