import 'package:flutter/material.dart';
import 'native.dart';
import 'pin_pad.dart';

// ==========================================
// 1. SETUP PIN SCREEN
// ==========================================
class SetupPinScreen extends StatefulWidget {
  final VoidCallback onDone;
  const SetupPinScreen({super.key, required this.onDone});

  @override
  State<SetupPinScreen> createState() => _SetupPinScreenState();
}

class _SetupPinScreenState extends State<SetupPinScreen> {
  String? _firstPin;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final isConfirming = _firstPin != null;

    return Scaffold(
      body: SafeArea(
        child: PinPad(
          key: ValueKey(isConfirming),
          headerIcon: Container(
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: cs.primaryContainer.withValues(alpha: 0.6),
            ),
            child: Icon(
              isConfirming ? Icons.check_circle_outline : Icons.lock_outline,
              size: 44,
              color: cs.primary,
            ),
          ),
          title: isConfirming ? 'Confirm your PIN' : 'Set a 4-digit PIN',
          subtitle: isConfirming
              ? 'Re-enter your 4-digit PIN to confirm'
              : 'Choose a 4-digit code to protect your apps',
          onCancel: isConfirming
              ? () => setState(() => _firstPin = null)
              : null,
          cancelLabel: 'Back',
          onSubmit: (pin) async {
            if (_firstPin == null) {
              setState(() => _firstPin = pin);
              return null;
            }

            if (pin != _firstPin) {
              setState(() => _firstPin = null);
              return "PINs didn't match. Please try again.";
            }

            await Native.savePin(pin);
            if (!context.mounted) return null;
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                content: Text('PIN successfully configured!'),
                behavior: SnackBarBehavior.floating,
              ),
            );
            widget.onDone();
            return null;
          },
        ),
      ),
    );
  }
}

// ==========================================
// 2. UNLOCK APP SCREEN (To access settings)
// ==========================================
class UnlockAppScreen extends StatefulWidget {
  final VoidCallback onUnlocked;
  const UnlockAppScreen({super.key, required this.onUnlocked});

  @override
  State<UnlockAppScreen> createState() => _UnlockAppScreenState();
}

class _UnlockAppScreenState extends State<UnlockAppScreen> {
  bool _bioReady = false;
  bool _prompting = false;

  @override
  void initState() {
    super.initState();
    _initBiometric();
  }

  Future<void> _initBiometric() async {
    final ready =
        await Native.isBiometricAvailable() && await Native.isBiometricEnabled();
    if (!mounted) return;
    setState(() => _bioReady = ready);
    if (ready) _tryBiometric(); // fingerprint first, PIN pad is the fallback
  }

