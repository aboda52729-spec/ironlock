import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:flutter_animate/flutter_animate.dart';
import '../main.dart';
import '../native_lock.dart';
import 'app_selection_sheet.dart';
import 'permissions_screen.dart';

class SetupScreen extends StatefulWidget {
  const SetupScreen({super.key});

  @override
  State<SetupScreen> createState() => _SetupScreenState();
}

class _SetupScreenState extends State<SetupScreen> with SingleTickerProviderStateMixin {
  final TextEditingController _emergencyContactController = TextEditingController();
  int _selectedHours = 0;
  int _selectedMinutes = 25;
  bool _isFullLockMode = true;
  List<String> _selectedApps = [];
  late AnimationController _animationController;
  bool _isAnimating = false;

  @override
  void initState() {
    super.initState();
    _animationController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 300),
    );
  }

  @override
  void dispose() {
    _emergencyContactController.dispose();
    _animationController.dispose();
    super.dispose();
  }

  void _startIronLock() async {
    if (_isAnimating) return;
    setState(() => _isAnimating = true);

    final sessionProvider = context.read<SessionProvider>();
    
    // Pre-flight permission check - verify ALL permissions before attempting session
    final hasAccessibility = await NativeLockService.checkAccessibilityPermission();
    final hasOverlay = await NativeLockService.checkOverlayPermission();
    final hasDeviceAdmin = await NativeLockService.isDeviceAdminEnabled();
    
    if (!hasAccessibility || !hasOverlay || !hasDeviceAdmin) {
      if (mounted) {
        List<String> missing = [];
        if (!hasAccessibility) missing.add("إمكانية الوصول");
        if (!hasOverlay) missing.add("العرض فوق التطبيقات");
        if (!hasDeviceAdmin) missing.add("مسؤول الجهاز");
        
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text("الأذونات التالية غير مفعلة: ${missing.join('، ')}"),
            backgroundColor: const Color(0xFFFF2E63),
            duration: const Duration(seconds: 3),
          ),
        );
        
        // Redirect back to permissions screen
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(builder: (_) => const PermissionsScreen()),
        );
      }
      setState(() => _isAnimating = false);
      return;
    }

    // Show loading dialog
    if (!mounted) {
      setState(() => _isAnimating = false);
      return;
    }
    
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => const Center(
        child: CircularProgressIndicator(color: Color(0xFFFF2E63)),
      ),
    );

    final totalMinutes = (_selectedHours * 60) + _selectedMinutes;
    bool success = await NativeLockService.startSession(
      durationMillis: totalMinutes * 60 * 1000,
      isFullLockMode: _isFullLockMode,
      selectedApps: _selectedApps,
      emergencyContact: _emergencyContactController.text.trim().isNotEmpty 
          ? _emergencyContactController.text.trim() 
          : null,
    );

    if (mounted) Navigator.pop(context); // Remove loading

    if (success) {
      await sessionProvider.checkStatus();
    } else {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text("فشل بدء الجلسة. حدث خطأ داخلي."),
            backgroundColor: Color(0xFFFF2E63),
          ),
        );
      }
    }
    
    setState(() => _isAnimating = false);
  }

  void _onHourChanged(int value) {
    setState(() {
      _selectedHours = value;
    });
    _animationController.forward(from: 0);
  }

  void _onMinuteChanged(int value) {
    setState(() {
      _selectedMinutes = value;
    });
    _animationController.forward(from: 0);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: CustomScrollView(
        slivers: [
          SliverAppBar(
            expandedHeight: 180,
            floating: false,
            pinned: true,
            backgroundColor: const Color(0xFF0A0A0F),
            flexibleSpace: FlexibleSpaceBar(
              title: const Text(
                "إعداد القفل الحديدي",
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 20),
              ),
              centerTitle: true,
              background: Container(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [
                      const Color(0xFFFF2E63).withValues(alpha: 0.3),
                      const Color(0xFF0A0A0F),
                    ],
                  ),
                ),
                child: const Icon(
                  Icons.lock_outline_rounded,
                  size: 80,
                  color: Color(0xFFFF2E63),
                ).animate().scale(delay: 200.ms, duration: 600.ms).fadeIn(),
              ),
            ),
            elevation: 0,
          ),
          SliverPadding(
            padding: const EdgeInsets.all(24.0),
            sliver: SliverList(
              delegate: SliverChildListDelegate([
                _buildTimerSection().animate().fadeIn(delay: 300.ms).slideY(begin: 0.2, end: 0),
                const SizedBox(height: 32),
                _buildEmergencySection().animate().fadeIn(delay: 500.ms).slideY(begin: 0.2, end: 0),
                const SizedBox(height: 32),
                _buildLockModeSection().animate().fadeIn(delay: 700.ms).slideY(begin: 0.2, end: 0),
                const SizedBox(height: 32),
                if (!_isFullLockMode) ...[
                  _buildAppSelector().animate().fadeIn(delay: 900.ms).slideY(begin: 0.2, end: 0),
                  const SizedBox(height: 32),
                ],
                const SizedBox(height: 20),
                _buildStartButton().animate().fadeIn(delay: 1100.ms).scale(delay: 1100.ms, duration: 400.ms),
                const SizedBox(height: 40),
              ]),
            ),
          ),
        ],
      ),
    );
  }
  
  Widget _buildEmergencySection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: const Color(0xFF08D9D6).withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Icon(Icons.emergency_rounded, color: Color(0xFF08D9D6), size: 24),
            ),
            const SizedBox(width: 12),
            const Text(
              "رقم اتصال للطوارئ",
              style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold),
            ),
          ],
        ).animate().fadeIn(),
        const SizedBox(height: 16),
        TextField(
          controller: _emergencyContactController,
          keyboardType: TextInputType.phone,
          style: const TextStyle(color: Colors.white, fontSize: 16),
          decoration: InputDecoration(
            hintText: "مثلاً: 0912345678",
            hintStyle: const TextStyle(color: Colors.grey, fontSize: 14),
            prefixIcon: const Icon(Icons.phone_rounded, color: Color(0xFFFF2E63)),
          ),
        ).animate().fadeIn(delay: 200.ms),
      ],
    );
  }

  Widget _buildTimerSection() {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            const Color(0xFF1A1A2E),
            const Color(0xFF252A34),
          ],
        ),
        borderRadius: BorderRadius.circular(24),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFFFF2E63).withValues(alpha: 0.1),
            blurRadius: 20,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: const Color(0xFFFF2E63).withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Icon(Icons.timer_outlined, color: Color(0xFFFF2E63), size: 24),
              ),
              const SizedBox(width: 12),
              const Text(
                "مدة التركيز",
                style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold),
              ),
            ],
          ),
          const SizedBox(height: 24),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Expanded(
                child: _buildRollingPicker(
                  label: "ساعة",
                  values: List.generate(24, (i) => i),
                  selectedValue: _selectedHours,
                  onChanged: _onHourChanged,
                  icon: Icons.schedule,
                ),
              ),
              const SizedBox(width: 16),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                decoration: BoxDecoration(
                  color: const Color(0xFFFF2E63),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Text(
                  ":",
                  style: TextStyle(color: Colors.white, fontSize: 32, fontWeight: FontWeight.bold),
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: _buildRollingPicker(
                  label: "دقيقة",
                  values: [5, 10, 15, 20, 25, 30, 45, 60, 90, 120],
                  selectedValue: _selectedMinutes == 0 ? 5 : _selectedMinutes,
                  onChanged: _onMinuteChanged,
                  icon: Icons.access_time,
                ),
              ),
            ],
          ).animate().scale(delay: 300.ms, duration: 500.ms),
          const SizedBox(height: 20),
          AnimatedBuilder(
            animation: _animationController,
            builder: (context, child) {
              final totalMinutes = (_selectedHours * 60) + _selectedMinutes;
              final hours = totalMinutes ~/ 60;
              final mins = totalMinutes % 60;
              return Container(
                padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                decoration: BoxDecoration(
                  color: const Color(0xFF08D9D6).withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: const Color(0xFF08D9D6).withValues(alpha: 0.3)),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(Icons.info_outline, color: Color(0xFF08D9D6), size: 18),
                    const SizedBox(width: 8),
                    Text(
                      "المدة الإجمالية: ${hours > 0 ? '$hours ساعة و ' : ''}$mins دقيقة",
                      style: const TextStyle(color: Color(0xFF08D9D6), fontWeight: FontWeight.w600),
                    ),
                  ],
                ),
              ).animate().fadeIn(duration: 200.ms);
            },
          ),
        ],
      ),
    );
  }

  Widget _buildRollingPicker({
    required String label,
    required List<int> values,
    required int selectedValue,
    required ValueChanged<int> onChanged,
    required IconData icon,
  }) {
    return Column(
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, color: const Color(0xFFFF2E63), size: 18),
            const SizedBox(width: 6),
            Text(label, style: const TextStyle(color: Colors.white70, fontSize: 14)),
          ],
        ),
        const SizedBox(height: 12),
        Container(
          height: 180,
          decoration: BoxDecoration(
            color: const Color(0xFF0A0A0F),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: const Color(0xFFFF2E63).withValues(alpha: 0.3)),
          ),
          child: Listener(
            onPointerUp: (_) {
              HapticFeedback.lightImpact();
            },
            child: ListWheelScrollView.useDelegate(
              itemExtent: 50,
              physics: const FixedExtentScrollPhysics(),
              diameterRatio: 1.4,
              offAxisFraction: 0.0,
              useMagnifier: true,
              magnification: 1.2,
              onSelectedItemChanged: (index) {
                if (index >= 0 && index < values.length) {
                  onChanged(values[index]);
                }
              },
              childDelegate: ListWheelChildBuilderDelegate(
                builder: (context, index) {
                  if (index < 0 || index >= values.length) return const SizedBox.shrink();
                  final value = values[index];
                  final isSelected = value == selectedValue;
                  return Center(
                    child: AnimatedScale(
                      scale: isSelected ? 1.3 : 0.9,
                      duration: const Duration(milliseconds: 200),
                      child: AnimatedOpacity(
                        opacity: isSelected ? 1.0 : 0.4,
                        duration: const Duration(milliseconds: 200),
                        child: Text(
                          '$value',
                          style: TextStyle(
                            color: isSelected ? const Color(0xFFFF2E63) : Colors.white54,
                            fontSize: isSelected ? 28 : 18,
                            fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                          ),
                        ),
                      ),
                    ),
                  );
                },
                childCount: values.length,
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildLockModeSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: const Color(0xFF252A34).withValues(alpha: 0.5),
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Icon(Icons.security_rounded, color: Color(0xFF08D9D6), size: 24),
            ),
            const SizedBox(width: 12),
            const Text(
              "وضع القفل",
              style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold),
            ),
          ],
        ),
        const SizedBox(height: 16),
        _buildModeCard(
          title: "قفل كامل للرأس",
          subtitle: "يتم قفل الشاشة تماماً ومنع الوصول لأي شيء عدا الطوارئ",
          icon: Icons.lock_rounded,
          isActive: _isFullLockMode,
          onTap: () => setState(() => _isFullLockMode = true),
        ).animate().fadeIn(delay: 100.ms),
        const SizedBox(height: 12),
        _buildModeCard(
          title: "قفل تطبيقات محددة",
          subtitle: "يمكنك استخدام الهاتف ولكن سيتم منع التطبيقات التي تختارها",
          icon: Icons.app_blocking_rounded,
          isActive: !_isFullLockMode,
          onTap: () => setState(() => _isFullLockMode = false),
        ).animate().fadeIn(delay: 200.ms),
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
        duration: const Duration(milliseconds: 300),
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          gradient: isActive
              ? LinearGradient(
                  colors: [
                    const Color(0xFFFF2E63).withValues(alpha: 0.2),
                    const Color(0xFFFF2E63).withValues(alpha: 0.05),
                  ],
                )
              : null,
          color: isActive ? null : const Color(0xFF1A1A2E),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: isActive ? const Color(0xFFFF2E63) : Colors.transparent,
            width: 2,
          ),
          boxShadow: isActive
              ? [
                  BoxShadow(
                    color: const Color(0xFFFF2E63).withValues(alpha: 0.3),
                    blurRadius: 15,
                    offset: const Offset(0, 5),
                  ),
                ]
              : null,
        ),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: isActive ? const Color(0xFFFF2E63) : const Color(0xFF252A34),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Icon(icon, color: isActive ? Colors.white : Colors.grey, size: 28),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: TextStyle(
                      color: isActive ? Colors.white : Colors.white70,
                      fontWeight: FontWeight.bold,
                      fontSize: 16,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    subtitle,
                    style: TextStyle(
                      color: isActive ? Colors.white70 : Colors.grey,
                      fontSize: 13,
                    ),
                  ),
                ],
              ),
            ),
            AnimatedSwitcher(
              duration: const Duration(milliseconds: 300),
              child: isActive
                  ? const Icon(Icons.check_circle, color: Color(0xFFFF2E63), key: ValueKey(1))
                  : const Icon(Icons.circle_outlined, color: Colors.grey, key: ValueKey(0)),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAppSelector() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: const Color(0xFF252A34).withValues(alpha: 0.5),
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Icon(Icons.apps_rounded, color: Color(0xFF08D9D6), size: 24),
            ),
            const SizedBox(width: 12),
            const Text(
              "التطبيقات المحظورة",
              style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold),
            ),
          ],
        ),
        const SizedBox(height: 16),
        InkWell(
          onTap: () async {
            HapticFeedback.lightImpact();
            final apps = await showModalBottomSheet<List<String>>(
              context: context,
              isScrollControlled: true,
              backgroundColor: Colors.transparent,
              builder: (context) => AppSelectionSheet(initialSelectedApps: _selectedApps),
            );
            if (apps != null) setState(() => _selectedApps = apps);
          },
          child: Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: const Color(0xFF1A1A2E),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: const Color(0xFFFF2E63).withValues(alpha: 0.3)),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: const Color(0xFFFF2E63).withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Icon(Icons.list_alt_rounded, color: Color(0xFFFF2E63), size: 22),
                    ),
                    const SizedBox(width: 16),
                    Text(
                      _selectedApps.isEmpty
                          ? "اختر التطبيقات"
                          : "تم اختيار ${_selectedApps.length} تطبيق",
                      style: const TextStyle(color: Colors.white, fontSize: 16),
                    ),
                  ],
                ),
                const Icon(Icons.arrow_forward_ios_rounded, color: Colors.grey, size: 18),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildStartButton() {
    final totalMinutes = (_selectedHours * 60) + _selectedMinutes;
    return Container(
      width: double.infinity,
      height: 64,
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [
            const Color(0xFFFF2E63),
            const Color(0xFFFF2E63).withValues(alpha: 0.8),
          ],
        ),
        borderRadius: BorderRadius.circular(20),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFFFF2E63).withValues(alpha: 0.4),
            blurRadius: 20,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: _isAnimating ? null : _startIronLock,
          borderRadius: BorderRadius.circular(20),
          splashColor: Colors.white.withValues(alpha: 0.3),
          child: Center(
            child: _isAnimating
                ? const SizedBox(
                    width: 24,
                    height: 24,
                    child: CircularProgressIndicator(
                      color: Colors.white,
                      strokeWidth: 2.5,
                    ),
                  )
                : Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.lock_outline_rounded, color: Colors.white, size: 24),
                      const SizedBox(width: 12),
                      Text(
                        "تفعيل القفل الحديدي",
                        style: const TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                        ),
                      ),
                    ],
                  ),
          ),
        ),
      ),
    ).animate().hover().scale(delay: 100.ms, duration: 200.ms);
  }
}
