package com.normpix.cashflow.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DonutSmall
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import com.normpix.cashflow.addCategoryIcons
import com.normpix.cashflow.catIcons
import com.normpix.cashflow.catList
import com.normpix.cashflow.currencies
import com.normpix.cashflow.fmt
import com.normpix.cashflow.langList
import com.normpix.cashflow.rememberSmsPermissionRequester
import com.normpix.cashflow.seedToneColor
import kotlin.math.abs
import kotlin.math.roundToInt

/** Top-right diagonal half of a shape (design's `clipPath: polygon(0 0, 100% 0, 100% 100%)`),
 * used to render a seed's accent color over its lighter "tone" base in the theme swatch. */
private val diagonalSplit = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height)
    close()
}

/** The 4 extended categories that map onto a dashboard/transactions category key, for "View entries". */
private val catDashboardKey = mapOf(
    "Food & dining" to "Food",
    "Shopping" to "Shopping",
    "Transport" to "Transport",
    "Bills & utilities" to "Bills",
)

@Composable
fun SettingsScreen(state: CashflowState, sysDark: Boolean) {
    when (state.subScreen) {
        "categories" -> CategoriesSubScreen(state)
        "banks" -> BanksSubScreen(state)
        "profile" -> ProfileSubScreen(state)
        "appearance" -> AppearanceSubScreen(state, sysDark)
        "language" -> LanguageSubScreen(state)
        "currency" -> CurrencySubScreen(state)
        "accounts" -> AccountsSubScreen(state)
        "budgets" -> BudgetsSubScreen(state)
        "smartrules" -> SmartRulesSubScreen(state)
        "privacy" -> PrivacySubScreen(state)
        "notifications" -> NotificationsSubScreen(state)
        "reminders" -> RemindersSubScreen(state)
        "appExceptions" -> AppExceptionsSubScreen(state)
        "syncedMessages" -> SyncedMessagesSubScreen(state)
        "webhooks" -> WebhooksSubScreen(state)
        "about" -> AboutSubScreen(state)
        else -> SettingsMain(state)
    }
}

@Composable
private fun SettingsMain(state: CashflowState) {
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

        // Profile card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(t.pc)
                .clickable { state.subScreen = "profile" }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(t.p),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.profileName.trim().take(1).uppercase().ifEmpty { "N" },
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = t.onP,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.profileName, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = t.onPc,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${state.txns.size} transactions logged", fontSize = 12.sp,
                    color = t.onPc.copy(alpha = 0.8f), modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = t.onPc, modifier = Modifier.size(20.dp))
        }

        SectionTitle("Preferences")
        GroupCard {
            SettingRow(
                Icons.Rounded.Palette, "Appearance", "Theme, color & navigation bar",
                onClick = { state.subScreen = "appearance" },
            )
            SettingRow(Icons.Rounded.Translate, "Language", state.language, onClick = { state.subScreen = "language" })
            SettingRow(
                Icons.Rounded.CurrencyRupee, "Currency",
                if (state.currency.code == "INR") "Indian Rupee (₹)" else state.currency.label,
                onClick = { state.subScreen = "currency" },
            )
        }

        SectionTitle("Manage")
        GroupCard {
            SettingRow(
                Icons.Rounded.AccountBalance, "Accounts", "${state.accounts.size} linked",
                onClick = { state.subScreen = "accounts" },
            )
            SettingRow(
                Icons.Rounded.DonutSmall, "Budgets", "${state.budgetCaps.size} category budgets",
                onClick = { state.subScreen = "budgets" },
            )
            SettingRow(
                Icons.Rounded.Category, "Categories", "12 categories · 4 custom",
                onClick = { state.subScreen = "categories" },
            )
            SettingRow(
                Icons.Rounded.AutoAwesome, "Smart rules", "${state.smartRules.size} auto-categorize rules",
                onClick = { state.subScreen = "smartrules" },
            )
            SettingRow(
                Icons.Rounded.Lock, "Data & privacy", "Local-first · export or erase",
                onClick = { state.subScreen = "privacy" },
            )
        }

        SectionTitle("More")
        GroupCard {
            SettingRow(
                Icons.Rounded.Notifications, "Notifications", "Budget alerts, summaries & reminders",
                onClick = { state.subScreen = "notifications" },
            )
            SettingRow(
                Icons.Rounded.Sms, "SMS parsing",
                if (state.smsPermissionGranted) "Read-only · processed on this device" else "Permission required · tap to allow",
                trailing = {
                    TogglePill(state.smsOn) {
                        if (state.smsPermissionGranted) state.setSmsOnPref(!state.smsOn) else requestSmsPermission()
                    }
                },
                onClick = { if (!state.smsPermissionGranted) requestSmsPermission() else state.subScreen = "banks" },
            )
            if (state.devMode) {
                SettingRow(
                    Icons.Rounded.Link, "Webhooks", "${state.webhooks.size} endpoint(s) configured",
                    onClick = { state.subScreen = "webhooks" },
                )
            }
        }

        GroupCard {
            SettingRow(Icons.Rounded.Info, "About", "Version, licenses & credits", onClick = { state.subScreen = "about" })
        }

        Text(
            "NormPix 1.0 · 100% on-device", fontSize = 12.sp, color = t.onSV,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
}

