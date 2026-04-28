import 'dart:io';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'screens/permissions_screen.dart';
import 'screens/setup_screen.dart';
import 'screens/active_session_screen.dart';
import 'native_lock.dart';

// Simple State Management for Session
class SessionProvider with ChangeNotifier {
  int _remainingMilli = 0;
  bool _isChecking = true;
  bool _allPermissionsGranted = false;

  int get remainingMilli => _remainingMilli;
  bool get isChecking => _isChecking;
  bool get allPermissionsGranted => _allPermissionsGranted;

  Future<void> checkStatus() async {
    if (!Platform.isAndroid) {
      _isChecking = false;
      notifyListeners();
      return;
    }

    try {
      _remainingMilli = await NativeLockService.isSessionActive();
      if (_remainingMilli <= 0) {
        final hasAccessibility = await NativeLockService.checkAccessibilityPermission();
        final hasOverlay = await NativeLockService.checkOverlayPermission();
        final hasDeviceAdmin = await NativeLockService.isDeviceAdminEnabled();
        _allPermissionsGranted = hasAccessibility && hasOverlay && hasDeviceAdmin;
      }
    } catch (e) {
      debugPrint("Error checking status: $e");
    } finally {
      _isChecking = false;
      notifyListeners();
    }
  }

  void updateRemainingTime(int time) {
    _remainingMilli = time;
    notifyListeners();
  }
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(
    ChangeNotifierProvider(
      create: (_) => SessionProvider()..checkStatus(),
      child: const IronLockApp(),
    ),
  );
}

class IronLockApp extends StatelessWidget {
  const IronLockApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'IronLock - No Escape',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF0D0D0D),
        primaryColor: const Color(0xFFE50914),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFFE50914),
          secondary: Color(0xFF1F1F1F),
        ),
        fontFamily: 'Roboto',
      ),
      home: Consumer<SessionProvider>(
        builder: (context, session, _) {
          if (session.isChecking) {
            return const Scaffold(
              body: Center(
                child: CircularProgressIndicator(color: Color(0xFFE50914)),
              ),
            );
          }

          if (!Platform.isAndroid) {
            return const Scaffold(
              body: Center(
                child: Text("هذا التطبيق مصمم حالياً لنظام أندرويد فقط."),
              ),
            );
          }

          if (session.remainingMilli > 0) {
            return ActiveSessionScreen(remainingMilli: session.remainingMilli);
          } else if (session.allPermissionsGranted) {
            return const SetupScreen();
          } else {
            return const PermissionsScreen();
          }
        },
      ),
    );
  }
}
