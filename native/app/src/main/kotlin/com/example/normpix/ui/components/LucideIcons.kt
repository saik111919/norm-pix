package com.example.normpix.ui.components

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.example.normpix.R
import java.util.concurrent.ConcurrentHashMap

/*
 * Lucide (https://lucide.dev, release 1.70.0; ISC, the Feather-derived icons MIT: assets/licenses/lucide.txt, shown in About) as the
 * app's other set of interface icons (Settings > Appearance > Icons > Interface icons).
 *
 * The code names Material icons everywhere (`Icons.Outlined.Lock`), so Lucide is a lookup at the moment one is drawn, not a second
 * set of names: `Icon` (AppIcon.kt) asks [forMaterial] for the Lucide drawing of the Material icon it was given and draws that
 * where there is one. Two plain-text resources hold it all:
 *   res/raw/lucide_map.txt    "MaterialName=lucide-name" (the Material name without its family), one per line; the table to edit;
 *   res/raw/lucide_icons.txt  each Lucide icon as path data, made from that table by native/design/lucide/LucideBuild.java.
 * A Material icon with no line in the table is drawn as Material. Nothing is fetched: the drawings ship in the app.
 */
internal object LucideIcons {
    /** Lucide draws on a 24 grid with a 2 stroke, round at the ends and joins. */
    private const val GRID = 24f
    private const val STROKE = 2f

    /** The families a Material `ImageVector`'s name starts with (`Outlined.Lock`, `AutoMirrored.Rounded.ArrowBack`). */
    private val families = setOf("Outlined", "Rounded", "Filled", "Sharp", "TwoTone", "AutoMirrored")

    /** What the two resources say: each Lucide icon's path data, and which Lucide icon stands for which Material one. */
    class Table(val drawings: Map<String, List<String>>, val mapping: Map<String, String>)

    @Volatile
    private var loaded: Table? = null
    private val built = ConcurrentHashMap<String, ImageVector>()

    /** The table, read from the app's resources the first time it is wanted. */
    fun table(context: Context): Table = loaded ?: synchronized(this) {
        loaded ?: read(context).also { loaded = it }
    }

    internal fun read(context: Context): Table {
        val resources = context.resources
        val drawings = HashMap<String, List<String>>()
        resources.openRawResource(R.raw.lucide_icons).bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.isBlank() || line.startsWith("#")) continue
                val parts = line.split('\t')
                drawings[parts[0]] = parts.drop(1).filter { it.isNotBlank() }
            }
        }
        val mapping = HashMap<String, String>()
        resources.openRawResource(R.raw.lucide_map).bufferedReader().useLines { lines ->
            for (line in lines) {
                val text = line.trim()
                if (text.isEmpty() || text.startsWith("#")) continue
                val at = text.indexOf('=')
                if (at > 0) mapping[text.substring(0, at).trim()] = text.substring(at + 1).trim()
            }
        }
        return Table(drawings, mapping)
    }

    /** "Lock" for `Outlined.Lock` or `AutoMirrored.Outlined.Lock`; null for an icon that is not Material's (the app's own artwork). */
    fun materialName(vectorName: String): String? {
        val parts = vectorName.split('.')
        return if (parts.size >= 2 && parts.first() in families) parts.last() else null
    }

    /** Lucide's drawing for the Material [icon], or null where the table has none for it or it is not a Material icon. */
    fun forMaterial(context: Context, icon: ImageVector): ImageVector? {
        val lucide = table(context).mapping[materialName(icon.name) ?: return null] ?: return null
        return vector(context, lucide, icon.autoMirror)
    }

    /** One Lucide icon as an `ImageVector` (built once; one that mirrors in a right-to-left layout when the Material one did). */
    fun vector(context: Context, lucide: String, autoMirror: Boolean = false): ImageVector? {
        val key = if (autoMirror) "$lucide|mirror" else lucide
        built[key]?.let { return it }
        val paths = table(context).drawings[lucide] ?: return null
        val ink = SolidColor(Color.Black)
        val vector = ImageVector.Builder(
            name = "Lucide.$lucide",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = GRID,
            viewportHeight = GRID,
            autoMirror = autoMirror,
        ).apply {
            for (data in paths) {
                addPath(
                    pathData = PathParser().parsePathString(data).toNodes(),
                    fill = null,
                    stroke = ink,
                    strokeLineWidth = STROKE,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
        return built.putIfAbsent(key, vector) ?: vector
    }
}
