import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_slidable/flutter_slidable.dart';
import 'package:normpix/theme/app_theme.dart';
import 'package:normpix/theme/theme_controller.dart';
import 'package:normpix/wallet/bank_card_design.dart';
import 'package:normpix/wallet/card_network.dart';
import 'package:normpix/wallet/stored_card.dart';
import 'package:normpix/widgets/feedback.dart';

/// A credit/debit card rendered as a realistic card face. Tapping the card
/// flips it (like a physical card) to its back, which shows the CVV.
///
/// Reveal/edit/delete are exposed per [ThemeController.walletCardActionStyle]:
/// a row of buttons under the card, or a right-to-left swipe. Either way the
/// same actions are also available to screen readers as custom semantics
/// actions, so no action depends on a gesture alone.
class WalletCardView extends StatefulWidget {
  const WalletCardView({
    super.key,
    required this.card,
    required this.revealed,
    required this.actionStyle,
    required this.onToggleReveal,
    required this.onEdit,
    required this.onDelete,
    this.nfcGated = false,
  });

  final StoredCard card;
  final bool revealed;
  final WalletCardActionStyle actionStyle;
  final VoidCallback onToggleReveal;

  /// When true, [onToggleReveal] asks for an NFC tag tap before revealing,
  /// and the reveal control says so.
  final bool nfcGated;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  @override
  State<WalletCardView> createState() => _WalletCardViewState();
}

class _WalletCardViewState extends State<WalletCardView>
    with SingleTickerProviderStateMixin {
  late final AnimationController _flipController = AnimationController(
    vsync: this,
    duration: Durations.extralong2,
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _flipController.duration = AppMotion.of(context, Durations.extralong2);
  }

  @override
  void dispose() {
    _flipController.dispose();
    super.dispose();
  }

  bool get _showingBack => _flipController.value >= 0.5;

  void _flip() {
    AppHaptics.selection();
    if (_flipController.value == 0) {
      _flipController.forward();
    } else {
      _flipController.reverse();
    }
  }

  IconData get _revealIcon {
    if (widget.revealed) return Icons.visibility_off_outlined;
    return widget.nfcGated ? Icons.nfc_rounded : Icons.visibility_outlined;
  }

  String get _revealLabel {
    if (widget.revealed) return 'Hide';
    return widget.nfcGated ? 'Tap NFC to reveal' : 'Reveal';
  }

  String get _maskedNumber => '•••• •••• •••• ${widget.card.last4}';

  String get _spacedNumber => _groupDigits(widget.card.cardNumber);

  String get _semanticsLabel {
    final card = widget.card;
    final kind = card.kind == CardKind.credit ? 'credit' : 'debit';
    final number = widget.revealed
        ? 'number ${_groupDigits(card.cardNumber)}'
        : 'ending in ${card.last4}';
    return '${card.bankName} $kind card, $number, '
        'expires ${card.expiryMonth}/${card.expiryYear}, '
        '${card.cardholderName}';
  }

  @override
  Widget build(BuildContext context) {
    final brandTheme = BankCardDesign.themeFor(widget.card.bankName);

    final flipCard = AspectRatio(
      aspectRatio: 1.586,
      child: AnimatedBuilder(
        animation: _flipController,
        builder: (context, _) {
          // M3's emphasized curve gives the flip weight instead of a
          // constant-speed spin; the small lift peaks mid-flip.
          final t = AppMotion.standard.transform(_flipController.value);
          final angle = t * math.pi;
          final lift = math.sin(t * math.pi) * 0.04;
          final face = angle > math.pi / 2
              ? Transform(
                  alignment: Alignment.center,
                  transform: Matrix4.identity()..rotateY(math.pi),
                  child: _CardBack(
                    card: widget.card,
                    brandTheme: brandTheme,
                    revealed: widget.revealed,
                  ),
                )
              : _CardFront(
                  card: widget.card,
                  brandTheme: brandTheme,
                  revealed: widget.revealed,
                  maskedNumber: _maskedNumber,
                  spacedNumber: _spacedNumber,
                );
          return Semantics(
            button: true,
            label: _semanticsLabel,
            hint: _showingBack ? 'Shows the front' : 'Shows the back and CVV',
            excludeSemantics: true,
            onTap: _flip,
            customSemanticsActions: {
              CustomSemanticsAction(label: _revealLabel):
                  widget.onToggleReveal,
              const CustomSemanticsAction(label: 'Edit card'): widget.onEdit,
              const CustomSemanticsAction(label: 'Delete card'):
                  widget.onDelete,
            },
            child: GestureDetector(
              onTap: _flip,
              child: Transform(
                alignment: Alignment.center,
                transform: Matrix4.identity()
                  ..setEntry(3, 2, 0.0014)
                  ..scaleByDouble(1.0 + lift, 1.0 + lift, 1.0 + lift, 1.0)
                  ..rotateY(angle),
                child: face,
              ),
            ),
          );
        },
      ),
    );

    if (widget.actionStyle == WalletCardActionStyle.swipe) {
      final scheme = Theme.of(context).colorScheme;
      return ClipRRect(
        borderRadius: AppRadius.rLg,
        child: Slidable(
          endActionPane: ActionPane(
            motion: const ScrollMotion(),
            extentRatio: 0.72,
            children: [
              SlidableAction(
                onPressed: (_) => widget.onToggleReveal(),
                backgroundColor: scheme.secondaryContainer,
                foregroundColor: scheme.onSecondaryContainer,
                icon: _revealIcon,
                label: widget.revealed ? 'Hide' : 'Reveal',
              ),
              SlidableAction(
                onPressed: (_) => widget.onEdit(),
                backgroundColor: scheme.surfaceContainerHighest,
                foregroundColor: scheme.onSurfaceVariant,
                icon: Icons.edit_outlined,
                label: 'Edit',
              ),
              SlidableAction(
                onPressed: (_) => widget.onDelete(),
                backgroundColor: scheme.errorContainer,
                foregroundColor: scheme.onErrorContainer,
                icon: Icons.delete_outline_rounded,
                label: 'Delete',
              ),
            ],
          ),
          child: flipCard,
        ),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        flipCard,
        const SizedBox(height: AppSpacing.md),
        _CardActions(
          revealIcon: _revealIcon,
          revealLabel: _revealLabel,
          onToggleReveal: widget.onToggleReveal,
          onEdit: widget.onEdit,
          onDelete: widget.onDelete,
        ),
      ],
    );
  }
}

