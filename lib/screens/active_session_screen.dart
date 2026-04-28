import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../main.dart';
import '../native_lock.dart';

class ActiveSessionScreen extends StatefulWidget {
  final int remainingMilli;
  const ActiveSessionScreen({super.key, required this.remainingMilli});

  @override
  State<ActiveSessionScreen> createState() => _ActiveSessionScreenState();
}

class _ActiveSessionScreenState extends State<ActiveSessionScreen> {
  late Timer _timer;
  late final ValueNotifier<int> _timeNotifier;

  @override
  void initState() {
    super.initState();
    _timeNotifier = ValueNotifier<int>(widget.remainingMilli);
    _startTimer();
  }

  void _startTimer() {
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_timeNotifier.value <= 0) {
        _timer.cancel();
        context.read<SessionProvider>().checkStatus();
      } else {
        _timeNotifier.value -= 1000;
      }
    });
  }

  @override
  void dispose() {
    _timer.cancel();
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

    return Scaffold(
      body: Container(
        width: double.infinity,
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Color(0xFF1A0000), Color(0xFF0D0D0D)],
          ),
        ),
        child: Column(
          children: [
            const Spacer(),
            const Icon(Icons.lock_outline_rounded, size: 100, color: Color(0xFFE50914)),
            const SizedBox(height: 40),
            const Text(
              "الوقت المتبقي للحرية",
              style: TextStyle(color: Colors.white, fontSize: 24, fontWeight: FontWeight.bold, letterSpacing: 1.2),
            ),
            const SizedBox(height: 20),
            ValueListenableBuilder<int>(
              valueListenable: _timeNotifier,
              builder: (context, val, _) {
                return Text(
                  _formatTime(val),
                  style: const TextStyle(
                    color: Color(0xFFE50914),
                    fontSize: 64,
                    fontFamily: 'monospace',
                    fontWeight: FontWeight.w900,
                    shadows: [Shadow(color: Colors.red, blurRadius: 20)],
                  ),
                );
              },
            ),
            const SizedBox(height: 60),
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 40),
              child: Text(
                "القفل الحديدي مفعّل. لا يمكنك الخروج حتى ينتهي العداد. استغل وقتك في العمل أو الدراسة.",
                textAlign: TextAlign.center,
                style: TextStyle(color: Colors.grey, fontSize: 16, height: 1.5),
              ),
            ),
            const Spacer(),
            if (emergencyContact != null) ...[
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 40, vertical: 20),
                child: SizedBox(
                  width: double.infinity,
                  height: 50,
                  child: OutlinedButton.icon(
                    onPressed: () => NativeLockService.makeEmergencyCall(),
                    icon: const Icon(Icons.emergency_rounded, color: Color(0xFFE50914)),
                    label: Text("اتصال طوارئ: $emergencyContact", style: const TextStyle(color: Colors.white)),
                    style: OutlinedButton.styleFrom(
                      side: const BorderSide(color: Color(0xFFE50914)),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                  ),
                ),
              ),
            ],
            const Padding(
              padding: EdgeInsets.only(bottom: 40),
              child: CircularProgressIndicator(
                valueColor: AlwaysStoppedAnimation<Color>(Color(0xFFE50914)),
                strokeWidth: 2,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
