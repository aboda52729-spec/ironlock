import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:ironlock/main.dart';

void main() {
  testWidgets('IronLock App smoke test', (WidgetTester tester) async {
    // Build our app and trigger a frame.
    await tester.pumpWidget(
      ChangeNotifierProvider(
        create: (_) => SessionProvider(),
        child: const IronLockApp(),
      ),
    );

    // Basic check that app builds
    expect(find.byType(IronLockApp), findsOneWidget);
  });
}
