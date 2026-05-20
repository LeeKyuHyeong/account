import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:flutter_app/features/auth/presentation/login_screen.dart';

void main() {
  testWidgets('login screen renders title + field labels', (tester) async {
    await tester.pumpWidget(
      const ProviderScope(
        child: MaterialApp(home: LoginScreen()),
      ),
    );
    // 라벨/타이틀은 auth state 와 무관하게 항상 렌더.
    // 로그인 버튼 자체는 부팅 직후 AsyncLoading 동안 스피너로 표시되므로 검사 제외.
    expect(find.text('가계부 로그인'), findsOneWidget);
    expect(find.text('이메일'), findsOneWidget);
    expect(find.text('비밀번호'), findsOneWidget);
  });
}