@Composable
internal fun SubHeader(state: CashflowState, title: String, sub: String? = null) {
    val t = LocalCashTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onClick = { state.subScreen = null })
        Column {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = t.onS)
            if (sub != null) Text(sub, fontSize = 12.sp, color = t.onSV, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
internal fun subScreenModifier(state: CashflowState) = Modifier
    .fillMaxSize()
    .verticalScroll(rememberGlassSyncedScrollState())
    .statusBarsPadding()
    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = contentBottomPadding(state.showFab))

@Composable
private fun ProfileSubScreen(state: CashflowState) {
    val t = LocalCashTokens.current
    val seeds = listOf(Triple("green", "Forest", Seed.GREEN.swatch), Triple("blue", "Ocean", Seed.BLUE.swatch), Triple("purple", "Orchid", Seed.PURPLE.swatch))
    Column(modifier = subScreenModifier(state), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SubHeader(state, "Profile", "Shown across NormPix")

        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(84.dp).clip(CircleShape).background(t.p),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.profileName.trim().take(1).uppercase().ifEmpty { "N" },
                    fontSize = 32.sp, fontWeight = FontWeight.SemiBold, color = t.onP,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                seeds.forEach { (key, _, color) ->
                    val sel = state.profileColor == key
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(3.dp, if (sel) t.onS else Color.Transparent, CircleShape)
                            .clickable { state.setProfileColorPref(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (sel) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(t.cont).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Display name", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = t.onSV)
            CashTextField(
                value = state.profileName, onValueChange = { state.setProfileNamePref(it) },
                placeholder = "Your name", modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp,
            )
        }

        GroupCard {
            SettingRow(Icons.AutoMirrored.Rounded.ReceiptLong, "Transactions logged", "${state.txns.size} total")
            SettingRow(Icons.Rounded.EventRepeat, "Member since", "July 2026")
        }
    }
}

@Composable
private fun AppearanceSubScreen(state: CashflowState, sysDark: Boolean) {
    val t = LocalCashTokens.current
    Column(modifier = subScreenModifier(state), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SubHeader(state, "Appearance", "Theme, color & navigation bar")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(t.cont)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Color theme", fontSize = 13.sp, color = t.onSV)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val seeds = Seed.entries.filter { it != Seed.DYNAMIC || dynamicColorAvailable }
                seeds.forEach { seed ->
                    val sel = state.seed == seed
                    val swatchColor = if (seed == Seed.DYNAMIC) dynamicSwatch(t.dark) ?: seed.swatch else seed.swatch
                    val tone = if (seed == Seed.DYNAMIC) swatchColor else seedToneColor(seed)
                    val bg by animateColorAsState(
                        if (sel) swatchColor.copy(alpha = if (t.dark) 0.28f else 0.12f) else t.hi,
                        tween(240, easing = Emphasized), label = "seedBg",
                    )
                    val lift by animateDpAsState(if (sel) (-2).dp else 0.dp, tween(240, easing = Emphasized), label = "seedLift")
                    Column(
                        modifier = Modifier
                            .width(92.dp)
                            .offset(y = lift)
                            .clip(RoundedCornerShape(22.dp))
                            .background(bg)
                            .border(2.dp, if (sel) swatchColor else Color.Transparent, RoundedCornerShape(22.dp))
                            .clickable { state.setSeedPref(seed) }
                            .padding(top = 16.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .then(if (sel) Modifier.shadow(6.dp, CircleShape, spotColor = swatchColor.copy(alpha = 0.4f)) else Modifier)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(modifier = Modifier.fillMaxSize().background(tone))
                            Box(modifier = Modifier.fillMaxSize().clip(diagonalSplit).background(swatchColor))
                            if (sel) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Text(
                            seed.label, fontSize = 12.5.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (sel) t.onS else t.onSV,
                        )
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

        SectionTitle("Sheets & overlays")
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
                Text("${state.sheetBlur.roundToInt()}px", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = t.onS)
            }
            Slider(
                value = state.sheetBlur,
                onValueChange = { state.setSheetBlurPref(it) },
                valueRange = 0f..40f,
                colors = SliderDefaults.colors(
                    thumbColor = t.p, activeTrackColor = t.p, inactiveTrackColor = t.p.copy(alpha = 0.2f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Background opacity", fontSize = 13.sp, color = t.onSV)
                Text("${(state.sheetOpacity * 100).roundToInt()}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = t.onS)
            }
            Slider(
                value = state.sheetOpacity,
                onValueChange = { state.setSheetOpacityPref(it) },
                valueRange = 0.1f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = t.p, activeTrackColor = t.p, inactiveTrackColor = t.p.copy(alpha = 0.2f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Applies to the Add screen and Edit/confirm sheets", fontSize = 12.sp, color = t.onSV)
        }
    }
}

@Composable
private fun LanguageSubScreen(state: CashflowState) {
    val t = LocalCashTokens.current
    Column(modifier = subScreenModifier(state), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SubHeader(state, "Language", "Used across menus and messages")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(t.cont)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Translate, null, tint = t.onSV, modifier = Modifier.size(18.dp))
            Text(
                "Other languages are coming in a future update. NormPix only supports English for now.",
                fontSize = 12.sp, color = t.onSV, lineHeight = 16.sp,
            )
        }
        GroupCard {
            langList.forEach { l ->
                val sel = state.language == l
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(t.hi)
                        .then(if (sel) Modifier else Modifier.alpha(0.4f))
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(l, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.onS, modifier = Modifier.weight(1f))
                    if (sel) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = t.p, modifier = Modifier.size(22.dp))
                    } else {
                        Box(modifier = Modifier.size(22.dp).clip(CircleShape).border(2.dp, t.out, CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrencySubScreen(state: CashflowState) {
    val t = LocalCashTokens.current
    Column(modifier = subScreenModifier(state), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SubHeader(state, "Currency", "Used for all amounts in NormPix")
        GroupCard {
            currencies.forEach { c ->
                val sel = state.currency.code == c.code
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(t.hi)
                        .clickable { state.setCurrencyByCode(c.code) }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(t.sc),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(c.symbol, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = t.onSc)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(c.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.onS)
                        Text(c.code, fontSize = 12.sp, color = t.onSV, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (sel) Icon(Icons.Rounded.CheckCircle, null, tint = t.p, modifier = Modifier.size(22.dp))
                }
            }
        }
        Text(
            "Amounts in this preview always display in ₹.", fontSize = 12.sp, color = t.onSV,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun AccountsSubScreen(state: CashflowState) {
    val t = LocalCashTokens.current
    Column(modifier = subScreenModifier(state), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SubHeader(state, "Accounts", "${state.accounts.size} linked")
        GroupCard {
            state.accounts.forEachIndexed { i, a ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(t.hi)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBubble(a.icon, 42.dp, bg = t.sc, tint = t.onSc, iconSize = 20.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            a.name + (a.mask?.let { " $it" } ?: ""), fontSize = 15.sp,
                            fontWeight = FontWeight.Medium, color = t.onS,
                        )
                        Text(
                            "${a.type} · ${if (a.balance < 0) "−" else ""}${fmt(abs(a.balance), state.currency)}",
                            fontSize = 12.sp, color = t.onSV, modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    TogglePill(a.on) { state.toggleAccount(i) }
                }
            }
        }
        if (state.addingAccount) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(t.cont)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Link account", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.onS)
                CashTextField(
                    value = state.newAccountName, onValueChange = { state.newAccountName = it },
                    placeholder = "Account name (e.g. Kotak Bank)", modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Savings", "Credit card", "Cash").forEach { type ->
                        ChoiceChip(type, state.newAccountType == type, { state.newAccountType = type })
                    }
                }
                CashTextField(
                    value = state.newAccountBalance, onValueChange = { state.newAccountBalance = it },
                    placeholder = "Current balance", modifier = Modifier.fillMaxWidth(),
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .border(1.dp, t.out, RoundedCornerShape(22.dp))
                            .clickable { state.cancelAddAccount() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.p)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(t.p)
                            .clickable { state.addAccount() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Link", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.onP)
                    }
                }
            }
        } else {
            PrimaryCtaButton(Icons.Rounded.Add, "Link account", onClick = { state.startAddAccount() })
        }
    }
}

@Composable
private fun BudgetsSubScreen(state: CashflowState) {
    val t = LocalCashTokens.current
    val orderedKeys = listOf("Food", "Shopping", "Transport", "Bills").filter { it in state.budgetCaps } +
        state.budgetCaps.keys.filter { it !in listOf("Food", "Shopping", "Transport", "Bills") }
    Column(modifier = subScreenModifier(state), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SubHeader(state, "Budgets", "Monthly caps by category")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            orderedKeys.forEach { key ->
                val name = com.normpix.cashflow.catDisplayName[key] ?: key
                val icon = catIcons[key] ?: (catList.find { it.name == key }?.icon ?: Icons.Rounded.DonutSmall)
                val cap = state.budgetCaps.getValue(key)
                val spent = state.categorySpent(key)
                val pct = if (cap > 0) ((spent / cap) * 100).toInt() else 0
                val editing = state.editingBudgetFor == key
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(t.cont)
                        .padding(16.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = t.onSV, modifier = Modifier.size(20.dp))
                        Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.onS, modifier = Modifier.weight(1f))
                        if (editing) {
                            CashTextField(
                                value = state.editBudgetVal, onValueChange = { state.editBudgetVal = it },
                                placeholder = "Amount", modifier = Modifier.width(96.dp),
                                height = 36.dp, cornerRadius = 12.dp, fontSize = 13.sp,
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            )
                            Icon(
                                Icons.Rounded.Check, null, tint = t.p,
                                modifier = Modifier.size(18.dp).clickable { state.saveEditBudget() },
                            )
                            Icon(
                                Icons.Rounded.Close, null, tint = t.onSV,
                                modifier = Modifier.size(18.dp).clickable { state.cancelEditBudget() },
                            )
                        } else {
                            Text("${fmt(cap, state.currency)}/mo", fontSize = 13.sp, color = t.onSV)
                            Icon(
                                Icons.Rounded.Edit, null, tint = t.p,
                                modifier = Modifier.size(18.dp).clickable { state.startEditBudget(key) },
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(t.p.copy(alpha = 0.15f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((pct.coerceAtMost(100)) / 100f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (pct > 100) t.err else t.p),
                        )
                    }
                }
            }
        }
        if (state.addingBudget) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(t.cont)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("New budget", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.onS)
                val avail = state.availableBudgetCats()
                if (avail.isEmpty()) {
                    Text("All categories already have a budget.", fontSize = 13.sp, color = t.onSV)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        avail.forEach { (key, name) ->
                            ChoiceChip(name, state.newBudgetKey == key, { state.newBudgetKey = key })
                        }
                    }
                    CashTextField(
                        value = state.newBudgetVal, onValueChange = { state.newBudgetVal = it },
                        placeholder = "Monthly cap amount", modifier = Modifier.fillMaxWidth(),
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .border(1.dp, t.out, RoundedCornerShape(22.dp))
                            .clickable { state.cancelAddBudget() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.p)
                    }
                    if (avail.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(t.p)
                                .clickable { state.saveAddBudget() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Add", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.onP)
                        }
                    }
                }
            }
        } else {
            PrimaryCtaButton(Icons.Rounded.Add, "New budget", onClick = { state.startAddBudget() })
        }
    }
}

@Composable
private fun SmartRulesSubScreen(state: CashflowState) {
    val t = LocalCashTokens.current
    Column(modifier = subScreenModifier(state), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SubHeader(state, "Smart rules", "Auto-categorize transactions by merchant keyword")
        GroupCard {
            state.smartRules.forEach { r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(t.hi)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBubble(catIcons[r.cat] ?: Icons.Rounded.Category, 42.dp, bg = t.sc, tint = t.onSc, iconSize = 20.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            r.match, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.onS,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text("→ ${r.cat}", fontSize = 12.sp, color = t.onSV, modifier = Modifier.padding(top = 2.dp))
                    }
                    Icon(
                        Icons.Rounded.Close, null, tint = t.onSV,
                        modifier = Modifier.size(18.dp).clickable { state.removeSmartRule(r.id) },
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(t.cont)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add a rule", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.onS)
            CashTextField(
                value = state.newRuleMatch, onValueChange = { state.newRuleMatch = it },
                placeholder = "Merchant keywords, comma separated", modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Food", "Shopping", "Transport", "Bills").forEach { c ->
                    ChoiceChip(c, state.newRuleCat == c, { state.newRuleCat = c }, icon = catIcons[c])
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(t.p)
                    .clickable { state.addSmartRule() },
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, tint = t.onP, modifier = Modifier.size(18.dp))
                Text("Add rule", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.onP)
            }
        }
    }
}

/** Launches the system document picker and imports the chosen CSV into [state]. */
@Composable
private fun rememberCsvImportLauncher(state: CashflowState): () -> Unit {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) state.importCsv(uri) }
    return { launcher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) }
}

@Composable
private fun PrivacySubScreen(state: CashflowState) {
    val t = LocalCashTokens.current
    val importCsv = rememberCsvImportLauncher(state)
    Column(modifier = subScreenModifier(state), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SubHeader(state, "Data & privacy", "Everything stays on this device")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(t.cont)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Lock, null, tint = t.onSV, modifier = Modifier.size(18.dp))
            Text(
                "NormPix never sends your transactions, SMS content, or account data off this device.",
                fontSize = 12.sp, color = t.onSV, lineHeight = 16.sp,
            )
        }
        GroupCard {
            SettingRow(Icons.Rounded.Download, "Export data", "CSV format", onClick = { state.export() })
            SettingRow(Icons.Rounded.Upload, "Import data", "From CSV backup", onClick = { importCsv() })
            SettingRow(
                Icons.Rounded.History, "Auto-backup", "Weekly, kept on-device",
                trailing = { TogglePill(state.autoBackup) { state.toggleAutoBackup() } },
            )
        }
        GroupCard {
            SettingRow(
                Icons.Rounded.DeleteForever, "Erase all data", "This cannot be undone",
                onClick = { state.eraseAllData() },
            )
        }
    }
}

@Composable
private fun NotificationsSubScreen(state: CashflowState) {
    Column(modifier = subScreenModifier(state), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SubHeader(state, "Notifications", "Choose what NormPix can notify you about")
        GroupCard {
            SettingRow(
                Icons.Rounded.NotificationsActive, "Budget alerts", "When a category nears its cap",
                trailing = { TogglePill(state.notifPrefs.budget) { state.toggleNotif("budget") } },
            )
            SettingRow(
                Icons.Rounded.Summarize, "Weekly summary", "Every Monday morning",
                trailing = { TogglePill(state.notifPrefs.weekly) { state.toggleNotif("weekly") } },
            )
            SettingRow(
                Icons.Rounded.EventRepeat, "Bill reminders", "2 days before a due date",
                trailing = { TogglePill(state.notifPrefs.bills) { state.toggleNotif("bills") } },
            )
            SettingRow(
                Icons.Rounded.Sms, "New SMS detected", "When a bank message needs review",
                trailing = { TogglePill(state.notifPrefs.sms) { state.toggleNotif("sms") } },
            )
        }
        GroupCard {
            val activeCount = state.reminders.count { it.on }
            SettingRow(
                Icons.Rounded.Alarm, "Manage reminders",
                "$activeCount of ${state.reminders.size} active · time, frequency & alert style",
                onClick = { state.subScreen = "reminders" },
            )
        }
    }
}

@Composable
private fun WebhooksSubScreen(state: CashflowState) {
    val t = LocalCashTokens.current
    Column(modifier = subScreenModifier(state), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SubHeader(state, "Webhooks", "Send events to your own endpoint")
        GroupCard {
            state.webhooks.forEach { w ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(t.hi)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBubble(Icons.Rounded.Link, 42.dp, bg = t.sc, tint = t.onSc, iconSize = 20.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            w.url, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = t.onS,
                            fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(w.event, fontSize = 12.sp, color = t.onSV, modifier = Modifier.padding(top = 2.dp))
                    }
                    TogglePill(w.on) { state.toggleWebhook(w.id) }
                    Icon(
                        Icons.Rounded.Close, null, tint = t.onSV,
                        modifier = Modifier.size(18.dp).clickable { state.removeWebhook(w.id) },
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(t.cont)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add endpoint", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.onS)
            CashTextField(
                value = state.newWebhookUrl, onValueChange = { state.newWebhookUrl = it },
                placeholder = "https://your-server.com/hook", modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(t.p)
                    .clickable { state.addWebhook() },
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, tint = t.onP, modifier = Modifier.size(18.dp))
                Text("Add webhook", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.onP)
            }
        }
    }
}

@Composable
private fun AboutSubScreen(state: CashflowState) {
    val t = LocalCashTokens.current
    Column(modifier = subScreenModifier(state), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SubHeader(state, "About")
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(t.p),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.CurrencyRupee, null, tint = t.onP, modifier = Modifier.size(32.dp))
            }
            Text("NormPix", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = t.onS)
            Text("Version ${state.currentVersionName} · 100% on-device", fontSize = 12.sp, color = t.onSV)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(t.cont)
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (state.rating > 0) "You rated NormPix ${state.rating} star${if (state.rating == 1) "" else "s"}" else "Enjoying NormPix?",
                fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.onS,
            )
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (i in 1..5) {
                    Icon(
                        Icons.Rounded.StarRate, null,
                        tint = if (i <= state.rating) t.p else t.onSV.copy(alpha = 0.3f),
                        modifier = Modifier.size(28.dp).clickable { state.rate(i) },
                    )
                }
            }
        }
        GroupCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(t.hi),
            ) {
                SettingRow(
                    Icons.Rounded.Description, "Licenses", "Open-source software",
                    onClick = { state.aboutExpanded = if (state.aboutExpanded == "licenses") null else "licenses" },
                )
                if (state.aboutExpanded == "licenses") {
                    Text(
                        "NormPix is built with Jetpack Compose, Kotlin coroutines, and AndroidX libraries, " +
                            "all licensed under the Apache License 2.0. Full license texts for every " +
                            "third-party library are bundled with the app.",
                        fontSize = 12.sp, color = t.onSV, lineHeight = 17.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(t.hi),
            ) {
                SettingRow(
                    Icons.Rounded.Policy, "Privacy policy", "Read how we handle data",
                    onClick = { state.aboutExpanded = if (state.aboutExpanded == "privacy") null else "privacy" },
                )
                if (state.aboutExpanded == "privacy") {
                    Text(
                        "NormPix processes every transaction, SMS message, and account balance entirely on " +
                            "this device. Nothing is uploaded, synced, or shared with NormPix or any third " +
                            "party. Uninstalling the app permanently deletes all local data.",
                        fontSize = 12.sp, color = t.onSV, lineHeight = 17.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    )
                }
            }
        }
        GroupCard {
            SettingRow(
                Icons.Rounded.Code, "Developer mode",
                if (state.devMode) "Unlocks webhooks & sync" else "Off",
                trailing = { TogglePill(state.devMode) { state.toggleDevMode() } },
            )
        }
    }
}

