import 'dart:convert';
import 'package:crypto/crypto.dart';
import 'package:flutter/services.dart';

class AppInfo {
  final String name;
  final String package;
  final Uint8List? icon;

  AppInfo(this.name, this.package, this.icon);
}

class Native {
  static const _ch = MethodChannel('applock/native');

  static String hashPin(String pin) =>
      sha256.convert(utf8.encode('applock::$pin')).toString();

  static Future<List<AppInfo>> getApps() async {
    try {
      final raw = await _ch.invokeMethod<List<dynamic>>('getApps') ?? [];
      return raw.map((e) {
        final m = Map<String, dynamic>.from(e as Map);
        return AppInfo(
          m['name'] as String,
          m['package'] as String,
          m['icon'] as Uint8List?,
        );
      }).toList();
    } catch (_) {
      return [];
    }
  }

  static Future<bool> hasPin() async {
    try {
      return await _ch.invokeMethod<bool>('hasPin') ?? false;
    } catch (_) {
      return false;
    }
  }

  static Future<void> savePin(String pin) async {
    try {
      await _ch.invokeMethod('setPinHash', {'hash': hashPin(pin)});
    } catch (_) {}
  }

  static Future<bool> verifyPin(String pin) async {
    try {
      final res = await _ch.invokeMethod<bool>('verifyPin', {'pin': pin});
      if (res != null) return res;
      final stored = await _ch.invokeMethod<String>('getPinHash');
      return stored == hashPin(pin);
    } catch (_) {
      return false;
    }
  }

  static Future<List<String>> getLockedApps() async {
    try {
      return await _ch.invokeListMethod<String>('getLockedApps') ?? [];
    } catch (_) {
      return [];
    }
  }

  static Future<void> setLockedApps(List<String> apps) async {
    try {
      await _ch.invokeMethod('setLockedApps', {'apps': apps});
    } catch (_) {}
  }

  static Future<bool> isAccessibilityEnabled() async {
    try {
      return await _ch.invokeMethod<bool>('isAccessibilityEnabled') ?? false;
    } catch (_) {
      return false;
    }
  }

  static Future<void> openAccessibilitySettings() async {
    try {
      await _ch.invokeMethod('openAccessibilitySettings');
    } catch (_) {}
  }

  static Future<bool> canDrawOverlays() async {
    try {
      return await _ch.invokeMethod<bool>('canDrawOverlays') ?? false;
    } catch (_) {
      return false;
    }
  }

  static Future<void> requestOverlay() async {
    try {
      await _ch.invokeMethod('requestOverlay');
    } catch (_) {}
  }

  // ---- Fingerprint / biometrics ----
  static Future<bool> isBiometricAvailable() async {
    try {
      return await _ch.invokeMethod<bool>('isBiometricAvailable') ?? false;
    } catch (_) {
      return false;
    }
  }

  static Future<bool> isBiometricEnabled() async {
    try {
      return await _ch.invokeMethod<bool>('isBiometricEnabled') ?? false;
    } catch (_) {
      return false;
    }
  }

  static Future<void> setBiometricEnabled(bool enabled) async {
    try {
      await _ch.invokeMethod('setBiometricEnabled', {'enabled': enabled});
    } catch (_) {}
  }

  /// Shows the system fingerprint prompt. Returns true only on success;
  /// cancel / "Use PIN" / lockout / errors all return false.
  static Future<bool> authenticateBiometric({
    String title = 'Unlock',
    String? subtitle,
  }) async {
    try {
      return await _ch.invokeMethod<bool>('authenticateBiometric', {
            'title': title,
            'subtitle': subtitle,
          }) ??
          false;
    } catch (_) {
      return false;
    }
  }

  // Used by the lock screen
  static Future<String> getTargetName() async {
    try {
      return await _ch.invokeMethod<String>('getTargetName') ?? 'This app';
    } catch (_) {
      return 'This app';
    }
  }

  static Future<void> unlocked() async {
    try {
      await _ch.invokeMethod('unlocked');
    } catch (_) {}
  }

  static Future<void> goHome() async {
    try {
      await _ch.invokeMethod('goHome');
    } catch (_) {}
  }
}