  Future<void> _tryBiometric() async {
    if (_prompting) return;
    _prompting = true;
    final ok = await Native.authenticateBiometric(
      title: 'Unlock Nish App Lock',
      subtitle: 'Use your fingerprint to access settings',
    );
    _prompting = false;
    if (ok && mounted) widget.onUnlocked();
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: PinPad(
                headerIcon: Container(
                  padding: const EdgeInsets.all(18),
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: cs.primaryContainer.withValues(alpha: 0.6),
                  ),
                  child: Icon(
                    _bioReady ? Icons.fingerprint : Icons.shield_outlined,
                    size: 44,
                    color: cs.primary,
                  ),
                ),
                title: 'Nish App Lock',
                subtitle: _bioReady
                    ? 'Use your fingerprint or enter your PIN'
                    : 'Enter your 4-digit PIN to access settings',
                onSubmit: (pin) async {
                  final valid = await Native.verifyPin(pin);
                  if (valid) {
                    widget.onUnlocked();
                    return null;
                  }
                  return 'Incorrect PIN. Try again.';
                },
              ),
            ),
            if (_bioReady)
              Padding(
                padding: const EdgeInsets.only(bottom: 16),
                child: TextButton.icon(
                  onPressed: _tryBiometric,
                  icon: const Icon(Icons.fingerprint),
                  label: const Text('Use fingerprint'),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

// ==========================================
// 3. HOME SCREEN (App list & management)
// ==========================================
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  List<AppInfo> _apps = [];
  Set<String> _locked = {};
  bool _loading = true;
  bool _accEnabled = false;
  bool _overlayEnabled = false;
  bool _bioAvailable = false;
  bool _bioEnabled = false;
  String _searchQuery = '';
  String _filter = 'all'; // 'all', 'locked', 'unlocked'
  bool _showSearch = false;
  final TextEditingController _searchCtrl = TextEditingController();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
    _checkPerms();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _searchCtrl.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _checkPerms();
    }
  }

  Future<void> _load() async {
    final apps = await Native.getApps();
    final locked = await Native.getLockedApps();
    if (!mounted) return;
    setState(() {
      _apps = apps;
      _locked = locked.toSet();
      _loading = false;
    });
  }

  Future<void> _checkPerms() async {
    final a = await Native.isAccessibilityEnabled();
    final o = await Native.canDrawOverlays();
    final bAvail = await Native.isBiometricAvailable();
    final bOn = await Native.isBiometricEnabled();
    if (mounted) {
      setState(() {
        _accEnabled = a;
        _overlayEnabled = o;
        _bioAvailable = bAvail;
        _bioEnabled = bOn;
      });
    }
  }

  Future<void> _toggleBiometric() async {
    final messenger = ScaffoldMessenger.of(context);

    if (!_bioAvailable) {
      messenger.showSnackBar(
        const SnackBar(
          content: Text(
            'No fingerprint available. Enroll a fingerprint in your device settings first.',
          ),
          behavior: SnackBarBehavior.floating,
        ),
      );
      return;
    }

    if (_bioEnabled) {
      await Native.setBiometricEnabled(false);
      if (mounted) setState(() => _bioEnabled = false);
      return;
    }

    // Enabling requires a successful fingerprint scan.
    final ok = await Native.authenticateBiometric(
      title: 'Enable fingerprint unlock',
      subtitle: 'Confirm with your fingerprint',
    );
    if (!ok) return;
    await Native.setBiometricEnabled(true);
    if (mounted) setState(() => _bioEnabled = true);
  }

  Future<void> _toggleLock(String pkg, bool enable) async {
    setState(() {
      if (enable) {
        _locked.add(pkg);
      } else {
        _locked.remove(pkg);
      }
    });
    await Native.setLockedApps(_locked.toList());
  }

  List<AppInfo> get _filteredApps {
    return _apps.where((app) {
      final matchesSearch = _searchQuery.isEmpty ||
          app.name.toLowerCase().contains(_searchQuery.toLowerCase()) ||
          app.package.toLowerCase().contains(_searchQuery.toLowerCase());
      if (!matchesSearch) return false;

      final isLocked = _locked.contains(app.package);
      if (_filter == 'locked') return isLocked;
      if (_filter == 'unlocked') return !isLocked;
      return true;
    }).toList();
  }

  void _openChangePinDialog() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (_) => const ChangePinSheet(),
    );
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final filtered = _filteredApps;
    final fullyProtected = _accEnabled && _overlayEnabled;

    return Scaffold(
      appBar: AppBar(
        title: _showSearch
            ? TextField(
                controller: _searchCtrl,
                autofocus: true,
                decoration: const InputDecoration(
                  hintText: 'Search installed apps...',
                  border: InputBorder.none,
                ),
                onChanged: (q) => setState(() => _searchQuery = q),
              )
            : const Text(
                'Nish App Lock',
                style: TextStyle(fontWeight: FontWeight.bold),
              ),
        actions: [
          IconButton(
            icon: Icon(_showSearch ? Icons.close : Icons.search),
            onPressed: () {
              setState(() {
                if (_showSearch) {
                  _showSearch = false;
                  _searchQuery = '';
                  _searchCtrl.clear();
                } else {
                  _showSearch = true;
                }
              });
            },
          ),
          PopupMenuButton<String>(
            onSelected: (val) {
              if (val == 'pin') {
                _openChangePinDialog();
              } else if (val == 'bio') {
                _toggleBiometric();
              } else if (val == 'refresh') {
                setState(() => _loading = true);
                _load();
              }
            },
            itemBuilder: (_) => [
              const PopupMenuItem(
                value: 'pin',
                child: Row(
                  children: [
                    Icon(Icons.password, size: 20),
                    SizedBox(width: 12),
                    Text('Change PIN'),
                  ],
                ),
              ),
              CheckedPopupMenuItem<String>(
                value: 'bio',
                checked: _bioAvailable && _bioEnabled,
                child: const Text('Fingerprint unlock'),
              ),
              const PopupMenuItem(
                value: 'refresh',
                child: Row(
                  children: [
                    Icon(Icons.refresh, size: 20),
                    SizedBox(width: 12),
                    Text('Refresh Apps'),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
      body: Column(
        children: [
          // Permission status card
          if (!fullyProtected)
            _buildPermissionBanner(context)
          else
            _buildStatusHeader(context),

          // Filter bar
          _buildFilterChips(context),

          const Divider(height: 1),

          // App list
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : filtered.isEmpty
                    ? Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(
                              Icons.search_off,
                              size: 56,
                              color: cs.outline,
                            ),
                            const SizedBox(height: 12),
                            Text(
                              'No apps found',
                              style: TextStyle(
                                fontSize: 16,
                                color: cs.onSurfaceVariant,
                              ),
                            ),
                          ],
                        ),
                      )
                    : ListView.separated(
                        itemCount: filtered.length,
                        separatorBuilder: (context, index) =>
                            const Divider(height: 1, indent: 72),
                        itemBuilder: (context, i) {
                          final app = filtered[i];
                          final isLocked = _locked.contains(app.package);

                          return ListTile(
                            leading: Container(
                              width: 44,
                              height: 44,
                              decoration: BoxDecoration(
                                borderRadius: BorderRadius.circular(10),
                                color: cs.surfaceContainerHighest,
                              ),
                              clipBehavior: Clip.antiAlias,
                              child: app.icon != null
                                  ? Image.memory(
                                      app.icon!,
                                      gaplessPlayback: true,
                                      fit: BoxFit.cover,
                                    )
                                  : Icon(Icons.android, color: cs.primary),
                            ),
                            title: Text(
                              app.name,
                              style: TextStyle(
                                fontWeight: isLocked
                                    ? FontWeight.bold
                                    : FontWeight.w500,
                              ),
                            ),
                            subtitle: Text(
                              app.package,
                              style: TextStyle(
                                fontSize: 11,
                                color: cs.onSurfaceVariant,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                            trailing: Switch(
                              value: isLocked,
                              activeThumbColor: cs.primary,
                              onChanged: (v) => _toggleLock(app.package, v),
                            ),
                            onTap: () => _toggleLock(app.package, !isLocked),
                          );
                        },
                      ),
          ),
        ],
      ),
    );
  }

  Widget _buildPermissionBanner(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: cs.errorContainer.withValues(alpha: 0.7),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: cs.error.withValues(alpha: 0.3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.warning_amber_rounded, color: cs.error, size: 24),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  'Permissions Required',
                  style: TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 15,
                    color: cs.onErrorContainer,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            'App Lock requires both permissions to detect opened apps and display the PIN overlay:',
            style: TextStyle(fontSize: 12.5, color: cs.onErrorContainer),
          ),
          const SizedBox(height: 12),
          if (!_accEnabled)
            Padding(
              padding: const EdgeInsets.only(bottom: 6),
              child: ElevatedButton.icon(
                style: ElevatedButton.styleFrom(
                  backgroundColor: cs.error,
                  foregroundColor: cs.onError,
                  minimumSize: const Size.fromHeight(36),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
                icon: const Icon(Icons.accessibility_new, size: 18),
                label: const Text('1. Enable Accessibility Service'),
                onPressed: Native.openAccessibilitySettings,
              ),
            ),
          if (!_overlayEnabled)
            ElevatedButton.icon(
              style: ElevatedButton.styleFrom(
                backgroundColor: cs.error,
                foregroundColor: cs.onError,
                minimumSize: const Size.fromHeight(36),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
              icon: const Icon(Icons.layers, size: 18),
              label: const Text('2. Allow Display Over Other Apps'),
              onPressed: Native.requestOverlay,
            ),
        ],
      ),
    );
  }

  Widget _buildStatusHeader(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: cs.primaryContainer.withValues(alpha: 0.3),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: cs.primary.withValues(alpha: 0.2)),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: Colors.green.withValues(alpha: 0.2),
            ),
            child: const Icon(Icons.shield, color: Colors.green, size: 22),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Protection Active',
                  style: TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 14,
                    color: Colors.green,
                  ),
                ),
                Text(
                  '${_locked.length} of ${_apps.length} apps locked',
                  style: TextStyle(
                    fontSize: 12,
                    color: cs.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFilterChips(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Row(
        children: [
          ChoiceChip(
            label: Text('All (${_apps.length})'),
            selected: _filter == 'all',
            onSelected: (_) => setState(() => _filter = 'all'),
          ),
          const SizedBox(width: 8),
          ChoiceChip(
            label: Text('Locked (${_locked.length})'),
            selected: _filter == 'locked',
            onSelected: (_) => setState(() => _filter = 'locked'),
          ),
          const SizedBox(width: 8),
          ChoiceChip(
            label: Text('Unlocked (${_apps.length - _locked.length})'),
            selected: _filter == 'unlocked',
            onSelected: (_) => setState(() => _filter = 'unlocked'),
          ),
        ],
      ),
    );
  }
}

