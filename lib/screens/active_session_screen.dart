import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../main.dart';
import '../native_lock.dart';

class ActiveSessionScreen extends StatefulWidget {
  final int remainingMilli;
  const ActiveSessionScreen({super.key, required this.remainingMilli});

  @override
  State<ActiveSessionScreen> createState() => _ActiveSessionScreenState();
}

class _ActiveSessionScreenState extends State<ActiveSessionScreen> with SingleTickerProviderStateMixin {
  late Timer _timer;
  late final ValueNotifier<int> _timeNotifier;
  late AnimationController _pulseController;
  late Animation<double> _pulseAnimation;
  bool _isLocked = true;
  DateTime _lastPressTime = DateTime.now();
  int _pressCount = 0;

  @override
  void initState() {
    super.initState();
    _timeNotifier = ValueNotifier<int>(widget.remainingMilli);
    
    // Initialize pulse animation for the lock icon
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    )..repeat(reverse: true);
    
    _pulseAnimation = Tween<double>(begin: 1.0, end: 1.1).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
    );
    
    _startTimer();
    _preventExit();
  }

  void _preventExit() async {
    // Prevent system back button and home button
    await SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    await SystemChrome.setPreferredOrientations([
      DeviceOrientation.portraitUp,
      DeviceOrientation.portraitDown,
    ]);
  }

  void _handleTap() {
    final now = DateTime.now();
    final diff = now.difference(_lastPressTime);
    
    if (diff.inMilliseconds < 500) {
      _pressCount++;
      if (_pressCount >= 3) {
        // Rapid tapping detected - show warning and reset counter
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Row(
              children: [
                Icon(Icons.warning_amber_rounded, color: Colors.amber[700]),
                const SizedBox(width: 12),
                const Text("⚠️ الضغط المتكرر لن يفتح القفل!", style: TextStyle(fontWeight: FontWeight.bold)),
              ],
            ),
            backgroundColor: const Color(0xFF1A1A2E),
            duration: const Duration(seconds: 2),
          ),
        );
        HapticFeedback.heavyImpact();
        _pressCount = 0;
      }
    } else {
      _pressCount = 1;
    }
    _lastPressTime = now;
    
    // Always provide haptic feedback to simulate lock
    HapticFeedback.lightImpact();
  }

  void _startTimer() {
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_timeNotifier.value <= 0) {
        _timer.cancel();
        setState(() => _isLocked = false);
        context.read<SessionProvider>().checkStatus();
      } else {
        _timeNotifier.value -= 1000;
      }
    });
  }

  @override
  void dispose() {
    _timer.cancel();
    _pulseController.dispose();
    _timeNotifier.dispose();
    super.dispose();
  }

  String _formatTime(int milli) {
    if (milli <= 0) return "00:00:00";
    Duration duration = Duration(milliseconds: milli);
    String hours = duration.inHours.toString().padLeft(2, '0');
    String minutes = (duration.inMinutes % 60).toString().padLeft(2, '0');
    String seconds = (duration.inSeconds % 60).toString().padLeft(2, '0');
    return "$hours:$minutes:$seconds";
  }

  @override
  Widget build(BuildContext context) {
    final emergencyContact = context.select<SessionProvider, String?>((s) => s.emergencyContact);

    return WillPopScope(
      onWillPop: () async {
        // Prevent back button
        HapticFeedback.vibrate();
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text("🔒 لا يمكن الخروج أثناء جلسة التركيز!"),
            backgroundColor: Color(0xFFE50914),
            duration: Duration(seconds: 1),
          ),
        );
        return false;
      },
      child: Scaffold(
        body: GestureDetector(
          onTap: _handleTap,
          onPanUpdate: (_) => _handleTap(),
          child: Container(
            width: double.infinity,
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  const Color(0xFF0F0F1A),
                  const Color(0xFF1A0000).withOpacity(0.3),
                  const Color(0xFF0D0D0D),
                ],
                stops: const [0.0, 0.5, 1.0],
              ),
            ),
            child: SafeArea(
              child: Column(
                children: [
                  const Spacer(flex: 2),
                  
                  // Animated Lock Icon with Pulse Effect
                  ScaleTransition(
                    scale: _pulseAnimation,
                    child: Container(
                      padding: const EdgeInsets.all(30),
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        gradient: RadialGradient(
                          colors: [
                            const Color(0xFFE50914).withOpacity(0.2),
                            const Color(0xFFE50914).withOpacity(0.05),
                            Colors.transparent,
                          ],
                        ),
                      ),
                      child: Icon(
                        Icons.lock_rounded,
                        size: 120,
                        color: const Color(0xFFE50914),
                        shadows: [
                          BoxShadow(
                            color: const Color(0xFFE50914).withOpacity(0.6),
                            blurRadius: 40,
                            spreadRadius: 10,
                          ),
                        ],
                      ),
                    ),
                  ),
                  
                  const SizedBox(height: 50),
                  
                  // Title with Glow Effect
                  ShaderMask(
                    shaderCallback: (bounds) => const LinearGradient(
                      colors: [Color(0xFFE50914), Color(0xFFFF2E63)],
                    ).createShader(bounds),
                    child: const Text(
                      "الوقت المتبقي للحرية",
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 26,
                        fontWeight: FontWeight.bold,
                        letterSpacing: 1.5,
                      ),
                    ),
                  ),
                  
                  const SizedBox(height: 30),
                  
                  // Digital Countdown Timer with Neon Effect
                  ValueListenableBuilder<int>(
                    valueListenable: _timeNotifier,
                    builder: (context, val, _) {
                      return Container(
                        padding: const EdgeInsets.symmetric(horizontal: 40, vertical: 20),
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(20),
                          border: Border.all(
                            color: const Color(0xFFE50914).withOpacity(0.3),
                            width: 2,
                          ),
                          boxShadow: [
                            BoxShadow(
                              color: const Color(0xFFE50914).withOpacity(0.2),
                              blurRadius: 30,
                              spreadRadius: 5,
                            ),
                          ],
                        ),
                        child: Text(
                          _formatTime(val),
                          style: const TextStyle(
                            color: Color(0xFFE50914),
                            fontSize: 72,
                            fontFamily: 'monospace',
                            fontWeight: FontWeight.w900,
                            letterSpacing: 4,
                            shadows: [
                            BoxShadow(
                                color: Color(0xFFE50914),
                                blurRadius: 20,
                                spreadRadius: 2,
                              ),
                            BoxShadow(
                                color: Colors.red,
                                blurRadius: 40,
                                spreadRadius: 5,
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
                  
                  const SizedBox(height: 50),
                  
                  // Warning Message with Animated Border
                  Container(
                    margin: const EdgeInsets.symmetric(horizontal: 30),
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(16),
                      gradient: LinearGradient(
                        colors: [
                          const Color(0xFF1A1A2E).withOpacity(0.8),
                          const Color(0xFF252A34).withOpacity(0.8),
                        ],
                      ),
                      border: Border.all(
                        color: const Color(0xFFE50914).withOpacity(0.3),
                        width: 1,
                      ),
                    ),
                    child: Row(
                      children: [
                        Icon(Icons.security, color: const Color(0xFFE50914), size: 32),
                        const SizedBox(width: 16),
                        Expanded(
                          child: const Text(
                            "القفل الحديدي مفعّل. لا يمكنك الخروج حتى ينتهي العداد. استغل وقتك في العمل أو الدراسة.",
                            style: TextStyle(color: Colors.white70, fontSize: 15, height: 1.6),
                          ),
                        ),
                      ],
                    ),
                  ),
                  
                  const Spacer(flex: 3),
                  
                  // Emergency Contact Button (if set)
                  if (emergencyContact != null) ...[
                    AnimatedContainer(
                      duration: const Duration(milliseconds: 300),
                      margin: const EdgeInsets.symmetric(horizontal: 30, vertical: 15),
                      child: Material(
                        elevation: 0,
                        borderRadius: BorderRadius.circular(16),
                        child: InkWell(
                          onTap: () {
                            HapticFeedback.mediumImpact();
                            NativeLockService.makeEmergencyCall();
                          },
                          borderRadius: BorderRadius.circular(16),
                          child: Container(
                            width: double.infinity,
                            padding: const EdgeInsets.symmetric(vertical: 18),
                            decoration: BoxDecoration(
                              borderRadius: BorderRadius.circular(16),
                              gradient: LinearGradient(
                                colors: [
                                  const Color(0xFFE50914).withOpacity(0.2),
                                  const Color(0xFFE50914).withOpacity(0.1),
                                ],
                              ),
                              border: Border.all(
                                color: const Color(0xFFE50914),
                                width: 2,
                              ),
                            ),
                            child: Row(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                Icon(Icons.emergency_rounded, color: const Color(0xFFE50914), size: 24),
                                const SizedBox(width: 12),
                                Text(
                                  "اتصال طوارئ: $emergencyContact",
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 16,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                  
                  // Status Indicator
                  Padding(
                    padding: const EdgeInsets.only(bottom: 30),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        AnimatedBuilder(
                          animation: _pulseController,
                          builder: (context, child) {
                            return Container(
                              width: 8 * _pulseAnimation.value,
                              height: 8 * _pulseAnimation.value,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: const Color(0xFFE50914),
                                boxShadow: [
                                  BoxShadow(
                                    color: const Color(0xFFE50914).withOpacity(0.6),
                                    blurRadius: 10 * _pulseAnimation.value,
                                    spreadRadius: 2 * _pulseAnimation.value,
                                  ),
                                ],
                              ),
                            );
                          },
                        ),
                        const SizedBox(width: 12),
                        const Text(
                          "جلسة نشطة - لا يمكن الإيقاف",
                          style: TextStyle(color: Colors.white54, fontSize: 14, letterSpacing: 1),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
