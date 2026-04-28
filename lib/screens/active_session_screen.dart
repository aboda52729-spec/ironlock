import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../main.dart';

class ActiveSessionScreen extends StatefulWidget {
  final int remainingMilli;
  const ActiveSessionScreen({super.key, required this.remainingMilli});

  @override
  State<ActiveSessionScreen> createState() => _ActiveSessionScreenState();
}

class _ActiveSessionScreenState extends State<ActiveSessionScreen> {
  late Timer _timer;
  int _currentMilli = 0;

  @override
  void initState() {
    super.initState();
    _currentMilli = widget.remainingMilli;
    _startTimer();
  }

  void _startTimer() {
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      setState(() {
        if (_currentMilli <= 0) {
          _timer.cancel();
          // Notify provider to refresh status
          context.read<SessionProvider>().checkStatus();
        } else {
          _currentMilli -= 1000;
        }
      });
    });
  }

  @override
  void dispose() {
    _timer.cancel();
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
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(
              Icons.lock_outline_rounded,
              size: 100,
              color: Color(0xFFE50914),
            ),
            const SizedBox(height: 40),
            const Text(
              "الوقت المتبقي للحرية",
              style: TextStyle(
                color: Colors.white,
                fontSize: 24,
                fontWeight: FontWeight.bold,
                letterSpacing: 1.2,
              ),
            ),
            const SizedBox(height: 20),
            Text(
              _formatTime(_currentMilli),
              style: const TextStyle(
                color: Color(0xFFE50914),
                fontSize: 64,
                fontFamily: 'monospace',
                fontWeight: FontWeight.w900,
                shadows: [
                  Shadow(
                    color: Colors.red,
                    blurRadius: 20,
                  )
                ],
              ),
            ),
            const SizedBox(height: 60),
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 40),
              child: Text(
                "القفل الحديدي مفعّل. لا يمكنك الخروج حتى ينتهي العداد. استغل وقتك في العمل أو الدراسة.",
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: Colors.grey,
                  fontSize: 16,
                  height: 1.5,
                ),
              ),
            ),
            const SizedBox(height: 40),
            const CircularProgressIndicator(
              valueColor: AlwaysStoppedAnimation<Color>(Color(0xFFE50914)),
              strokeWidth: 2,
            ),
          ],
        ),
      ),
    );
  }
}
