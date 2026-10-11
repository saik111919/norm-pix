package com.example.normpix.wallet

import java.util.Locale

/**
 * Works out which bank a free-typed name refers to, and so which bundled logo
 * represents it. Every bank logo in the app - card faces, bank chips, reminder
 * rows - comes through here, so a bank is recognized the same way everywhere
 * and supporting a new one is a [BankDirectory] entry, not a UI change.
 *
 * Names are normalized first (case, punctuation, "&", legal suffixes such as
 * "Ltd"), then matched on whole words against each bank's name and aliases:
 * "HDFC", "HDFC Bank" and "HDFC BANK LTD" all resolve, while "Bobcat Credit
 * Union" doesn't pass for Bank of Baroda's "BOB". When several banks match,
 * the longest match wins, so "State Bank of India" is SBI, not Bank of India.
 */
object BankLogoResolver {
    /** One name or alias of a bank, normalized, plus the same with its spaces removed. */
    private class Key(val entry: BankDirectoryEntry, val words: String) {
        val joined = words.replace(" ", "")
    }

    private val keys: List<Key> by lazy {
        BankDirectory.entries.flatMap { entry ->
            (listOf(entry.name) + entry.aliases).map(::normalize).filter { it.isNotEmpty() }.distinct().map { Key(entry, it) }
        }
    }

    /** The bank [bankName] refers to, or null when it isn't one we know. */
    fun bankFor(bankName: String): BankDirectoryEntry? {
        val name = normalize(bankName)
        if (name.isEmpty()) return null
        val padded = " $name "
        keys.filter { padded.contains(" ${it.words} ") }.maxByOrNull { it.words.length }?.let { return it.entry }
        // Names typed without spaces ("HDFCBANK"). Only multi-word names and aliases count here:
        // a short single-word alias inside a longer word ("bob" in "bobcat") is a coincidence.
        val joined = name.replace(" ", "")
        return keys.filter { ' ' in it.words && joined.contains(it.joined) }.maxByOrNull { it.joined.length }?.entry
    }

    /** The bundled logo artwork for [bankName] (getBankLogo); null when the bank is unknown or has none on file. */
    fun logoFor(bankName: String): BankLogoArt? = bankFor(bankName)?.logo

    /** [name] as compared: lowercase words split on punctuation, "&" spelled out, legal suffixes and a leading "the" dropped. */
    fun normalize(name: String): String {
        val words = name.lowercase(Locale.ROOT)
            .replace("&", " and ")
            // Initialisms with dots or apostrophes ("H.D.F.C.") read as one word.
            .replace(Regex("[.'’]"), "")
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }
            .toMutableList()
        while (words.size > 1 && words.last() in LegalSuffixes) words.removeAt(words.lastIndex)
        if (words.size > 1 && words.first() == "the") words.removeAt(0)
        return words.joinToString(" ")
    }

    private val LegalSuffixes = setOf("ltd", "limited", "plc", "inc", "corp", "corporation", "pvt", "private", "co")
}
