// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility in the flutter_test package. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:nish_app_lock/main.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(MethodChannel('applock/native'), (
          call,
        ) async {
          switch (call.method) {
            case 'hasPin':
              return false;
            case 'getPinHash':
              return null;
            case 'getApps':
              return <Map<String, dynamic>>[];
            case 'getLockedApps':
              return <String>[];
            case 'isAccessibilityEnabled':
              return true;
            case 'canDrawOverlays':
              return true;
            default:
              return null;
          }
        });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(MethodChannel('applock/native'), null);
  });

  testWidgets('app boots into setup flow when no PIN is configured', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const MyApp());
    await tester.pump();

    expect(find.text('Set a 4-digit PIN'), findsOneWidget);
  });

  test('lockMain entrypoint exists for the Android lock overlay', () {
    expect(lockMain, isA<Function>());
  });
}
