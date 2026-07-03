package com.normpix.ui.floatingnav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Demo: colorful scrollable content with [FloatingGlassNavBar] floating above it.
 * The content is tagged with [glassSource] so the bar blurs whatever scrolls beneath.
 * Edge-to-edge friendly: the bar is padded by the navigation-bar insets, and the
 * list scrolls behind both the bar and the status bar.
 */
@Composable
fun FloatingNavBarDemoScreen(modifier: Modifier = Modifier) {
    val glassState = rememberGlassBackdropState()
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val items = remember {
        listOf(
            FloatingNavItem("Home", Icons.Outlined.Home, Icons.Filled.Home),
            FloatingNavItem("Search", Icons.Outlined.Search, Icons.Filled.Search),
            FloatingNavItem("Favorites", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
            FloatingNavItem("Profile", Icons.Outlined.Person, Icons.Filled.Person),
        )
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .glassSource(glassState), // <- content the bar blurs
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = bottomInset + 112.dp, // clears the floating bar
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(Modifier.statusBarsPadding().padding(top = 12.dp, bottom = 8.dp)) {
                    Text("NormPix", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Your photos, kept local.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(count = DemoGradients.size * 2) { index ->
                val (start, end) = DemoGradients[index % DemoGradients.size]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (index % 3 == 0) 200.dp else 150.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(start, end))),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Text(
                        "Moment ${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        FloatingGlassNavBar(
            items = items,
            selectedIndex = selectedIndex,
            onItemSelected = { selectedIndex = it },
            glassState = glassState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 16.dp),
        )
    }
}

private val DemoGradients = listOf(
    Color(0xFF7C4DFF) to Color(0xFF448AFF),
    Color(0xFFFF6E7F) to Color(0xFFFFB199),
    Color(0xFF00C9A7) to Color(0xFF92FE9D),
    Color(0xFFF77062) to Color(0xFFFE5196),
    Color(0xFF4FACFE) to Color(0xFF00F2FE),
    Color(0xFFFA709A) to Color(0xFFFEE140),
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun FloatingNavBarDemoPreview() {
    MaterialTheme {
        FloatingNavBarDemoScreen()
    }
}

/*
 * Hooking it up in an Activity (edge-to-edge, dynamic color):
 *
 * class MainActivity : ComponentActivity() {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         enableEdgeToEdge()
 *         setContent {
 *             val dark = isSystemInDarkTheme()
 *             val colorScheme = when {
 *                 Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(this)
 *                 Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(this)
 *                 dark -> darkColorScheme()
 *                 else -> lightColorScheme()
 *             }
 *             MaterialTheme(colorScheme = colorScheme) {
 *                 FloatingNavBarDemoScreen()
 *             }
 *         }
 *     }
 * }
 */
