package com.example.normpix.finance.domain

import java.time.LocalDateTime

/*
 * Spending by person. Nothing here is stored: a person's total is added up from the expenses that name them each time it is
 * asked for, so adding, editing, deleting or reassigning an expense can never leave a stale number behind.
 */

/** The longest name a person keeps: enough for a full name, short enough for a row. */
const val MAX_PERSON_NAME = 40

/** Collapses runs of spaces and trims, so "  Mom   Jones " and "Mom Jones" are the same name. */
fun cleanPersonName(name: String): String = name.trim().replace(Regex("\\s+"), " ")

/** What was spent on one person over a period. */
data class PersonTotal(
    val personId: Long,
    /** In minor units; expenses only. */
    val amount: Long,
    val count: Int,
    /** When the latest of these expenses happened. */
    val lastAt: LocalDateTime?,
)

/**
 * All spending over a period, and how much of it was on people. [totalExpense] is *every* expense in the period - the same
 * figure the dashboard shows - so the part that is on people can never be larger than the whole.
 */
data class PeopleSpending(
    val totalExpense: Long = 0,
    val expenseCount: Int = 0,
    val byPerson: List<PersonTotal> = emptyList(),
) {
    /** Spent on someone in particular. */
    val assigned: Long get() = byPerson.sumOf { it.amount }

    /** Spent on no one in particular. */
    val unassigned: Long get() = (totalExpense - assigned).coerceAtLeast(0)

    /** Number of expenses that name a person. */
    val assignedCount: Int get() = byPerson.sumOf { it.count }

    fun of(personId: Long): PersonTotal? = byPerson.firstOrNull { it.personId == personId }

    /** The share of all spending that went on [personId], 0..1. */
    fun shareOfPerson(personId: Long): Double = shareOf(of(personId)?.amount ?: 0, totalExpense)

    /** The share of all spending that went on people at all, 0..1. */
    val assignedShare: Double get() = shareOf(assigned, totalExpense)

    companion object {
        val Empty = PeopleSpending()
    }
}

/**
 * People in the order a list shows them: whoever got the most of the period's spending first, then by name; people with
 * nothing in it follow, alphabetically. Archived people are not part of the main order.
 */
fun List<Person>.rankedBy(spending: PeopleSpending): List<Person> =
    sortedWith(
        compareByDescending<Person> { spending.of(it.id ?: -1)?.amount ?: 0L }
            .thenBy { it.name.lowercase() },
    )
