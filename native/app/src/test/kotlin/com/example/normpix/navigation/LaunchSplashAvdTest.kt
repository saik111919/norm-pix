package com.example.normpix.navigation

import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The cookie's intro that Android plays itself (`avd_splash.xml`, written by `native/design/launch-splash/LaunchSplashAvd.py`) must
 * be [CookieMotion]'s intro, or the cookie Android draws would not be the one the app takes over from. These read the written file
 * as text and check each of its curves against the Kotlin motion, so a change to either one without the other fails here.
 */
class LaunchSplashAvdTest {
    private fun read(name: String): String =
        File("src/main/res/drawable/$name").readText().replace("\r", "")

    private val avd = read("avd_splash.xml")

    private class Anim(val target: String, val property: String, val from: String, val to: String, val duration: Int, val curve: List<Pair<Float, Float>>) {
        /** The share of the way from [from] to [to] at [t] (0..1 of the intro), read off the sampled curve. */
        fun at(t: Float): Float {
            val i = curve.indexOfFirst { it.first >= t }.let { if (it < 0) curve.lastIndex else it }
            if (i == 0) return curve[0].second
            val (x0, y0) = curve[i - 1]
            val (x1, y1) = curve[i]
            return if (x1 == x0) y1 else y0 + (y1 - y0) * (t - x0) / (x1 - x0)
        }
    }