String _groupDigits(String digits) {
  final buf = StringBuffer();
  for (var i = 0; i < digits.length; i++) {
    if (i > 0 && i % 4 == 0) buf.write(' ');
    buf.write(digits[i]);
  }
  return buf.toString();
}

/// Reveal is the card's main action, so it gets the wide tonal button;
/// edit and delete are secondary and sit as labelled icon buttons. Delete
/// stays neutral here — the confirmation dialog is where it turns red.
class _CardActions extends StatelessWidget {
  const _CardActions({
    required this.revealIcon,
    required this.revealLabel,
    required this.onToggleReveal,
    required this.onEdit,
    required this.onDelete,
  });

  final IconData revealIcon;
  final String revealLabel;
  final VoidCallback onToggleReveal;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: FilledButton.tonalIcon(
            onPressed: onToggleReveal,
            icon: Icon(revealIcon),
            label: Text(revealLabel, overflow: TextOverflow.ellipsis),
          ),
        ),
        const SizedBox(width: AppSpacing.sm),
        IconButton.filledTonal(
          onPressed: onEdit,
          tooltip: 'Edit card',
          icon: const Icon(Icons.edit_outlined),
        ),
        const SizedBox(width: AppSpacing.xs),
        IconButton.filledTonal(
          onPressed: onDelete,
          tooltip: 'Delete card',
          icon: const Icon(Icons.delete_outline_rounded),
        ),
      ],
    );
  }
}

/// Subtle foil emboss shadow behind card text runs: realistic specular highlight on top
/// with soft shadow beneath to match real stamped physical bank cards.
const _cardEmbossShadow = [
  Shadow(color: Colors.black54, blurRadius: 4, offset: Offset(0, 1.5)),
  Shadow(color: Color(0x33FFFFFF), blurRadius: 1, offset: Offset(0, -0.8)),
];

class _CardFront extends StatelessWidget {
  const _CardFront({
    required this.card,
    required this.brandTheme,
    required this.revealed,
    required this.maskedNumber,
    required this.spacedNumber,
  });

  final StoredCard card;
  final BankBrandTheme brandTheme;
  final bool revealed;
  final String maskedNumber;
  final String spacedNumber;

