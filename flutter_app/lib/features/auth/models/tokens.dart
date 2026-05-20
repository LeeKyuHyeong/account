/// 로그인 / 토큰 갱신 응답 (백엔드 [AuthDtos.LoginResponse] 와 1:1).
class Tokens {
  const Tokens({required this.access, required this.refresh});

  factory Tokens.fromJson(Map<String, dynamic> json) {
    return Tokens(
      access: json['accessToken'] as String,
      refresh: json['refreshToken'] as String,
    );
  }

  final String access;
  final String refresh;
}

/// 현재 로그인 사용자 정보 (`GET /api/auth/me` 응답).
class Me {
  const Me({
    required this.userId,
    required this.email,
    required this.name,
    required this.households,
  });

  factory Me.fromJson(Map<String, dynamic> json) {
    final raw = (json['households'] as List<dynamic>? ?? const [])
        .cast<Map<String, dynamic>>();
    return Me(
      userId: (json['userId'] as num).toInt(),
      email: json['email'] as String,
      name: json['name'] as String,
      households: raw.map(HouseholdSummary.fromJson).toList(),
    );
  }

  final int userId;
  final String email;
  final String name;
  final List<HouseholdSummary> households;
}

class HouseholdSummary {
  const HouseholdSummary({
    required this.id,
    required this.name,
    required this.role,
  });

  factory HouseholdSummary.fromJson(Map<String, dynamic> json) {
    return HouseholdSummary(
      id: (json['id'] as num).toInt(),
      name: json['name'] as String,
      role: json['role'] as String,
    );
  }

  final int id;
  final String name;
  final String role;
}
