import 'package:flutter/material.dart';
import 'screens.dart';
import 'native.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const MyApp());
}

@pragma('vm:entry-point')
void lockMain() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const LockApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    const seedColor = Color(0xFF6366F1); // Indigo

    return MaterialApp(
      title: 'Nish App Lock',
      debugShowCheckedModeBanner: false,
      themeMode: ThemeMode.system,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: seedColor,
          brightness: Brightness.light,
        ),
        appBarTheme: const AppBarTheme(
          centerTitle: false,
          elevation: 0,
        ),
      ),
      darkTheme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: seedColor,
          brightness: Brightness.dark,
        ),
        scaffoldBackgroundColor: const Color(0xFF0F172A), // Slate 900
        appBarTheme: const AppBarTheme(
          centerTitle: false,
          elevation: 0,
          backgroundColor: Color(0xFF0F172A),
        ),
      ),
      home: const Gate(),
    );
  }
}

class Gate extends StatefulWidget {
  const Gate({super.key});

  @override
  State<Gate> createState() => _GateState();
}

class _GateState extends State<Gate> {
  bool? _hasPin;
  bool _authenticated = false;

  @override
  void initState() {
    super.initState();
    _checkPin();
  }

  Future<void> _checkPin() async {
    final hasPin = await Native.hasPin();
    if (!mounted) return;
    setState(() => _hasPin = hasPin);
  }

  @override
  Widget build(BuildContext context) {
    if (_hasPin == null) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      );
    }

    if (!_hasPin!) {
      // First launch: Setup PIN
      return SetupPinScreen(
        onDone: () => setState(() {
          _hasPin = true;
          _authenticated = true;
        }),
      );
    }

    if (!_authenticated) {
      // Prompt user to enter PIN to access Nish App Lock settings
      return UnlockAppScreen(
        onUnlocked: () => setState(() => _authenticated = true),
      );
    }

    return const HomeScreen();
  }
}

class LockApp extends StatelessWidget {
  const LockApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark(useMaterial3: true).copyWith(
        scaffoldBackgroundColor: const Color(0xFF0F172A),
      ),
      home: const LockScreen(),
    );
  }
}
