import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/dio_provider.dart';
import '../models/tokens.dart';

/// 인증 엔드포인트 클라이언트.
///
/// login / refresh 는 인터셉터 없는 [unauthenticatedDioProvider] 의 dio 를 사용한다.
/// `me` 는 access 토큰이 필요하므로 메인 dio 를 받는다.
class AuthApi {
  AuthApi({required Dio authDio, required Dio mainDio})
    : _authDio = authDio,
      _mainDio = mainDio;

  final Dio _authDio;
  final Dio _mainDio;

  Future<Tokens> login({required String email, required String password}) async {
    final res = await _authDio.post<Map<String, dynamic>>(
      '/api/auth/login',
      data: {'email': email, 'password': password},
    );
    return Tokens.fromJson(res.data!);
  }

  Future<Me> me() async {
    final res = await _mainDio.get<Map<String, dynamic>>('/api/auth/me');
    return Me.fromJson(res.data!);
  }
}

final authApiProvider = Provider<AuthApi>((ref) {
  return AuthApi(
    authDio: ref.watch(unauthenticatedDioProvider),
    mainDio: ref.watch(dioProvider),
  );
});