// ==========================================
// 4. CHANGE PIN BOTTOM SHEET
// ==========================================
class ChangePinSheet extends StatefulWidget {
  const ChangePinSheet({super.key});

  @override
  State<ChangePinSheet> createState() => _ChangePinSheetState();
}

class _ChangePinSheetState extends State<ChangePinSheet> {
  int _step = 0; // 0 = verify old, 1 = enter new, 2 = confirm new
  String? _newPin;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: MediaQuery.of(context).size.height * 0.85,
      child: SafeArea(
        child: PinPad(
          key: ValueKey(_step),
          title: _step == 0
              ? 'Enter Current PIN'
              : (_step == 1 ? 'Enter New 4-Digit PIN' : 'Confirm New PIN'),
          subtitle: _step == 0
              ? 'Verify your identity before changing PIN'
              : (_step == 1
                  ? 'Choose your new 4-digit code'
                  : 'Re-enter your new PIN to confirm'),
          onCancel: () => Navigator.pop(context),
          cancelLabel: 'Cancel',
          onSubmit: (pin) async {
            if (_step == 0) {
              final ok = await Native.verifyPin(pin);
              if (ok) {
                setState(() => _step = 1);
                return null;
              }
              return 'Current PIN is incorrect.';
            } else if (_step == 1) {
              setState(() {
                _newPin = pin;
                _step = 2;
              });
              return null;
            } else {
              if (pin != _newPin) {
                setState(() => _step = 1);
                return "PINs didn't match. Start new PIN again.";
              }
              await Native.savePin(pin);
              if (!context.mounted) return null;
              Navigator.pop(context);
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: Text('PIN successfully changed!'),
                  behavior: SnackBarBehavior.floating,
                ),
              );
              return null;
            }
          },
        ),
      ),
    );
  }
}

