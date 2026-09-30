import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// 4-digit PIN entry pad with animations and tactile feedback.
class PinPad extends StatefulWidget {
  final String title;
  final String? subtitle;
  final Widget? headerIcon;
  final Future<String?> Function(String pin) onSubmit;
  final VoidCallback? onCancel;
  final String? cancelLabel;

  const PinPad({
    super.key,
    required this.title,
    this.subtitle,
    this.headerIcon,
    required this.onSubmit,
    this.onCancel,
    this.cancelLabel,
  });

  @override
  State<PinPad> createState() => _PinPadState();
}

class _PinPadState extends State<PinPad> with SingleTickerProviderStateMixin {
  String _pin = '';
  String? _error;
  bool _busy = false;
  late AnimationController _shakeController;
  late Animation<double> _shakeAnimation;

  @override
  void initState() {
    super.initState();
    _shakeController = AnimationController(
      duration: const Duration(milliseconds: 400),
      vsync: this,
    );
    _shakeAnimation = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _shakeController, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _shakeController.dispose();
    super.dispose();
  }

  Future<void> _tap(String d) async {
    if (_busy || _pin.length >= 4) return;
    HapticFeedback.lightImpact();
    setState(() {
      _pin += d;
      _error = null;
    });

    if (_pin.length == 4) {
      setState(() => _busy = true);
      final err = await widget.onSubmit(_pin);
      if (!mounted) return;

      if (err != null) {
        HapticFeedback.heavyImpact();
        _shakeController.forward(from: 0.0);
        setState(() {
          _busy = false;
          _error = err;
          _pin = '';
        });
      } else {
        setState(() {
          _busy = false;
          _pin = '';
        });
      }
    }
  }

  void _back() {
    if (_busy || _pin.isEmpty) return;
    HapticFeedback.selectionClick();
    setState(() {
      _pin = _pin.substring(0, _pin.length - 1);
      _error = null;
    });
  }

  Widget _buildKey(
    BuildContext context, {
    String? label,
    IconData? icon,
    VoidCallback? onTap,
    bool isAction = false,
  }) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    final isDark = theme.brightness == Brightness.dark;

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      width: 76,
      height: 76,
      child: Material(
        color: isAction
            ? Colors.transparent
            : (isDark ? const Color(0xFF1E293B) : cs.surfaceContainerHighest),
        shape: const CircleBorder(),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          splashColor: cs.primary.withValues(alpha: 0.2),
          highlightColor: cs.primary.withValues(alpha: 0.1),
          child: Center(
            child: icon != null
                ? Icon(
                    icon,
                    size: 26,
                    color: isAction ? cs.onSurfaceVariant : cs.onSurface,
                  )
                : Text(
                    label ?? '',
                    style: TextStyle(
                      fontSize: isAction ? 14 : 26,
                      fontWeight:
                          isAction ? FontWeight.w500 : FontWeight.w600,
                      color: isAction ? cs.onSurfaceVariant : cs.onSurface,
                    ),
                  ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;

    return Center(
      child: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              if (widget.headerIcon != null) ...[
                widget.headerIcon!,
                const SizedBox(height: 16),
              ],
              Text(
                widget.title,
                textAlign: TextAlign.center,
                style: theme.textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.bold,
                  letterSpacing: -0.5,
                ),
              ),
              if (widget.subtitle != null) ...[
                const SizedBox(height: 8),
                Text(
                  widget.subtitle!,
                  textAlign: TextAlign.center,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: cs.onSurfaceVariant,
                  ),
                ),
              ],
              const SizedBox(height: 28),

              // Animated PIN indicator dots
              AnimatedBuilder(
                animation: _shakeAnimation,
                builder: (context, child) {
                  final offset = sin(_shakeAnimation.value * pi * 4) * 12;
                  return Transform.translate(
                    offset: Offset(offset, 0),
                    child: child,
                  );
                },
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: List.generate(4, (i) {
                    final filled = i < _pin.length;
                    final isErr = _error != null;
                    return AnimatedContainer(
                      duration: const Duration(milliseconds: 180),
                      curve: Curves.easeOut,
                      margin: const EdgeInsets.symmetric(horizontal: 10),
                      width: filled ? 18 : 16,
                      height: filled ? 18 : 16,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: isErr
                            ? cs.error
                            : (filled ? cs.primary : Colors.transparent),
                        border: Border.all(
                          color: isErr
                              ? cs.error
                              : (filled
                                  ? cs.primary
                                  : cs.outlineVariant),
                          width: 2.2,
                        ),
                        boxShadow: filled && !isErr
                            ? [
                                BoxShadow(
                                  color: cs.primary.withValues(alpha: 0.4),
                                  blurRadius: 8,
                                  spreadRadius: 1,
                                )
                              ]
                            : null,
                      ),
                    );
                  }),
                ),
              ),

              SizedBox(
                height: 38,
                child: Center(
                  child: AnimatedOpacity(
                    opacity: _error != null ? 1.0 : 0.0,
                    duration: const Duration(milliseconds: 200),
                    child: Text(
                      _error ?? '',
                      style: TextStyle(
                        color: cs.error,
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ),
              ),

              // 3x4 Keypad
              for (final row in [
                ['1', '2', '3'],
                ['4', '5', '6'],
                ['7', '8', '9']
              ])
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    for (final d in row)
                      _buildKey(context, label: d, onTap: () => _tap(d)),
                  ],
                ),

              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  if (widget.onCancel != null)
                    _buildKey(
                      context,
                      label: widget.cancelLabel ?? 'Cancel',
                      isAction: true,
                      onTap: widget.onCancel,
                    )
                  else
                    const SizedBox(width: 104, height: 76),
                  _buildKey(context, label: '0', onTap: () => _tap('0')),
                  _buildKey(
                    context,
                    icon: Icons.backspace_outlined,
                    isAction: true,
                    onTap: _back,
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
