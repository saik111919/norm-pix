package com.example.normpix.wallet

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.normpix.ui.components.SvgAsset
import com.example.normpix.ui.theme.Appearance
import com.example.normpix.ui.theme.NormPixTheme
import java.io.File
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** TEMPORARY visual QA harness for the bank-logo work; renders to app/build/qa_banklogo. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xxhdpi")
class BankLogoQaRender {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val out = File("build/qa_banklogo").also { it.mkdirs() }
    private val tag = System.getProperty("qa.tag") ?: System.getenv("QA_TAG") ?: "before"

    private fun save(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$tag-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun assets() {
        val files = File("src/main/assets/banks").list()!!.filter { !it.contains("_symbol") }.sorted()
        compose.setContent {
            Column(Modifier.background(Color(0xFF1E2A3A)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (file in files) {
                    val code = file.removeSuffix(".svg")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(code, color = Color.White, fontSize = 9.sp, modifier = Modifier.width(30.dp))
                        // The slots the card face used: 96x20 wordmark, 20x20 symbol, on the white chip.
                        Box(Modifier.background(Color.White).padding(horizontal = 10.dp, vertical = 6.dp)) {
                            SvgAsset("banks/$file", Modifier.height(20.dp).width(96.dp).border(0.5.dp, Color.Magenta))
                        }
                        Box(Modifier.width(8.dp))
                        Box(Modifier.background(Color.White).padding(6.dp)) {
                            SvgAsset("banks/${code}_symbol.svg", Modifier.size(20.dp).border(0.5.dp, Color.Magenta))
                        }
                        Box(Modifier.width(8.dp))
                        Box(Modifier.background(Color.White).padding(6.dp)) {
                            SvgAsset("banks/$file", Modifier.height(40.dp).width(200.dp).border(0.5.dp, Color.Magenta))
                        }
                    }
                }
            }
        }
        save("assets")
    }

    @Test
    fun cards() {
        val banks = listOf(
            "HDFC Bank" to CardKind.Debit, "ICICI Bank" to CardKind.Credit, "HDFC BANK LTD" to CardKind.Credit,
            "ICICI BANK LIMITED" to CardKind.Debit, "SBI" to CardKind.Debit, "Axis Bank" to CardKind.Credit,
            "Kotak" to CardKind.Credit, "Yes Bank" to CardKind.Debit, "IndusInd Bank" to CardKind.Credit,
            "IDFC FIRST Bank" to CardKind.Credit, "Federal Bank" to CardKind.Debit, "Bank of Baroda" to CardKind.Debit,
            "Kisan Co-op Bank" to CardKind.Debit, "HSBC" to CardKind.Credit,
        )
        for ((style, compact) in listOf("full" to false, "compact" to true)) {
            compose.activityRule.scenario.recreate()
            compose.setContent {
                NormPixTheme(Appearance()) {
                    Column(Modifier.background(Color.White).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (pair in banks.chunked(2)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for ((bank, kind) in pair) {
                                    Box(Modifier.width(193.dp)) { QaCardFace(bank, kind, compact) }
                                }
                            }
                        }
                    }
                }
            }
            save("cards-$style")
        }
    }
}

@androidx.compose.runtime.Composable
private fun QaCardFace(bank: String, kind: CardKind, compact: Boolean) {
    val card = StoredCard("x", bank, "Asha Rao", "4012888888881881", "08", "29", "123", kind, LocalDateTime.of(2026, 1, 1, 0, 0))
    // The real card is ~2x this width; scale through density so the face lays out at its real size.
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density / 2f, 1f),
    ) {
        if (compact) {
            WalletCardView(
                card, revealed = false, actionStyle = com.example.normpix.settings.WalletCardActionStyle.Bar,
                onToggleReveal = {}, onEdit = {}, onDelete = {}, bankIconStyle = com.example.normpix.settings.BankIconStyle.Compact,
            )
        } else {
            WalletCardFacePreview(card, revealed = false)
        }
    }
}