// ==========================================
// 5. LOCK OVERLAY SCREEN (Used by LockActivity)
// ==========================================
class LockScreen extends StatefulWidget {
  const LockScreen({super.key});

  @override
  State<LockScreen> createState() => _LockScreenState();
}

class _LockScreenState extends State<LockScreen> {
  String _name = '';
  bool _bioReady = false;
  bool _prompting = false;

  @override
  void initState() {
    super.initState();
    Native.getTargetName().then((n) {
      if (mounted) setState(() => _name = n);
    });
    _initBiometric();
  }

  Future<void> _initBiometric() async {
    final ready =
        await Native.isBiometricAvailable() && await Native.isBiometricEnabled();
    if (!mounted) return;
    setState(() => _bioReady = ready);
    if (ready) _tryBiometric(); // fingerprint first, PIN pad is the fallback
  }

  Future<void> _tryBiometric() async {
    if (_prompting) return;
    _prompting = true;
    final ok = await Native.authenticateBiometric(
      title: _name.isNotEmpty ? 'Unlock $_name' : 'Unlock app',
      subtitle: 'Use your fingerprint to continue',
    );
    _prompting = false;
    if (ok) await Native.unlocked();
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) {
          Native.goHome();
        }
      },
      child: Scaffold(
        body: SafeArea(
          child: Column(
            children: [
              Expanded(
                child: PinPad(
                  headerIcon: const Icon(
                    Icons.lock_rounded,
                    size: 52,
                    color: Colors.indigoAccent,
                  ),
                  title: _name.isNotEmpty ? _name : 'App Locked',
                  subtitle: _bioReady
                      ? 'Use fingerprint or enter PIN to continue'
                      : 'Enter 4-digit PIN to continue',
                  onCancel: Native.goHome,
                  cancelLabel: 'Exit',
                  onSubmit: (pin) async {
                    final valid = await Native.verifyPin(pin);
                    if (valid) {
                      await Native.unlocked();
                      return null;
                    }
                    return 'Wrong PIN';
                  },
                ),
              ),
              if (_bioReady)
                Padding(
                  padding: const EdgeInsets.only(bottom: 16),
                  child: TextButton.icon(
                    onPressed: _tryBiometric,
                    icon: const Icon(Icons.fingerprint),
                    label: const Text('Use fingerprint'),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