    private val animations: List<Anim> by lazy {
        val out = mutableListOf<Anim>()
        for (targetBlock in Regex("""<target android:name="([^"]+)">(.*?)</target>""", RegexOption.DOT_MATCHES_ALL).findAll(avd)) {
            val target = targetBlock.groupValues[1]
            for (animator in Regex("""<objectAnimator(.*?)</objectAnimator>""", RegexOption.DOT_MATCHES_ALL).findAll(targetBlock.groupValues[2])) {
                val body = animator.groupValues[1]
                fun attr(name: String) = Regex("""android:$name="([^"]*)"""").find(body)!!.groupValues[1]
                val curve = Regex("""<pathInterpolator android:pathData="([^"]*)"""").find(body)!!.groupValues[1]
                val points = Regex("""[ML](-?[0-9.]+),(-?[0-9.]+)""").findAll(curve)
                    .map { it.groupValues[1].toFloat() to it.groupValues[2].toFloat() }.toList()
                out += Anim(target, attr("propertyName"), attr("valueFrom"), attr("valueTo"), attr("duration").toInt(), points)
            }
        }
        out
    }

    private fun anim(target: String, property: String) = animations.single { it.target == target && it.property == property }

    private val samples = (0..60).map { it / 60f }

    @Test
    fun everyAnimatorRunsForTheIntrosLength() {
        assertTrue(animations.isNotEmpty())
        for (a in animations) assertEquals(CookieMotion.INTRO_MS.toInt(), a.duration, "${a.target}.${a.property}")
    }

    @Test
    fun everyAnimatorWaitsTheSameLaunchDelay_soTheIntroIsSeenFromItsStartNotFromTheMiddle() {
        // The system starts the icon when it makes the launch screen, before the launcher's opening has shown the window: the delay
        // is what makes the person see the cookie begin. All the animators wait the same, or the cookie would come apart.
        val offsets = Regex("""<objectAnimator(.*?)</objectAnimator>""", RegexOption.DOT_MATCHES_ALL).findAll(avd)
            .map { Regex("""android:startOffset="([^"]*)"""").find(it.groupValues[1])?.groupValues?.get(1)?.toInt() }.toList()
        assertEquals(animations.size, offsets.size)
        for (offset in offsets) assertEquals(CookieMotion.LAUNCH_DELAY_MS, offset)
    }

    @Test
    fun theCookieGrowsAsTheComposeCookieDoes() {
        for (property in listOf("scaleX", "scaleY")) {
            val a = anim("cookie", property)
            assertEquals("0", a.from)
            assertEquals("1", a.to)
            for (t in samples) assertEquals(CookieMotion.intro(t * CookieMotion.INTRO_MS).scale, a.at(t), 0.01f, "$property at $t")
        }
    }

    @Test
    fun theCookieTurnsAsTheComposeCookieDoes() {
        val a = anim("cookie", "rotation")
        val from = a.from.toFloat()
        val to = a.to.toFloat()
        assertEquals(-CookieMotion.SPIN_DEGREES, from, 0.01f)
        assertEquals(0f, to, 0.01f)
        for (t in samples) {
            val degrees = Math.toDegrees(CookieMotion.intro(t * CookieMotion.INTRO_MS).rotation.toDouble()).toFloat()
            assertEquals(degrees, from + (to - from) * a.at(t), 0.6f, "rotation at $t")
        }
    }

    @Test
    fun theLobesGrowOutOfACircleAsTheComposeCookieDoes_oneStepOfTheMorph() {
        val a = anim("cookie_shape", "pathData")
        for (t in samples) assertEquals(CookieMotion.intro(t * CookieMotion.INTRO_MS).lobes, a.at(t), 0.01f, "lobes at $t")

        // A morph needs the two paths to have exactly the same commands, or Android refuses the whole animation.
        fun verbs(path: String) = path.filter { it.isLetter() }
        assertEquals(verbs(a.from), verbs(a.to), "the same commands in the same order")
        assertEquals(CookieMotion.LOBES * 24, verbs(a.to).count { it == 'Q' }, "one curve for each of the points the Compose cookie is drawn through")

        // The plain circle really is one, and the lobed one reaches the Compose cookie's height, at the radius the icon is drawn with.
        fun numbers(path: String) = Regex("""-?[0-9.]+""").findAll(path.substringAfter('M')).map { it.value.toFloat() }.toList()
        fun radii(path: String): List<Float> {
            val n = numbers(path)
            // Each Q has a control point (x1, y1) and an end point (x, y); the control points are the sampled points on the curve.
            return (2 until n.size step 4).map { i -> kotlin.math.hypot(n[i] - 144f, n[i + 1] - 144f) }
        }
        val circle = radii(a.from)
        assertEquals(CookieMotion.RADIUS_DP, circle.max(), 0.05f)
        assertEquals(CookieMotion.RADIUS_DP, circle.min(), 0.05f)
        val lobed = radii(a.to)
        assertEquals(CookieMotion.RADIUS_DP * (1 + CookieMotion.WAVE), lobed.max(), 0.05f)
        assertEquals(CookieMotion.RADIUS_DP * (1 - CookieMotion.WAVE), lobed.min(), 0.05f)
    }

    @Test
    fun theMarkSpringsUpAndFadesInAsTheComposeMarkDoes() {
        for (property in listOf("scaleX", "scaleY")) {
            val a = anim("logo", property)
            assertEquals(CookieMotion.LOGO_FROM, a.from.toFloat(), 0.0001f)
            assertEquals(1f, a.to.toFloat(), 0.0001f)
            for (t in samples) {
                val scale = CookieMotion.intro(t * CookieMotion.INTRO_MS).logoScale
                assertEquals(scale, a.from.toFloat() + (a.to.toFloat() - a.from.toFloat()) * a.at(t), 0.01f, "$property at $t")
            }
        }
        val marks = animations.filter { it.property == "fillAlpha" }
        assertEquals(3, marks.size, "the three cards of the mark")
        for (a in marks) for (t in samples) {
            assertEquals(CookieMotion.intro(t * CookieMotion.INTRO_MS).logoAlpha, a.at(t), 0.01f, "${a.target} at $t")
        }
    }

    @Test
    fun theLastFrameIsTheStaticIconAndBothAreCentredAtTheSizeComposeDraws() {
        val icon = read("splash_icon.xml")
        fun group(xml: String, name: String) = Regex("""<group\s+android:name="$name"(.*?)>""", RegexOption.DOT_MATCHES_ALL).find(xml)!!.groupValues[1]
        fun value(tag: String, name: String) = Regex("""android:$name="([^"]*)"""").find(tag)!!.groupValues[1]

        assertEquals("144", value(group(icon, "cookie"), "pivotX"))
        assertEquals("144", value(group(icon, "cookie"), "pivotY"))
        assertEquals("1", value(group(icon, "cookie"), "scaleX"))
        assertEquals("0", value(group(icon, "cookie"), "rotation"))
        assertEquals("1", value(group(icon, "logo"), "scaleX"))

        // The animation starts where the intro does (nothing of the cookie, the mark small) and the icon is where it ends.
        assertEquals("0", value(group(avd, "cookie"), "scaleX"))
        assertEquals(CookieMotion.LOGO_FROM, value(group(avd, "logo"), "scaleX").toFloat(), 0.0001f)

        val endShape = anim("cookie_shape", "pathData").to
        assertTrue(icon.contains(endShape), "the icon's cookie is the morph's last shape, character for character")

        // The mark is drawn CookieMotion.LOGO_OF_RADIUS radii wide, centred, in the icon's 288 dp.
        val side = CookieMotion.RADIUS_DP * CookieMotion.LOGO_OF_RADIUS
        val markGroup = Regex("""<group\s+android:scaleX="([0-9.]+)"\s+android:scaleY="([0-9.]+)"\s+android:translateX="([0-9.]+)"\s+android:translateY="([0-9.]+)"""").find(icon)!!
        assertEquals(side / 108f, markGroup.groupValues[1].toFloat(), 0.0001f)
        assertEquals((288f - side) / 2f, markGroup.groupValues[3].toFloat(), 0.01f)
        assertTrue(abs(markGroup.groupValues[3].toFloat() - markGroup.groupValues[4].toFloat()) < 0.001f, "centred both ways")
    }

    @Test
    fun theCookieStaysInsideTheCircleAndroidShowsOfAnIcon() {
        // Android masks an icon of this size (no background) to a circle of 192 dp: the cookie, as far as it ever reaches while it
        // overshoots, has to be inside it or its edge would be cut off.
        val limit = 96f
        var reach = 0f
        for (ms in 0..CookieMotion.INTRO_MS.toInt() step 5) {
            val s = CookieMotion.intro(ms.toFloat())
            reach = maxOf(reach, CookieMotion.RADIUS_DP * s.scale * (1 + CookieMotion.WAVE * s.lobes))
        }
        assertTrue(reach <= limit, "reaches ${reach.roundToInt()} dp of the 96 it may")
    }
}
