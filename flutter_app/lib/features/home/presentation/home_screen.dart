import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../auth/providers/auth_provider.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(authProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('가계부'),
        actions: [
          IconButton(
            tooltip: '로그아웃',
            icon: const Icon(Icons.logout),
            onPressed: () => ref.read(authProvider.notifier).logout(),
          ),
        ],
      ),
      body: Center(
        child: state.when(
          loading: () => const CircularProgressIndicator(),
          error: (e, _) => Text('오류: $e'),
          data: (auth) {
            if (auth is! Authenticated) {
              return const Text('로그인이 필요합니다');
            }
            final me = auth.me;
            final household = me.households.isNotEmpty
                ? me.households.first
                : null;
            return Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text('안녕하세요, ${me.name} 님',
                    style: Theme.of(context).textTheme.headlineSmall),
                const SizedBox(height: 8),
                Text(me.email),
                if (household != null) ...[
                  const SizedBox(height: 16),
                  Text('가구: ${household.name} (${household.role})'),
                ],
                const SizedBox(height: 32),
                const Text('— 거래 목록 / 입력 화면은 다음 PR —'),
              ],
            );
          },
        ),
      ),
    );
  }
}
