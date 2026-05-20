/// 백엔드 API 베이스 URL.
///
/// 기본값은 Android 에뮬레이터 기준 (`10.0.2.2` 가 호스트 머신의 localhost).
/// 실기기 / 운영에서는 빌드 시 `--dart-define=API_BASE_URL=https://...` 로 오버라이드.
class ApiConfig {
  ApiConfig._();

  static const String baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080',
  );
}
