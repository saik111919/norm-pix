package com.example.normpix.finance.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DesignServices
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VideoCall
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.material.icons.outlined.Widgets

/**
 * Authentic brand SVG icons for OTT, streaming, AI, dev, gaming, and cloud subscription services.
 * Assets live offline in `assets/brands/<name>.svg`.
 */
val brandCategoryIcons: Map<String, CategoryIconSpec> = mapOf(
    // Video & OTT
    "netflix" to CategoryIconSpec(Icons.Outlined.Movie, "Netflix", "🍿", "brands/netflix.svg"),
    "youtube" to CategoryIconSpec(Icons.Outlined.SmartDisplay, "YouTube", "▶️", "brands/youtube.svg"),
    "amazonprime" to CategoryIconSpec(Icons.Outlined.Movie, "Amazon Prime", "📦", "brands/amazonprime.svg"),
    "disneyplus" to CategoryIconSpec(Icons.Outlined.Movie, "Disney+", "🏰", "brands/disneyplus.svg"),
    "appletv" to CategoryIconSpec(Icons.Outlined.Tv, "Apple TV+", "🍏", "brands/appletv.svg"),
    "jiocinema" to CategoryIconSpec(Icons.Outlined.Movie, "JioCinema", "🎬", "brands/jiocinema.svg"),
    "sonyliv" to CategoryIconSpec(Icons.Outlined.LiveTv, "SonyLIV", "📺", "brands/sonyliv.svg"),
    "zee5" to CategoryIconSpec(Icons.Outlined.Movie, "Zee5", "📺", "brands/zee5.svg"),
    "hulu" to CategoryIconSpec(Icons.Outlined.Movie, "Hulu", "📺", "brands/hulu.svg"),
    "max" to CategoryIconSpec(Icons.Outlined.Movie, "Max (HBO)", "🎬", "brands/max.svg"),
    "twitch" to CategoryIconSpec(Icons.Outlined.VideogameAsset, "Twitch", "🎮", "brands/twitch.svg"),
    "crunchyroll" to CategoryIconSpec(Icons.Outlined.Movie, "Crunchyroll", "🍥", "brands/crunchyroll.svg"),

    // Music & Audio
    "spotify" to CategoryIconSpec(Icons.Outlined.MusicNote, "Spotify", "🎧", "brands/spotify.svg"),
    "applemusic" to CategoryIconSpec(Icons.Outlined.MusicNote, "Apple Music", "🎵", "brands/applemusic.svg"),
    "youtubemusic" to CategoryIconSpec(Icons.Outlined.MusicNote, "YouTube Music", "🎶", "brands/youtubemusic.svg"),
    "audible" to CategoryIconSpec(Icons.Outlined.Headphones, "Audible", "📚", "brands/audible.svg"),
    "podcasts" to CategoryIconSpec(Icons.Outlined.Podcasts, "Podcasts", "🎙️", "brands/podcasts.svg"),

    // AI & Machine Learning
    "chatgpt" to CategoryIconSpec(Icons.Outlined.Psychology, "ChatGPT", "🤖", "brands/chatgpt.svg"),
    "claude" to CategoryIconSpec(Icons.Outlined.AutoAwesome, "Claude", "✨", "brands/claude.svg"),
    "gemini" to CategoryIconSpec(Icons.Outlined.AutoAwesome, "Google Gemini", "💎", "brands/gemini.svg"),
    "perplexity" to CategoryIconSpec(Icons.Outlined.Search, "Perplexity", "🔍", "brands/perplexity.svg"),
    "midjourney" to CategoryIconSpec(Icons.Outlined.Palette, "Midjourney", "🎨", "brands/midjourney.svg"),
    "githubcopilot" to CategoryIconSpec(Icons.Outlined.Code, "GitHub Copilot", "💻", "brands/githubcopilot.svg"),

    // Cloud, Productivity & Dev
    "google" to CategoryIconSpec(Icons.Outlined.Language, "Google", "🌐", "brands/google.svg"),
    "googleone" to CategoryIconSpec(Icons.Outlined.Cloud, "Google One", "☁️", "brands/googleone.svg"),
    "googleworkspace" to CategoryIconSpec(Icons.Outlined.BusinessCenter, "Google Workspace", "💼", "brands/googleworkspace.svg"),
    "microsoft365" to CategoryIconSpec(Icons.Outlined.Widgets, "Microsoft 365", "📊", "brands/microsoft365.svg"),
    "icloud" to CategoryIconSpec(Icons.Outlined.Cloud, "iCloud", "☁️", "brands/icloud.svg"),
    "dropbox" to CategoryIconSpec(Icons.Outlined.FolderShared, "Dropbox", "📦", "brands/dropbox.svg"),
    "aws" to CategoryIconSpec(Icons.Outlined.Cloud, "AWS", "☁️", "brands/aws.svg"),
    "notion" to CategoryIconSpec(Icons.Outlined.Description, "Notion", "📝", "brands/notion.svg"),
    "figma" to CategoryIconSpec(Icons.Outlined.DesignServices, "Figma", "🎯", "brands/figma.svg"),
    "canva" to CategoryIconSpec(Icons.Outlined.Brush, "Canva", "🎨", "brands/canva.svg"),
    "adobe" to CategoryIconSpec(Icons.Outlined.Draw, "Adobe", "🔺", "brands/adobe.svg"),
    "slack" to CategoryIconSpec(Icons.Outlined.Forum, "Slack", "💬", "brands/slack.svg"),
    "zoom" to CategoryIconSpec(Icons.Outlined.VideoCall, "Zoom", "📹", "brands/zoom.svg"),
    "linkedin" to CategoryIconSpec(Icons.Outlined.Badge, "LinkedIn", "💼", "brands/linkedin.svg"),

    // Gaming & Media
    "playstation" to CategoryIconSpec(Icons.Outlined.SportsEsports, "PlayStation", "🎮", "brands/playstation.svg"),
    "xbox" to CategoryIconSpec(Icons.Outlined.SportsEsports, "Xbox", "🎮", "brands/xbox.svg"),
    "nintendo" to CategoryIconSpec(Icons.Outlined.SportsEsports, "Nintendo Switch", "🎮", "brands/nintendo.svg"),
    "discord" to CategoryIconSpec(Icons.Outlined.Forum, "Discord", "💬", "brands/discord.svg"),
    "medium" to CategoryIconSpec(Icons.Outlined.Article, "Medium", "📰", "brands/medium.svg"),
)

