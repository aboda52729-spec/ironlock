import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:google_fonts/google_fonts.dart';
import 'screens/permissions_screen.dart';
import 'screens/setup_screen.dart';
import 'screens/active_session_screen.dart';
import 'native_lock.dart';

// Simple State Management for Session
class SessionProvider with ChangeNotifier {
  int _remainingMilli = 0;
  String? _emergencyContact;
  bool _isChecking = true;
  bool _allPermissionsGranted = false;

  int get remainingMilli => _remainingMilli;
  String? get emergencyContact => _emergencyContact;
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
      _emergencyContact = await NativeLockService.getEmergencyContact();
      
      // Always check permissions so the app never gets stuck
      final hasAccessibility = await NativeLockService.checkAccessibilityPermission();
      final hasOverlay = await NativeLockService.checkOverlayPermission();
      final hasDeviceAdmin = await NativeLockService.isDeviceAdminEnabled();
      _allPermissionsGranted = hasAccessibility && hasOverlay && hasDeviceAdmin;
      
      debugPrint("IronLock Permissions: accessibility=$hasAccessibility, overlay=$hasOverlay, admin=$hasDeviceAdmin");
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
  
  // Set preferred orientations
  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);
  
  // Enable edge-to-edge display
  SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  
  // Set system bar style
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.light,
    systemNavigationBarColor: Colors.transparent,
    systemNavigationBarIconBrightness: Brightness.light,
  ));
  
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
        useMaterial3: true,
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF0A0A0F),
        primaryColor: const Color(0xFFFF2E63),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFFFF2E63),
          secondary: Color(0xFF08D9D6),
          tertiary: Color(0xFF252A34),
          surface: Color(0xFF1A1A2E),
          onPrimary: Colors.white,
          onSecondary: Colors.black,
          onSurface: Colors.white,
        ),
        textTheme: GoogleFonts.cairoTextTheme(
          const TextTheme(
            displayLarge: TextStyle(fontSize: 32, fontWeight: FontWeight.bold, color: Colors.white),
            displayMedium: TextStyle(fontSize: 28, fontWeight: FontWeight.bold, color: Colors.white),
            displaySmall: TextStyle(fontSize: 24, fontWeight: FontWeight.bold, color: Colors.white),
            headlineMedium: TextStyle(fontSize: 20, fontWeight: FontWeight.w600, color: Colors.white),
            headlineSmall: TextStyle(fontSize: 18, fontWeight: FontWeight.w600, color: Colors.white),
            titleLarge: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: Colors.white),
            bodyLarge: TextStyle(fontSize: 16, color: Colors.white70),
            bodyMedium: TextStyle(fontSize: 14, color: Colors.white70),
          ),
        ),
        cardTheme: CardTheme(
          elevation: 0,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
          color: const Color(0xFF1A1A2E),
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
            elevation: 0,
            padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 16),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            backgroundColor: const Color(0xFFFF2E63),
            foregroundColor: Colors.white,
            textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
          ),
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: const Color(0xFF1A1A2E),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(16),
            borderSide: BorderSide.none,
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(16),
            borderSide: BorderSide.none,
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(16),
            borderSide: const BorderSide(color: Color(0xFFFF2E63), width: 2),
          ),
          contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
        ),
      ),
      home: Consumer<SessionProvider>(
        builder: (context, session, _) {
          if (session.isChecking) {
            return const Scaffold(
              body: Center(
                child: CircularProgressIndicator(color: Color(0xFFFF2E63)),
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
