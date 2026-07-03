package com.normpix.cashflow.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.normpix.cashflow.CashflowState
import com.normpix.cashflow.LocalCashTokens
import com.normpix.cashflow.Seed
import com.normpix.cashflow.ThemeMode
import com.normpix.cashflow.dynamicColorAvailable
import com.normpix.cashflow.dynamicSwatch
import com.normpix.cashflow.catList
import com.normpix.cashflow.catSubMap
import com.normpix.cashflow.rememberSmsPermissionRequester
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(state: CashflowState, sysDark: Boolean) {
    when (state.subScreen) {
        "categories" -> CategoriesSubScreen(state)
        "banks" -> BanksSubScreen(state)
        else -> SettingsMain(state, sysDark)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsMain(state: CashflowState, sysDark: Boolean) {
    val t = LocalCashTokens.current
    val requestSmsPermission = rememberSmsPermissionRequester(state)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberGlassSyncedScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = contentBottomPadding(state.showFab)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Settings", fontSize = 22.sp, fontWeight = FontWeight.Medium, color = t.onS,
            modifier = Modifier.padding(top = 8.dp),
        )

        SectionTitle("Appearance")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(t.cont)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Color theme", fontSize = 13.sp, color = t.onSV)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 4,
            ) {
                val seeds = Seed.entries.filter { it != Seed.DYNAMIC || dynamicColorAvailable }
                seeds.forEach { seed ->
                    val sel = state.seed == seed
                    val swatchColor = if (seed == Seed.DYNAMIC) dynamicSwatch(t.dark) ?: seed.swatch else seed.swatch
                    val bg by animateColorAsState(
                        if (sel) swatchColor.copy(alpha = if (t.dark) 0.28f else 0.12f) else t.hi,
                        tween(240, easing = Emphasized), label = "seedBg",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(bg)
                            .border(2.dp, if (sel) swatchColor else Color.Transparent, RoundedCornerShape(18.dp))
                            .clickable { state.setSeedPref(seed) }
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(swatchColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (sel) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Text(seed.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = t.onS)
                    }
                }
            }
            if (state.seed == Seed.DYNAMIC) {
                Text(
                    "Dynamic follows your wallpaper colors, like Pixel system apps (Android 12+).",
                    fontSize = 12.sp, color = t.onSV,
                )
            }
            Text("Mode", fontSize = 13.sp, color = t.onSV)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, t.out, RoundedCornerShape(20.dp)),
            ) {
                val modes = listOf(
                    Triple(ThemeMode.LIGHT, Icons.Rounded.LightMode, "Light"),
                    Triple(ThemeMode.DARK, Icons.Rounded.DarkMode, "Dark"),
                    Triple(ThemeMode.SYSTEM, Icons.Rounded.Contrast, "System"),
                )
                modes.forEachIndexed { i, (mode, icon, name) ->
                    val sel = state.mode == mode
                    if (i > 0) Box(modifier = Modifier.width(1.dp).height(40.dp).background(t.out))
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .background(if (sel) t.sc else Color.Transparent)
                            .clickable { state.setModePref(mode) },
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (sel) Icons.Rounded.Check else icon, null,
                            tint = if (sel) t.onSc else t.onSV, modifier = Modifier.size(16.dp),
                        )
                        Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (sel) t.onSc else t.onSV)
                    }
                }
            }
            if (state.mode == ThemeMode.SYSTEM) {
                Text(
                    "Following your device setting — currently ${if (sysDark) "Dark" else "Light"}. " +
                        "Color theme stays ${state.seed.label}; pick a different one above anytime.",
                    fontSize = 12.sp, color = t.onSV,
                )
            }
        }

        SectionTitle("Navigation bar")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(t.cont)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Glass blur", fontSize = 13.sp, color = t.onSV)
                Text("${state.navBlur.roundToInt()}px", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = t.onS)
            }
            Slider(
                value = state.navBlur,
                onValueChange = { state.setNavBlurPref(it) },
                valueRange = 0f..40f,
                colors = SliderDefaults.colors(
                    thumbColor = t.p, activeTrackColor = t.p, inactiveTrackColor = t.p.copy(alpha = 0.2f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Background opacity", fontSize = 13.sp, color = t.onSV)
                Text("${(state.navOpacity * 100).roundToInt()}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = t.onS)
            }
            Slider(
                value = state.navOpacity,
                onValueChange = { state.setNavOpacityPref(it) },
                valueRange = 0.1f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = t.p, activeTrackColor = t.p, inactiveTrackColor = t.p.copy(alpha = 0.2f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Changes apply live to the floating bar below", fontSize = 12.sp, color = t.onSV)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .height(1.dp)
                    .background(t.out.copy(alpha = 0.5f)),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Floating add button", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.onS)
                    Text(
                        "Hide if you only add via SMS capture", fontSize = 12.sp, color = t.onSV,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                TogglePill(state.showFab) { state.setShowFabPref(!state.showFab) }
            }
        }

        SectionTitle("Automatic capture")
        GroupCard {
            SettingRow(
                Icons.Rounded.Sms, "Read bank SMS",
                if (state.smsPermissionGranted) "Read-only · processed on this device" else "Permission required · tap to allow",
                trailing = {
                    TogglePill(state.smsOn) {
                        if (state.smsPermissionGranted) state.setSmsOnPref(!state.smsOn) else requestSmsPermission()
                    }
                },
                onClick = if (!state.smsPermissionGranted) requestSmsPermission else null,
            )
            SettingRow(
                Icons.Rounded.AccountBalance, "Bank & sender rules", "HDFC, SBI, ICICI + 40 more",
                onClick = { state.subScreen = "banks" },
            )
            SettingRow(
                Icons.Rounded.Category, "Categories", "12 categories · 4 custom",
                onClick = { state.subScreen = "categories" },
            )
        }

        SectionTitle("General")
        GroupCard {
            SettingRow(Icons.Rounded.CurrencyRupee, "Currency", "Indian Rupee (₹)")
            SettingRow(Icons.Rounded.Lock, "Privacy", "Local-first · nothing leaves your device")
            SettingRow(
                Icons.Rounded.Download, "Export data", "CSV or PDF",
                onClick = { state.showToast("Exported expenses.csv (demo)") },
            )
        }

        Text(
            "NormPix 1.0 · 100% on-device", fontSize = 12.sp, color = t.onSV,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun SubHeader(state: CashflowState, title: String, sub: String) {
    val t = LocalCashTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onClick = { state.subScreen = null })
        Column {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = t.onS)
            Text(sub, fontSize = 12.sp, color = t.onSV, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun CategoriesSubScreen(state: CashflowState) {
    val t = LocalCashTokens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberGlassSyncedScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = contentBottomPadding(state.showFab)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SubHeader(state, "Categories", "12 categories · tap to see subcategories")
        GroupCard {
            catList.forEach { cat ->
                val open = state.catExpanded == cat.name
                val subs = catSubMap[cat.name].orEmpty()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(t.hi),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { state.catExpanded = if (open) null else cat.name }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBubble(cat.icon, 42.dp, bg = t.sc, tint = t.onSc, iconSize = 20.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cat.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.onS)
                            Text(
                                cat.subtitle + if (subs.isNotEmpty()) " · ${subs.size} subcategories" else "",
                                fontSize = 12.sp, color = t.onSV, modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Icon(
                            if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            null, tint = t.onSV, modifier = Modifier.size(20.dp),
                        )
                    }
                    if (open) {
                        Row(
                            modifier = Modifier.padding(start = 74.dp, end = 16.dp, bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            subs.forEach { sc2 ->
                                Box(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(t.cont)
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(sc2, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = t.onSV)
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(15.dp))
                                    .dashedBorder(t.out, 15.dp)
                                    .clickable { state.showToast("New subcategory under ${cat.name} (demo)") }
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Add, null, tint = t.p, modifier = Modifier.size(14.dp))
                                Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = t.p)
                            }
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .dashedBorder(t.out, 24.dp)
                .clickable { state.showToast("New category (demo)") },
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, null, tint = t.p, modifier = Modifier.size(20.dp))
            Text("New category", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.p)
        }
    }
}

@Composable
private fun BanksSubScreen(state: CashflowState) {
    val t = LocalCashTokens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberGlassSyncedScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = contentBottomPadding(state.showFab)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SubHeader(state, "Bank & sender rules", "Tap a bank to see its SMS formats")
        GroupCard {
            state.banks.forEachIndexed { bi, b ->
                val open = state.bankExpanded == b.name
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(t.hi),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { state.bankExpanded = if (open) null else b.name },
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconBubble(Icons.Rounded.AccountBalance, 42.dp, bg = t.sc, tint = t.onSc, iconSize = 20.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    b.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.onS,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${b.formats.size} format${if (b.formats.size == 1) "" else "s"} · ${b.parsed}",
                                    fontSize = 12.sp, color = t.onSV, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                            Icon(
                                if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                null, tint = t.onSV, modifier = Modifier.size(20.dp),
                            )
                        }
                        TogglePill(b.on) { state.banks[bi] = b.copy(on = !b.on) }
                    }
                    if (open) {
                        Column(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Sender IDs:", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = t.onSV)
                                b.senders.split(" · ").forEach { sid ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(t.cont)
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text(sid, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = t.onS)
                                    }
                                }
                            }
                            Text(
                                "SMS formats · {placeholders} are extracted",
                                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = t.onSV,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            b.formats.forEachIndexed { fi, f ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(t.cont)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        f, fontSize = 11.5.sp, lineHeight = 18.sp, color = t.onSV,
                                        fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        Icons.Rounded.Close, null, tint = t.onSV,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(16.dp)
                                            .clickable {
                                                state.banks[bi] = b.copy(formats = b.formats.filterIndexed { j, _ -> j != fi })
                                                state.showToast("Format removed")
                                            },
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .dashedBorder(t.out, 14.dp)
                                    .clickable {
                                        state.banks[bi] = b.copy(
                                            formats = b.formats + "{amount} debited from {account} — tap to edit pattern (demo)",
                                        )
                                        state.showToast("Format added — edit the pattern")
                                    },
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Add, null, tint = t.p, modifier = Modifier.size(16.dp))
                                Text("Add SMS format", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = t.p)
                            }
                        }
                    }
                }
            }
        }

        // Add a new rule
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(t.cont)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add a new rule", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.onS)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CashTextField(
                    value = state.newBankName, onValueChange = { state.newBankName = it },
                    placeholder = "Bank name", modifier = Modifier.weight(1.2f),
                    height = 48.dp, cornerRadius = 14.dp, fontSize = 14.sp,
                )
                CashTextField(
                    value = state.newSenderId, onValueChange = { state.newSenderId = it },
                    placeholder = "Sender ID (e.g. VM-KOTAKB)", modifier = Modifier.weight(1.4f),
                    height = 48.dp, cornerRadius = 14.dp, fontSize = 14.sp,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(t.p)
                    .clickable {
                        val nm = state.newBankName.trim()
                        val sid = state.newSenderId.trim().uppercase()
                        if (nm.isEmpty() || sid.isEmpty()) {
                            state.showToast("Enter bank name and sender ID")
                        } else {
                            state.banks.add(com.normpix.cashflow.Bank(nm, sid, "No messages yet", true, emptyList()))
                            state.newBankName = ""; state.newSenderId = ""; state.bankExpanded = nm
                            state.showToast("Rule added — now add its SMS formats")
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, tint = t.onP, modifier = Modifier.size(18.dp))
                Text("Add rule", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.onP)
            }
        }

        // Privacy note
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(t.cont)
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Lock, null, tint = t.onSV, modifier = Modifier.size(18.dp))
            Text(
                "Your SMS messages never leave this device. Processing happens entirely offline.",
                fontSize = 12.sp, color = t.onSV, lineHeight = 16.sp
            )
        }
    }
}