/** Returns the local asset path (e.g. `brands/netflix.svg`) if [icon] represents a brand vector. */
fun brandAsset(icon: String): String? {
    val clean = icon.removePrefix("brand:")
    return brandCategoryIcons[clean]?.brandAsset
}

/** Mapping from category name variants (case-insensitive) to their brand icon key. */
val brandNamesToKey: Map<String, String> = mapOf(
    "netflix" to "netflix",
    "youtube" to "youtube",
    "amazon prime" to "amazonprime",
    "amazon prime video" to "amazonprime",
    "prime video" to "amazonprime",
    "disney+ hotstar" to "disneyplus",
    "disney+" to "disneyplus",
    "disney plus" to "disneyplus",
    "hotstar" to "disneyplus",
    "apple tv+" to "appletv",
    "apple tv" to "appletv",
    "apple tv plus" to "appletv",
    "jiocinema" to "jiocinema",
    "jio cinema" to "jiocinema",
    "sonyliv" to "sonyliv",
    "sony liv" to "sonyliv",
    "zee5" to "zee5",
    "zee 5" to "zee5",
    "hulu" to "hulu",
    "max" to "max",
    "max (hbo)" to "max",
    "hbo max" to "max",
    "twitch" to "twitch",
    "crunchyroll" to "crunchyroll",
    "spotify" to "spotify",
    "apple music" to "applemusic",
    "youtube music" to "youtubemusic",
    "audible" to "audible",
    "podcasts" to "podcasts",
    "apple podcasts" to "podcasts",
    "chatgpt" to "chatgpt",
    "claude" to "claude",
    "google gemini" to "gemini",
    "gemini" to "gemini",
    "perplexity" to "perplexity",
    "perplexity ai" to "perplexity",
    "midjourney" to "midjourney",
    "github copilot" to "githubcopilot",
    "copilot" to "githubcopilot",
    "google" to "google",
    "google one" to "googleone",
    "google workspace" to "googleworkspace",
    "microsoft 365" to "microsoft365",
    "office 365" to "microsoft365",
    "icloud" to "icloud",
    "dropbox" to "dropbox",
    "aws" to "aws",
    "amazon web services" to "aws",
    "notion" to "notion",
    "figma" to "figma",
    "canva" to "canva",
    "adobe" to "adobe",
    "slack" to "slack",
    "zoom" to "zoom",
    "linkedin premium" to "linkedin",
    "linkedin" to "linkedin",
    "playstation plus" to "playstation",
    "playstation" to "playstation",
    "xbox game pass" to "xbox",
    "xbox" to "xbox",
    "nintendo switch online" to "nintendo",
    "nintendo switch" to "nintendo",
    "nintendo" to "nintendo",
    "discord nitro" to "discord",
    "discord" to "discord",
    "medium" to "medium",
)

/** Resolves a category name to its brand key if recognized, or null otherwise. */
fun brandKeyForName(name: String): String? =
    brandNamesToKey[name.trim().lowercase()]

/** Resolves a category name to its offline brand SVG asset path if recognized. */
fun brandAssetForName(name: String): String? =
    brandKeyForName(name)?.let(::brandAsset)
