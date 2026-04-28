import 'package:flutter/services.dart';
import 'package:flutter/material.dart';

class NativeLockService {
  static const platform = MethodChannel('ironlock_channel');

  /// Start a new locking session
  static Future<bool> startSession({
    required int durationMillis,
    required bool isFullLockMode,
    required List<String> selectedApps,
  }) async {
    try {
      final bool success = await platform.invokeMethod('startSession', {
        'durationMillis': durationMillis,
        'isFullLockMode': isFullLockMode,
        'selectedApps': selectedApps,
      });
      return success;
    } catch (e) {
      debugPrint("Error starting session: $e");
      return false;
    }
  }

  /// Check if a session is currently active. Returns remaining time in millis.
  static Future<int> isSessionActive() async {
    try {
      final int remaining = await platform.invokeMethod('isSessionActive');
      return remaining;
    } catch (e) {
      debugPrint("Error checking session: $e");
      return 0;
    }
  }

  /// Check if Device Admin permission is enabled
  static Future<bool> isDeviceAdminEnabled() async {
    try {
      final bool result = await platform.invokeMethod('isDeviceAdminEnabled');
      return result;
    } catch (e) {
      debugPrint("Error checking Device Admin: $e");
      return false;
    }
  }

  static Future<void> requestDeviceAdmin() async {
    try {
      await platform.invokeMethod('requestDeviceAdmin');
    } catch (e) {
      debugPrint("Error requesting Device Admin: $e");
    }
  }

  static Future<bool> checkAccessibilityPermission() async {
    try {
      final bool result = await platform.invokeMethod('checkAccessibilityPermission');
      return result;
    } catch (e) {
      return false;
    }
  }

  static Future<void> requestAccessibilityPermission() async {
    try {
      await platform.invokeMethod('requestAccessibilityPermission');
    } catch (e) {/**/}
  }

  static Future<bool> checkOverlayPermission() async {
    try {
      final bool result = await platform.invokeMethod('checkOverlayPermission');
      return result;
    } catch (e) {
      return false;
    }
  }

  static Future<void> requestOverlayPermission() async {
    try {
      await platform.invokeMethod('requestOverlayPermission');
    } catch (e) {/**/}
  }

  static Future<void> lockScreen() async {
    try {
      await platform.invokeMethod('lockScreen');
    } catch (e) {
      debugPrint("Error locking screen: $e");
    }
  }
}
