package com.example.normpix.about.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.normpix.about.GuideTopic
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.components.AppSubPage

/** How many topic cards sit side by side in the Feature guide. */
internal const val GUIDE_CARDS_PER_ROW = 2

/** What the guide is called: the About page's pill to it, and its own page. Until 2026-11-01 a heading on About, "How NormPix works". */
internal const val GUIDE_TITLE = "Feature guide"

/** What the Feature guide's page says above its cards. */
internal const val GUIDE_INTRO = "How each part of NormPix works, step by step, with a picture of it."

/**
 * The Feature guide, behind a pill on the About page: every [GuideTopic] as a card - a picture of the feature with its name
 * ([GuideCard]) - that opens the topic's page ([GuidePage]) with the steps.
 */
@Composable
fun GuideIndexPage() {
    val navigator = LocalNavigator.current
    AppSubPage(title = GUIDE_TITLE, onBack = navigator::back) {
        item(key = "body") {
            Column(Modifier.padding(AppSpacing.page)) {
                Text(
                    GUIDE_INTRO,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = AppSpacing.xs, end = AppSpacing.xs, bottom = AppSpacing.lg),
                )
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                    for (row in GuideTopic.entries.chunked(GUIDE_CARDS_PER_ROW)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                            for (topic in row) {
                                GuideCard(topic, onClick = { navigator.openGuide(topic) }, modifier = Modifier.weight(1f))
                            }
                            // A last, lone card is as wide as the others rather than stretched across the row.
                            repeat(GUIDE_CARDS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/** A topic in the Feature guide: its picture above its name. The whole card opens the topic. */
@Composable
private fun GuideCard(topic: GuideTopic, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = AppRadius.rXl,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            GuideArt(topic, shape = RectangleShape)
            Text(
                topic.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
            )
        }
    }
}
