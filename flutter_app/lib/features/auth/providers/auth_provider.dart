import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/storage/token_storage.dart';
import '../data/auth_api.dart';
import '../models/tokens.dart';

/// 인증 상태 — 라우터 redirect 와 화면 분기에 사용.
sealed class AuthState {
  const AuthState();
}

/// 부팅 직후 secure_storage 에서 토큰 읽는 중.
class AuthInitial extends AuthState {
  const AuthInitial();
}

/// 토큰 없음 → 로그인 화면.
class Unauthenticated extends AuthState {
  const Unauthenticated();
}

/// access 토큰 있음 → 홈.
class Authenticated extends AuthState {
  const Authenticated(this.me);
  final Me me;
}

class AuthNotifier extends AsyncNotifier<AuthState> {
  @override
  Future<AuthState> build() async {
    final storage = ref.watch(tokenStorageProvider);
    final access = await storage.readAccess();
    if (access == null || access.isEmpty) {
      return const Unauthenticated();
    }
    // 토큰이 있어도 만료/위조 가능 — /me 호출로 실효 검증. 401 이면 인터셉터가
    // refresh 시도 → 그래도 실패면 토큰 폐기되고 DioException 이 여기로 던져진다.
    try {
      final me = await ref.read(authApiProvider).me();
      return Authenticated(me);
    } catch (_) {
      await storage.clear();
      return const Unauthenticated();
    }
  }

  Future<void> login({required String email, required String password}) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final api = ref.read(authApiProvider);
      final tokens = await api.login(email: email, password: password);
      await ref.read(tokenStorageProvider).save(
            access: tokens.access,
            refresh: tokens.refresh,
          );
      final me = await api.me();
      return Authenticated(me);
    });
  }

  Future<void> logout() async {
    await ref.read(tokenStorageProvider).clear();
    state = const AsyncData(Unauthenticated());
  }
}

final authProvider = AsyncNotifierProvider<AuthNotifier, AuthState>(
  AuthNotifier.new,
);
