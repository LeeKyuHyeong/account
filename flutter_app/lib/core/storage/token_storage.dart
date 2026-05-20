import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// JWT access + refresh 토큰의 안전 저장소 래퍼.
///
/// flutter_secure_storage 는 Android 의 Keystore / iOS Keychain 을 사용해
/// SharedPreferences 대신 OS 키체인에 토큰을 저장한다. 단말 분실 / 루팅 시에도
/// 평문 노출 위험을 줄인다.
class TokenStorage {
  TokenStorage(this._storage);

  static const _accessKey = 'jwt_access';
  static const _refreshKey = 'jwt_refresh';

  final FlutterSecureStorage _storage;

  Future<void> save({required String access, required String refresh}) async {
    await _storage.write(key: _accessKey, value: access);
    await _storage.write(key: _refreshKey, value: refresh);
  }

  Future<String?> readAccess() => _storage.read(key: _accessKey);
  Future<String?> readRefresh() => _storage.read(key: _refreshKey);

  Future<void> updateAccess(String access) =>
      _storage.write(key: _accessKey, value: access);

  Future<void> clear() async {
    await _storage.delete(key: _accessKey);
    await _storage.delete(key: _refreshKey);
  }
}

final tokenStorageProvider = Provider<TokenStorage>((ref) {
  // flutter_secure_storage 10.x: Android 는 기본적으로 custom cipher 사용 (Jetpack
  // Security 가 deprecate 됨). 별도 옵션 불필요. iOS / 데스크톱도 OS 기본 키체인 사용.
  const storage = FlutterSecureStorage();
  return TokenStorage(storage);
});