  @override
  Widget build(BuildContext context) {
    final network = CardNetwork.fromNumber(card.cardNumber);
    final iconStyle =
        ThemeControllerScope.maybeOf(context)?.bankIconStyle ??
        BankIconStyle.full;

    return _CardFace(
      brandTheme: brandTheme,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Flexible(
                child: brandTheme.buildLogo(
                  context,
                  compact: iconStyle == BankIconStyle.compact,
                ),
              ),
              const SizedBox(width: 8),
              _Badge(
                text: card.kind == CardKind.credit ? 'CREDIT' : 'DEBIT',
                backgroundColor: brandTheme.badgeColor,
                borderColor: brandTheme.badgeBorderColor,
                textColor: brandTheme.badgeTextColor,
              ),
            ],
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              _EmvChip(finish: brandTheme.chipFinish),
              const SizedBox(width: 12),
              Icon(
                Icons.contactless_rounded,
                color: Colors.white.withValues(alpha: 0.85),
                size: 24,
                shadows: const [
                  Shadow(color: Colors.black38, blurRadius: 2, offset: Offset(0, 1)),
                ],
              ),
            ],
          ),
          const Spacer(),
          Text(
            revealed ? spacedNumber : maskedNumber,
            style: const TextStyle(
              color: Colors.white,
              fontWeight: FontWeight.w700,
              fontSize: 18.5,
              letterSpacing: 2.0,
              fontFamily: 'monospace',
              shadows: _cardEmbossShadow,
            ),
          ),
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Flexible(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          'VALID\nTHRU',
                          style: TextStyle(
                            color: Colors.white.withValues(alpha: 0.7),
                            fontWeight: FontWeight.w800,
                            fontSize: 7.5,
                            letterSpacing: 0.2,
                            height: 1.0,
                            shadows: _cardEmbossShadow,
                          ),
                        ),
                        const SizedBox(width: 6),
                        Text(
                          '${card.expiryMonth}/${card.expiryYear}',
                          style: const TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.w700,
                            fontSize: 13,
                            letterSpacing: 1.0,
                            shadows: _cardEmbossShadow,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      card.cardholderName.toUpperCase(),
                      style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w700,
                        fontSize: 13,
                        letterSpacing: 0.8,
                        shadows: _cardEmbossShadow,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
              if (network != CardNetwork.unknown)
                Padding(
                  padding: const EdgeInsets.only(bottom: 2),
                  child: network.buildBadge(height: 24),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

/// Realistic EMV chip glyph with gold or platinum/silver finish and authentic circuit traces.
class _EmvChip extends StatelessWidget {
  const _EmvChip({this.finish = EmvChipFinish.gold});

  final EmvChipFinish finish;

  @override
  Widget build(BuildContext context) {
    final colors = finish == EmvChipFinish.gold
        ? const [Color(0xFFFBE8A6), Color(0xFFD4A017), Color(0xFFA67C00)]
        : const [Color(0xFFE8E8E8), Color(0xFFB0B0B0), Color(0xFF888888)];

    final borderColor = finish == EmvChipFinish.gold
        ? const Color(0xFFECC865)
        : const Color(0xFFCCCCCC);

    return Container(
      width: 38,
      height: 28,
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: colors,
        ),
        borderRadius: BorderRadius.circular(5.5),
        border: Border.all(color: borderColor, width: 0.8),
        boxShadow: const [
          BoxShadow(
            color: Colors.black38,
            blurRadius: 3,
            offset: Offset(0, 1.5),
          ),
        ],
      ),
      child: CustomPaint(painter: _ChipLinesPainter()),
    );
  }
}

class _ChipLinesPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Colors.black.withValues(alpha: 0.35)
      ..strokeWidth = 1.0;

    final w = size.width;
    final h = size.height;

    // Horizontal center divider
    canvas.drawLine(Offset(0, h * 0.48), Offset(w, h * 0.48), paint);

    // Vertical contact dividers
    canvas.drawLine(Offset(w * 0.32, 0), Offset(w * 0.32, h), paint);
    canvas.drawLine(Offset(w * 0.68, 0), Offset(w * 0.68, h), paint);

    // Central smart card core oval
    final coreRect = Rect.fromCenter(
      center: Offset(w / 2, h * 0.48),
      width: w * 0.36,
      height: h * 0.36,
    );
    canvas.drawOval(
      coreRect,
      Paint()
        ..color = Colors.black.withValues(alpha: 0.18)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 0.8,
    );
  }

  @override
  bool shouldRepaint(covariant _ChipLinesPainter oldDelegate) => false;
}

class _CardBack extends StatelessWidget {
  const _CardBack({
    required this.card,
    required this.brandTheme,
    required this.revealed,
  });

  final StoredCard card;
  final BankBrandTheme brandTheme;
  final bool revealed;

