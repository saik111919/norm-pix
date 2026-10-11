package com.example.normpix.wallet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.normpix.ui.components.SvgAsset
import com.example.normpix.ui.components.rememberSvgAspectRatio

private val White = Color.White

@Composable
private fun LogoText(text: String, size: TextUnit, weight: FontWeight, spacing: TextUnit = 0.sp, family: FontFamily? = null) {
    Text(
        text,
        style = TextStyle(color = White, fontWeight = weight, fontSize = size, letterSpacing = spacing, fontFamily = family),
        maxLines = 1,
    )
}

/**
 * The bank's logo for the card face, chosen from the bank's name by
 * [BankLogoResolver] - the card itself never picks artwork. Bundled artwork is
 * fitted whole into a [LogoMaxWidth] x [LogoMaxHeight] area ([compact]: the
 * symbol, in a smaller area) at its natural proportions, so a long wordmark and
 * a square symbol both fill the space they need without being stretched or
 * cropped. Full-color artwork sits on a light chip to stay legible on the dark
 * card. Banks with no artwork on file (or whose artwork won't load) get the
 * theme's hand-drawn mark, which for unrecognized banks is a generic bank icon
 * beside the name - never an empty or broken logo.
 */
@Composable
fun BankLogoView(bankName: String, theme: BankBrandTheme, compact: Boolean, modifier: Modifier = Modifier) {
    val file = remember(bankName, compact) { BankLogoResolver.logoFor(bankName)?.pick(compact, onDark = true) }
    val aspectRatio = file?.let { rememberSvgAspectRatio(it.path) }
    if (file == null || aspectRatio == null) {
        Box(modifier.semantics { contentDescription = theme.displayName }) { HandDrawnLogo(theme.logo) }
        return
    }
    val maxWidth = if (compact) CompactLogoMaxWidth else LogoMaxWidth
    val artwork = Modifier.sizeIn(maxWidth = maxWidth, maxHeight = LogoMaxHeight).aspectRatio(aspectRatio)
    Box(
        modifier
            .semantics { contentDescription = theme.displayName }
            .then(
                if (file.needsLightBacking) {
                    Modifier
                        .clip(RoundedCornerShape(if (compact) 20.dp else 6.dp))
                        .background(White.copy(alpha = 0.92f))
                        .padding(horizontal = if (compact) 6.dp else 10.dp, vertical = 6.dp)
                } else {
                    Modifier
                },
            ),
    ) {
        SvgAsset(file.path, artwork)
    }
}

/** The most room a bundled logo takes on the card face; it keeps its own proportions within it. */
private val LogoMaxWidth = 96.dp
private val CompactLogoMaxWidth = 32.dp
private val LogoMaxHeight = 20.dp

