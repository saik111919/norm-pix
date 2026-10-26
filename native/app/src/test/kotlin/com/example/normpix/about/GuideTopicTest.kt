package com.example.normpix.about

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** The guide on the About page: every topic is whole, and a topic can be found again by the name a route carries. */
class GuideTopicTest {
    @Test
    fun everyTopicHasAWholeExplanation() {
        for (topic in GuideTopic.entries) {
            assertTrue(topic.title.isNotBlank(), "${topic.name} has a title")
            assertTrue(topic.summary.isNotBlank(), "${topic.name} has a summary")
            assertTrue(topic.steps.size in 3..6, "${topic.name} has a few steps to follow: ${topic.steps.size}")
            assertTrue(topic.steps.all { it.isNotBlank() }, "${topic.name} has no empty step")
            assertTrue(topic.tips.isNotEmpty(), "${topic.name} has something worth knowing")
            assertTrue(topic.tips.all { it.isNotBlank() }, "${topic.name} has no empty tip")
            assertTrue(topic.steps.all { it.trim() == it && it.endsWith(".") }, "${topic.name}'s steps are sentences")
        }
    }

    @Test
    fun topicsAreToldApart() {
        assertEquals(GuideTopic.entries.size, GuideTopic.entries.map { it.title }.toSet().size, "no two topics share a title")
        assertEquals(GuideTopic.entries.size, GuideTopic.entries.map { it.name }.toSet().size)
    }

    @Test
    fun aTopicsColorIsOneOfThePalettes() {
        // The category palette has eight; an index outside it would wrap round and quietly repeat a color.
        for (topic in GuideTopic.entries) assertTrue(topic.hue in 0..7, "${topic.name}'s color is ${topic.hue}")
    }

    @Test
    fun aTopicIsFoundAgainByItsName() {
        for (topic in GuideTopic.entries) assertEquals(topic, GuideTopic.named(topic.name))
        assertNull(GuideTopic.named("NoSuchTopic"))
        assertNull(GuideTopic.named(null))
    }

    @Test
    fun theGuideCoversTheThingsNormPixIsFor() {
        val titles = GuideTopic.entries.map { it.title }
        for (wanted in listOf("Add a transaction", "Add from a receipt", "Home", "Activity", "Insights", "Home-screen widgets", "Colors and themes")) {
            assertTrue(wanted in titles, "the guide has $wanted")
        }
    }

    @Test
    fun theCardExchangeTopicSaysItIsOffUntilTurnedOn() {
        // The app's NFC switch is off on a fresh install; the guide has to say how it is turned on.
        val steps = GuideTopic.CardExchange.steps.joinToString(" ")
        assertTrue("off until you turn it on" in steps, steps)
        assertTrue("Availability & Schedules" in steps, steps)
        assertEquals(GuideLink.Availability, GuideTopic.CardExchange.link)
    }

    @Test
    fun linksHaveDistinctLabels() {
        assertEquals(GuideLink.entries.size, GuideLink.entries.map { it.label }.toSet().size)
        assertTrue(GuideLink.entries.all { it.label.startsWith("Open ") })
    }
}