  @override
  Widget build(BuildContext context) {
    return _CardFace(
      brandTheme: brandTheme,
      padding: EdgeInsets.zero,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SizedBox(height: 20),
          // Realistic magnetic stripe with subtle brushed sheen
          Container(
            height: 40,
            decoration: const BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [Color(0xFF1F1F1F), Color(0xFF0F0F0F)],
              ),
            ),
          ),
          const SizedBox(height: 16),
          // Signature & CVV panel
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
            child: Row(
              children: [
                Expanded(
                  flex: 3,
                  child: Container(
                    height: 32,
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: 0.85),
                      borderRadius: const BorderRadius.horizontal(left: Radius.circular(4)),
                    ),
                    alignment: Alignment.centerLeft,
                    padding: const EdgeInsets.only(left: 8),
                    child: Text(
                      'AUTHORIZED SIGNATURE',
                      style: TextStyle(
                        color: Colors.black.withValues(alpha: 0.35),
                        fontSize: 7.5,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 0.4,
                      ),
                    ),
                  ),
                ),
                Expanded(
                  flex: 1,
                  child: Container(
                    height: 32,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: const BorderRadius.horizontal(right: Radius.circular(4)),
                      border: Border.all(color: Colors.black12),
                    ),
                    child: Text(
                      revealed ? card.cvv : '•' * card.cvv.length,
                      style: const TextStyle(
                        color: Colors.black87,
                        fontWeight: FontWeight.w800,
                        fontStyle: FontStyle.italic,
                        letterSpacing: 1.5,
                        fontFamily: 'monospace',
                        fontSize: 13,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const Spacer(),
          Padding(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Flexible(
                  child: Text(
                    brandTheme.displayName,
                    style: const TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.w700,
                      fontSize: 12,
                      shadows: _cardEmbossShadow,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                Text(
                  'CVV / CVC',
                  style: TextStyle(
                    color: Colors.white.withValues(alpha: 0.8),
                    fontWeight: FontWeight.w700,
                    fontSize: 10,
                    letterSpacing: 0.6,
                    shadows: _cardEmbossShadow,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _CardFace extends StatelessWidget {
  const _CardFace({
    required this.brandTheme,
    required this.child,
    this.padding = const EdgeInsets.all(AppSpacing.lg),
  });

  final BankBrandTheme brandTheme;
  final Widget child;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    return Container(
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        gradient: brandTheme.gradient,
        borderRadius: AppRadius.rXxl,
        boxShadow: [
          BoxShadow(
            color: brandTheme.gradientColors.last.withValues(alpha: 0.40),
            blurRadius: 20,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          // Dynamic authentic bank watermark/pattern background
          Positioned.fill(
            child: CustomPaint(
              painter: BankPatternPainter(
                patternType: brandTheme.patternType,
                accentColor: brandTheme.accentColor,
              ),
            ),
          ),
          // Subtle top-to-bottom card surface reflection sheen
          Positioned.fill(
            child: Container(
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [
                    Color(0x18FFFFFF),
                    Color(0x00FFFFFF),
                    Color(0x12000000),
                  ],
                  stops: [0.0, 0.5, 1.0],
                ),
              ),
            ),
          ),
          Padding(
            padding: padding,
            child: child,
          ),
        ],
      ),
    );
  }
}

class _Badge extends StatelessWidget {
  const _Badge({
    required this.text,
    required this.backgroundColor,
    required this.borderColor,
    required this.textColor,
  });

  final String text;
  final Color backgroundColor;
  final Color borderColor;
  final Color textColor;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 3.5),
      decoration: BoxDecoration(
        color: backgroundColor,
        borderRadius: AppRadius.rFull,
        border: Border.all(color: borderColor, width: 1.0),
      ),
      child: Text(
        text,
        style: TextStyle(
          color: textColor,
          fontWeight: FontWeight.w800,
          fontSize: 10,
          letterSpacing: 0.8,
        ),
      ),
    );
  }
}

/// Standalone card face preview (without action bars or flip gestures),
/// used in sheets and creation forms for real-time visual feedback as the
/// user types or selects their bank.
class WalletCardFacePreview extends StatelessWidget {
  const WalletCardFacePreview({
    super.key,
    required this.card,
    this.revealed = true,
  });

  final StoredCard card;
  final bool revealed;

  String get _maskedNumber => '•••• •••• •••• ${card.last4}';

  String get _spacedNumber {
    final buf = StringBuffer();
    final number = card.cardNumber;
    for (var i = 0; i < number.length; i++) {
      if (i > 0 && i % 4 == 0) buf.write(' ');
      buf.write(number[i]);
    }
    return buf.toString();
  }

  @override
  Widget build(BuildContext context) {
    final brandTheme = BankCardDesign.themeFor(card.bankName);

    return AspectRatio(
      aspectRatio: 1.586,
      child: _CardFront(
        card: card,
        brandTheme: brandTheme,
        revealed: revealed,
        maskedNumber: _maskedNumber,
        spacedNumber: _spacedNumber,
      ),
    );
  }
}