@Composable
private fun HandDrawnLogo(logo: BankLogo) {
    when (logo) {
        BankLogo.Hdfc -> LogoRow {
            Canvas(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF004C8F))
                    .border(1.2.dp, White, RoundedCornerShape(4.dp)),
            ) { drawHdfcCross() }
            Gap()
            LogoText("HDFC BANK", 14.5.sp, FontWeight.Black, 0.5.sp)
        }
        BankLogo.Sbi -> LogoRow {
            Canvas(Modifier.size(26.dp).clip(CircleShape).background(Color(0xFF00B4D8))) { drawSbiKeyhole() }
            Gap()
            LogoText("State Bank of India", 14.sp, FontWeight.ExtraBold, 0.2.sp)
        }
        BankLogo.Icici -> LogoRow {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFF37021), Color(0xFFC7222A))))
                    .border(1.2.dp, White.copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { LogoText("i", 17.sp, FontWeight.Black, family = FontFamily.Serif) }
            Gap()
            LogoText("ICICI Bank", 14.5.sp, FontWeight.ExtraBold, 0.3.sp)
        }
        BankLogo.Axis -> LogoRow {
            Canvas(Modifier.size(24.dp)) { drawAxisPyramid() }
            Gap()
            LogoText("AXIS BANK", 14.5.sp, FontWeight.Black, 0.6.sp)
        }
        BankLogo.Kotak -> LogoRow {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFFED1C24))
                    .border(1.2.dp, White.copy(alpha = 0.9f), RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) { LogoText("∞", 18.sp, FontWeight.Black) }
            Gap()
            LogoText("kotak", 15.5.sp, FontWeight.Black, 0.2.sp)
        }
        BankLogo.Amex -> Column(
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0070D2))
                .border(1.2.dp, White, RoundedCornerShape(4.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LogoText("AMERICAN", 9.sp, FontWeight.Black, 0.8.sp)
            LogoText("EXPRESS", 9.sp, FontWeight.Black, 0.8.sp)
        }
        BankLogo.Chase -> LogoRow {
            Canvas(Modifier.size(22.dp)) { drawChaseOctagon() }
            Gap()
            LogoText("CHASE", 15.sp, FontWeight.Black, 1.sp)
        }
        BankLogo.Citi -> Box(Modifier.height(26.dp)) {
            Box(Modifier.padding(top = 4.dp)) { LogoText("citi", 17.sp, FontWeight.ExtraBold, 0.2.sp) }
            Canvas(Modifier.padding(start = 12.dp).size(20.dp, 10.dp)) { drawCitiArc() }
        }
        BankLogo.Boa -> LogoRow {
            Column(Modifier.size(22.dp, 18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFFB3131E)))
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF012169)))
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF012169)))
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFFB3131E)))
                }
            }
            Gap()
            LogoText("Bank of America", 13.5.sp, FontWeight.Bold)
        }
        BankLogo.Hsbc -> LogoRow {
            Canvas(Modifier.size(24.dp)) { drawHsbcHexagon() }
            Gap()
            LogoText("HSBC", 15.sp, FontWeight.Black, 0.8.sp)
        }
        BankLogo.CapitalOne -> LogoRow {
            Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0xFFD03027)), contentAlignment = Alignment.Center) {
                LogoText("1", 13.sp, FontWeight.Black)
            }
            Gap()
            LogoText("Capital One", 14.sp, FontWeight.ExtraBold, 0.2.sp)
        }
        BankLogo.WellsFargo -> Box(
            Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFD71E28))
                .border(1.2.dp, Color(0xFFFFCD41), RoundedCornerShape(3.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Text("WELLS FARGO", style = TextStyle(color = Color(0xFFFFCD41), fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.6.sp))
        }
        BankLogo.Barclays -> LogoRow {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = Color(0xFF00AEEF), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            LogoText("BARCLAYS", 14.sp, FontWeight.Black, 0.8.sp)
        }
        BankLogo.Discover -> LogoRow {
            LogoText("DISC", 15.sp, FontWeight.Black, 0.5.sp)
            Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFFE87722)))
            LogoText("VER", 15.sp, FontWeight.Black, 0.5.sp)
        }
        BankLogo.Pnb -> LogoRow {
            Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xFFFFB81C)), contentAlignment = Alignment.Center) {
                Text("pnb", style = TextStyle(color = Color(0xFF7A1B28), fontWeight = FontWeight.Black, fontSize = 10.sp))
            }
            Gap()
            LogoText("pnb", 15.sp, FontWeight.Black, 0.5.sp)
        }
        BankLogo.Bob -> LogoRow {
            Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xFFEE6A1A)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = White, modifier = Modifier.size(14.dp))
            }
            Gap()
            LogoText("Bank of Baroda", 14.sp, FontWeight.ExtraBold)
        }
        BankLogo.IndusInd -> LogoRow {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF701919))
                    .border(1.2.dp, Color(0xFFD4AF37), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("I", style = TextStyle(color = Color(0xFFD4AF37), fontWeight = FontWeight.Black, fontSize = 13.sp)) }
            Gap()
            LogoText("IndusInd Bank", 14.sp, FontWeight.ExtraBold)
        }
        BankLogo.Yes -> LogoRow {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF004B87))
                    .border(1.5.dp, Color(0xFFE31837), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFFE31837), modifier = Modifier.size(16.dp)) }
            Gap()
            LogoText("YES BANK", 14.sp, FontWeight.Black, 0.5.sp)
        }
        is BankLogo.Text -> LogoText(logo.name, 15.sp, FontWeight.ExtraBold, 0.4.sp)
        is BankLogo.Generic -> {
            val trimmed = logo.bankName.trim()
            LogoRow {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0x66FFFFFF), Color(0x22FFFFFF))))
                        .border(1.2.dp, White.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.AccountBalance, contentDescription = null, tint = White, modifier = Modifier.size(14.dp)) }
                Gap()
                Text(
                    trimmed.ifEmpty { "Wallet Card" },
                    style = TextStyle(color = White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = 0.2.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LogoRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
private fun Gap(size: Dp = 8.dp) {
    Spacer(Modifier.width(size))
}

// ---- Small logo glyphs ----------------------------------------------------------------------

private fun DrawScope.drawHdfcCross() {
    val w = size.width
    val h = size.height
    drawRect(White, Offset(w * 0.29f, h * 0.29f), Size(w * 0.42f, h * 0.42f))
    drawRect(Color(0xFFED232A), Offset(0f, h * 0.38f), Size(w, h * 0.24f))
    drawRect(Color(0xFFED232A), Offset(w * 0.38f, 0f), Size(w * 0.24f, h))
    drawRect(Color(0xFF004C8F), Offset(w * 0.34f, h * 0.34f), Size(w * 0.32f, h * 0.32f))
}

private fun DrawScope.drawSbiKeyhole() {
    val w = size.width
    val h = size.height
    drawCircle(White, w * 0.22f, Offset(w / 2, h / 2))
    val slit = w * 0.12f
    drawRect(White, Offset((w - slit) / 2, h * 0.5f), Size(slit, h * 0.5f))
    drawCircle(Color(0xFF163765), w * 0.12f, Offset(w / 2, h / 2))
}

private fun DrawScope.drawAxisPyramid() {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w / 2, 2f)
        lineTo(w - 2, h - 2)
        lineTo(w * 0.62f, h - 2)
        lineTo(w / 2, h * 0.45f)
        lineTo(w * 0.38f, h - 2)
        lineTo(2f, h - 2)
        close()
    }
    drawPath(path, Color(0xFFE84284))
    drawCircle(White, 2f, Offset(w / 2, h * 0.32f))
}

