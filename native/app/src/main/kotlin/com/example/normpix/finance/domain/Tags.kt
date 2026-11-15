package com.example.normpix.finance.domain

import java.time.LocalDateTime

/**
 * A label the user names and puts on expenses and income - "Tax-deductible", "Reimbursable", "Goa trip" - any number on one
 * transaction (2026-11-15, round-2 recommendation 8). Only a label: it moves no money and changes no total. Referred to by [id], never
 * by name, so renaming a tag renames it on every transaction at once. A transfer has none: it only moves money between the user's
 * own accounts, so it is neither spending nor income to add up.
 */
data class Tag(
    val id: Long? = null,
    val name: String,
    val createdAt: LocalDateTime,
)

/** The longest a tag's name may be: a few words, short enough for a chip. */
const val MAX_TAG_NAME = 30

/** The tag the yearly review puts first, when the user has it: what the tax year is about. */
const val TAX_DEDUCTIBLE_TAG = "Tax-deductible"

/** The other ready-made tag: what is paid out to be paid back (work, a trip shared with friends). */
const val REIMBURSABLE_TAG = "Reimbursable"

/**
 * The tags offered ready-made while the user has none yet. Offered, not made: one is created only when it is picked, so a user who
 * never wanted them never has them.
 */
val SUGGESTED_TAGS = listOf(TAX_DEDUCTIBLE_TAG, REIMBURSABLE_TAG)

/** A tag's name as it is kept: no spaces at its ends and one space between words. */
fun cleanTagName(name: String): String = name.trim().replace(Regex("\\s+"), " ")

/** How long [name] is as it reads: an emoji is one, as a letter is. */
private fun lengthOf(name: String): Int = name.codePointCount(0, name.length)

/**
 * What is wrong with [name] as a tag's name (already [cleanTagName]d), said as advice to the user; null when it can be one. Unique
 * names are the repository's to check: it knows the others.
 */
fun tagNameFault(name: String): String? = when {
    name.isEmpty() -> "Give the tag a name."
    lengthOf(name) > MAX_TAG_NAME -> "Keep the tag's name to $MAX_TAG_NAME characters."
    else -> null
}

/** Whether [name] is the tax-deductible tag (ignoring case and spaces), which the yearly review lists first. */
fun isTaxDeductible(name: String): Boolean = cleanTagName(name).equals(TAX_DEDUCTIBLE_TAG, ignoreCase = true)

/**
 * What the transactions with one tag came to over a time: [spent] is its expenses and [received] its income, both in minor units,
 * and [count] how many transactions those are. Transfers are never counted (they have no tags), and what is dated after today is not
 * in it yet (see `settledCutoff`).
 */
data class TagTotal(val tagId: Long, val spent: Long = 0, val received: Long = 0, val count: Int = 0) {
    /** All the money that moved under the tag, either way: what the review sorts by. */
    val total: Long get() = spent + received
}

/** One line of the yearly review: a tag and what its transactions came to (zero when it has none in the year). */
data class TagReviewRow(val tag: Tag, val total: TagTotal)

/**
 * The yearly review's list: every tag in [tags] with what [totals] say it came to - a tag with nothing in the year is listed with
 * zeros, so the user sees it had none. The tax-deductible tag comes first when there is one (it is what a tax year is for); then the
 * biggest total first, then by name. A transaction with two tags counts under both, so the rows are not meant to be added up.
 */
fun tagReview(tags: List<Tag>, totals: List<TagTotal>): List<TagReviewRow> {
    val byTag = totals.associateBy { it.tagId }
    return tags.filter { it.id != null }
        .map { tag -> TagReviewRow(tag, byTag[tag.id] ?: TagTotal(tag.id!!)) }
        .sortedWith(
            compareBy<TagReviewRow> { if (isTaxDeductible(it.tag.name)) 0 else 1 }
                .thenByDescending { it.total.total }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.tag.name }
                .thenBy { it.tag.id },
        )
}
