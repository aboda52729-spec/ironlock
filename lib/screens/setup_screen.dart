import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../main.dart';
import '../native_lock.dart';
import 'app_selection_sheet.dart';

class SetupScreen extends StatefulWidget {
  const SetupScreen({super.key});

  @override
  State<SetupScreen> createState() => _SetupScreenState();
}

class _SetupScreenState extends State<SetupScreen> {
  int _selectedMinutes = 25;
  bool _isFullLockMode = true;
  List<String> _selectedApps = [];

  void _startIronLock() async {
    final sessionProvider = context.read<SessionProvider>();
    
    // Show loading dialog
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => const Center(child: CircularProgressIndicator(color: Color(0xFFE50914))),
    );

    bool success = await NativeLockService.startSession(
      durationMillis: _selectedMinutes * 60 * 1000,
      isFullLockMode: _isFullLockMode,
      selectedApps: _selectedApps,
    );

    if (mounted) Navigator.pop(context); // Remove loading

    if (success) {
      await sessionProvider.checkStatus();
    } else {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text("فشل بدء الجلسة. تأكد من منح كافة الصلاحيات.")),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("إعداد القفل الحديدي", style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: Colors.transparent,
        elevation: 0,
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildSectionTitle("مدة التركيز (بالدقائق)"),
            const SizedBox(height: 16),
            _buildTimerSelector(),
            const SizedBox(height: 32),
            _buildSectionTitle("وضع القفل"),
            const SizedBox(height: 16),
            _buildLockModeSelector(),
            const SizedBox(height: 32),
            if (!_isFullLockMode) ...[
              _buildSectionTitle("التطبيقات المحظورة"),
              const SizedBox(height: 16),
              _buildAppSelector(),
              const SizedBox(height: 32),
            ],
            const SizedBox(height: 20),
            _buildStartButton(),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Text(
      title,
      style: const TextStyle(color: Colors.white70, fontSize: 16, fontWeight: FontWeight.w500),
    );
  }

  Widget _buildTimerSelector() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      decoration: BoxDecoration(
        color: const Color(0xFF1F1F1F),
        borderRadius: BorderRadius.circular(12),
      ),
      child: DropdownButtonHideUnderline(
        child: DropdownButton<int>(
          value: _selectedMinutes,
          isExpanded: true,
          dropdownColor: const Color(0xFF1F1F1F),
          items: [5, 15, 25, 45, 60, 90, 120].map((int value) {
            return DropdownMenuItem<int>(
              value: value,
              child: Text("$value دقيقة", style: const TextStyle(color: Colors.white)),
            );
          }).toList(),
          onChanged: (val) => setState(() => _selectedMinutes = val!),
        ),
      ),
    );
  }

  Widget _buildLockModeSelector() {
    return Column(
      children: [
        _buildModeCard(
          title: "قفل كامل للرأس (Full Lock)",
          subtitle: "يتم قفل الشاشة تماماً ومنع الوصول لأي شيء عدا الطوارئ.",
          icon: Icons.security_rounded,
          isActive: _isFullLockMode,
          onTap: () => setState(() => _isFullLockMode = true),
        ),
        const SizedBox(height: 12),
        _buildModeCard(
          title: "قفل تطبيقات محددة",
          subtitle: "يمكنك استخدام الهاتف ولكن سيتم منع التطبيقات التي تختارها.",
          icon: Icons.app_blocking_rounded,
          isActive: !_isFullLockMode,
          onTap: () => setState(() => _isFullLockMode = false),
        ),
      ],
    );
  }

  Widget _buildModeCard({
    required String title,
    required String subtitle,
    required IconData icon,
    required bool isActive,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: isActive ? const Color(0xFFE50914).withOpacity(0.1) : const Color(0xFF1F1F1F),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: isActive ? const Color(0xFFE50914) : Colors.transparent,
            width: 2,
          ),
        ),
        child: Row(
          children: [
            Icon(icon, color: isActive ? const Color(0xFFE50914) : Colors.grey, size: 32),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 4),
                  Text(subtitle, style: const TextStyle(color: Colors.grey, fontSize: 12)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAppSelector() {
    return InkWell(
      onTap: () async {
        final apps = await showModalBottomSheet<List<String>>(
          context: context,
          isScrollControlled: true,
          backgroundColor: const Color(0xFF0D0D0D),
          builder: (context) => AppSelectionSheet(initialSelection: _selectedApps),
        );
        if (apps != null) setState(() => _selectedApps = apps);
      },
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: const Color(0xFF1F1F1F),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              _selectedApps.isEmpty ? "اختر التطبيقات" : "تم اختيار ${_selectedApps.length} تطبيق",
              style: const TextStyle(color: Colors.white),
            ),
            const Icon(Icons.arrow_forward_ios_rounded, color: Colors.grey, size: 16),
          ],
        ),
      ),
    );
  }

  Widget _buildStartButton() {
    return SizedBox(
      width: double.infinity,
      height: 56,
      child: ElevatedButton(
        onPressed: _startIronLock,
        style: ElevatedButton.styleFrom(
          backgroundColor: const Color(0xFFE50914),
          shape: RoundedRectangleManager.circular(12),
        ),
        child: const Text(
          "تفعيل القفل الحديدي",
          style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white),
        ),
      ),
    );
  }
}

// Helper class since original code might have a typo
class RoundedRectangleManager {
  static RoundedRectangleBorder circular(double radius) {
    return RoundedRectangleBorder(borderRadius: BorderRadius.circular(radius));
  }
}