private fun DrawScope.drawChaseOctagon() {
    val w = size.width
    val h = size.height
    val cx = w / 2
    val cy = h / 2
    for (i in 0 until 4) {
        withTransform({ rotate(i * 90f, Offset(cx, cy)) }) {
            val p = Path().apply {
                moveTo(w * 0.15f, h * 0.35f)
                lineTo(w * 0.35f, h * 0.15f)
                lineTo(w * 0.65f, h * 0.15f)
                lineTo(w * 0.50f, h * 0.35f)
                close()
            }
            drawPath(p, Color(0xFF1170CF))
        }
    }
}

private fun DrawScope.drawCitiArc() {
    val path = Path().apply {
        moveTo(0f, size.height)
        quadraticTo(size.width / 2, -2f, size.width, size.height)
    }
    drawPath(path, Color(0xFFED232A), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawHsbcHexagon() {
    val w = size.width
    val h = size.height
    val red = Color(0xFFDB0011)
    drawPath(Path().apply { moveTo(0f, h / 2); lineTo(w * 0.35f, 0f); lineTo(w * 0.35f, h); close() }, red)
    drawPath(Path().apply { moveTo(w, h / 2); lineTo(w * 0.65f, 0f); lineTo(w * 0.65f, h); close() }, red)
    drawPath(Path().apply { moveTo(w * 0.35f, 0f); lineTo(w * 0.65f, 0f); lineTo(w / 2, h / 2); close() }, White)
    drawPath(Path().apply { moveTo(w * 0.35f, h); lineTo(w * 0.65f, h); lineTo(w / 2, h / 2); close() }, White)
}