@Composable
private fun CategoriesSubScreen(state: CashflowState) {
    val t = LocalCashTokens.current
    Column(modifier = subScreenModifier(state), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SubHeader(state, "Categories", "12 categories · tap to see subcategories")
        GroupCard {
            state.categories.forEach { cat ->
                val open = state.catExpanded == cat.name
                val subs = state.subCategories[cat.name].orEmpty()
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
                            catDashboardKey[cat.name]?.let { key ->
                                Row(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(t.sc)
                                        .clickable { state.subScreen = null; state.catDetail = key }
                                        .padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.ReceiptLong, null, tint = t.onSc, modifier = Modifier.size(14.dp))
                                    Text("View entries", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = t.onSc)
                                }
                            }
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
                            if (state.addingSubFor != cat.name) {
                                Row(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(15.dp))
                                        .dashedBorder(t.out, 15.dp)
                                        .clickable { state.startAddSubcat(cat.name) }
                                        .padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.Add, null, tint = t.p, modifier = Modifier.size(14.dp))
                                    Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = t.p)
                                }
                            }
                        }
                        if (state.addingSubFor == cat.name) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 74.dp, end = 16.dp, bottom = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CashTextField(
                                    value = state.newSubCatName, onValueChange = { state.newSubCatName = it },
                                    placeholder = "Subcategory name", modifier = Modifier.weight(1f),
                                    height = 36.dp, cornerRadius = 14.dp, fontSize = 13.sp,
                                )
                                Icon(
                                    Icons.Rounded.Check, null, tint = t.p,
                                    modifier = Modifier.size(20.dp).clickable { state.saveNewSubcat() },
                                )
                                Icon(
                                    Icons.Rounded.Close, null, tint = t.onSV,
                                    modifier = Modifier.size(20.dp).clickable { state.cancelAddSubcat() },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (state.addingCategory) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(t.cont)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("New category", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.onS)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    addCategoryIcons.forEachIndexed { i, icon ->
                        val sel = state.newCatIconIdx == i
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (sel) t.sc else t.hi)
                                .border(2.dp, if (sel) t.p else Color.Transparent, CircleShape)
                                .clickable { state.newCatIconIdx = i },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon, null, tint = if (sel) t.onSc else t.onSV, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                CashTextField(
                    value = state.newCatName, onValueChange = { state.newCatName = it },
                    placeholder = "Category name", modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .border(1.dp, t.out, RoundedCornerShape(22.dp))
                            .clickable { state.cancelAddCategory() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.p)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(t.p)
                            .clickable { state.saveNewCategory() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Save", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.onP)
                    }
                }
            }
        } else {
            PrimaryCtaButton(Icons.Rounded.Add, "New category", onClick = { state.startAddCategory() })
        }
    }
}

@Composable
private fun BanksSubScreen(state: CashflowState) {
    val t = LocalCashTokens.current
    Column(modifier = subScreenModifier(state), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                            if (state.addingFormatFor == b.name) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CashTextArea(
                                        value = state.newFormatText, onValueChange = { state.newFormatText = it },
                                        placeholder = "e.g. Rs.{amount} debited from a/c **{last4} on {date}",
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                                .clip(RoundedCornerShape(20.dp))
                                                .border(1.dp, t.out, RoundedCornerShape(20.dp))
                                                .clickable { state.cancelAddFormat() },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text("Cancel", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = t.p)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(t.p)
                                                .clickable { state.saveNewFormat() },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text("Save", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = t.onP)
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .dashedBorder(t.out, 14.dp)
                                        .clickable { state.startAddFormat(b.name) },
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
