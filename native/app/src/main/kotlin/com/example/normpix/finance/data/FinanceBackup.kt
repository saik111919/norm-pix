package com.example.normpix.finance.data

import com.example.normpix.banks.BankCatalog
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.BILLING_DAYS
import com.example.normpix.finance.domain.Budget
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.pathName
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.EMOJI_ICON_PREFIX
import com.example.normpix.finance.domain.emojiOf
import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.CustomBank
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.cleanPersonName
import com.example.normpix.finance.domain.isPictureName
import com.example.normpix.finance.domain.isSingleEmoji
import com.example.normpix.finance.domain.minorToDecimalString
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Everything needed to reconstruct the ledger: the unit of export, import,
 * and restore. Identities (ids) are preserved so references stay valid.
 */
data class FinanceSnapshot(
    val currencyCode: String,
    val defaultAccountId: Long? = null,
    val accounts: List<Account>,
    val categories: List<Category>,
    val transactions: List<FinanceTransaction>,
    val budgets: List<Budget>,
    val recurringRules: List<RecurringRule>,
    /** The banks the user added; backups from before banks existed simply have none. */
    val banks: List<CustomBank> = emptyList(),
    /** The people expenses were spent on; backups from before people existed simply have none. */
    val people: List<Person> = emptyList(),
)

/**
 * Result of reading a backup file: either a validated snapshot or a list of
 * human-readable problems. Nothing is written until the caller restores a
 * snapshot that validated cleanly.
 */
class BackupReadResult private constructor(val snapshot: FinanceSnapshot?, val errors: List<String>) {
    val isValid: Boolean get() = snapshot != null

    companion object {
        fun ok(snapshot: FinanceSnapshot) = BackupReadResult(snapshot, emptyList())
        fun invalid(errors: List<String>) = BackupReadResult(null, errors)
    }
}

private class BackupFormatError(message: String) : Exception(message)

/** JSON backup format (versioned) and CSV transaction export. */
object FinanceBackupCodec {
    const val FORMAT = "normpix-finance-backup"
    /**
     * 2 adds people and the person on an expense. Version 1 files still read (they just have no people); a file with a newer
     * version than this is refused, so an older app never restores a backup and silently drops who the spending was on.
     */
    const val FORMAT_VERSION = 2

    private val prettyJson = Json { prettyPrint = true }
    private val isoLocal: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")

    // ---------------------------------------------------------------- JSON

    private fun iso(d: LocalDateTime): String = isoLocal.format(d)

    fun encode(s: FinanceSnapshot, exportedAt: LocalDateTime): String {
        val root = buildJsonObject {
            put("format", FORMAT)
            put("version", FORMAT_VERSION)
            put("exportedAt", iso(exportedAt))
            put("currency", s.currencyCode)
            put("settings", buildJsonObject { put("defaultAccountId", s.defaultAccountId) })
            put(
                "accounts",
                buildJsonArray {
                    for (a in s.accounts) {
                        add(
                            buildJsonObject {
                                put("id", a.id)
                                put("name", a.name)
                                put("kind", a.kind.key)
                                put("openingBalance", a.openingBalance)
                                put("archived", a.archived)
                                put("sortOrder", a.sortOrder)
                                put("createdAt", iso(a.createdAt))
                                put("updatedAt", iso(a.updatedAt))
                                put("bankKey", a.bankKey)
                                put("creditLimit", a.creditLimit)
                                put("statementDay", a.statementDay)
                                put("dueDay", a.dueDay)
                            },
                        )
                    }
                },
            )
            put(
                "banks",
                buildJsonArray {
                    for (b in s.banks) {
                        add(
                            buildJsonObject {
                                put("key", b.key)
                                put("name", b.name)
                                put("createdAt", iso(b.createdAt))
                            },
                        )
                    }
                },
            )
            put(
                "people",
                buildJsonArray {
                    for (p in s.people) {
                        add(
                            buildJsonObject {
                                put("id", p.id)
                                put("name", p.name)
                                put("emoji", p.emoji)
                                put("color", p.color)
                                put("archived", p.archived)
                                put("createdAt", iso(p.createdAt))
                                put("updatedAt", iso(p.updatedAt))
                            },
                        )
                    }
                },
            )
            put(
                "categories",
                buildJsonArray {
                    for (c in s.categories) {
                        add(
                            buildJsonObject {
                                put("id", c.id)
                                put("name", c.name)
                                put("type", c.type.key)
                                put("icon", c.icon)
                                put("color", c.color)
                                put("archived", c.archived)
                                put("parentId", c.parentId)
                                put("sortOrder", c.sortOrder)
                                put("createdAt", iso(c.createdAt))
                                put("updatedAt", iso(c.updatedAt))
                            },
                        )
                    }
                },
            )
            put(
                "transactions",
                buildJsonArray {
                    for (t in s.transactions) {
                        add(
                            buildJsonObject {
                                put("id", t.id)
                                put("type", t.type.key)
                                put("amount", t.amount)
                                put("accountId", t.accountId)
                                put("toAccountId", t.toAccountId)
                                put("categoryId", t.categoryId)
                                put("occurredAt", iso(t.occurredAt))
                                put("payee", t.payee)
                                put("note", t.note)
                                put("recurringId", t.recurringId)
                                put("personId", t.personId)
                                put("picture", t.picture)
                                put("createdAt", iso(t.createdAt))
                                put("updatedAt", iso(t.updatedAt))
                            },
                        )
                    }
                },
            )
            put(
                "budgets",
                buildJsonArray {
                    for (b in s.budgets) {
                        add(
                            buildJsonObject {
                                put("id", b.id)
                                put("categoryId", b.categoryId)
                                put("amount", b.amount)
                                put("effectiveMonth", b.effectiveMonth.toInt())
                            },
                        )
                    }
                },
            )
            put(
                "recurringRules",
                buildJsonArray {
                    for (r in s.recurringRules) {
                        add(
                            buildJsonObject {
                                put("id", r.id)
                                put("type", r.type.key)
                                put("amount", r.amount)
                                put("accountId", r.accountId)
                                put("toAccountId", r.toAccountId)
                                put("categoryId", r.categoryId)
                                put("payee", r.payee)
                                put("note", r.note)
                                put("frequency", r.frequency.key)
                                put("interval", r.interval)
                                put("startDate", iso(r.startDate))
                                put("endDate", r.endDate?.let(::iso))
                                put("nextDate", iso(r.nextDate))
                                put("active", r.active)
                                put("createdAt", iso(r.createdAt))
                                put("updatedAt", iso(r.updatedAt))
                                put("personId", r.personId)
                            },
                        )
                    }
                },
            )
        }
        return prettyJson.encodeToString(JsonElement.serializer(), root)
    }

    /** Parses and fully validates a backup. Never throws for bad input. */
    fun decode(text: String): BackupReadResult {
        val root = try {
            Json.parseToJsonElement(text)
        } catch (_: Exception) {
            return BackupReadResult.invalid(listOf("This file isn't a NormPix backup (it isn't valid JSON)."))
        }
        if (root !is JsonObject || root.string("format") != FORMAT) {
            return BackupReadResult.invalid(listOf("This file isn't a NormPix finance backup."))
        }
        val version = (root["version"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
        if (version == null || version > FORMAT_VERSION) {
            return BackupReadResult.invalid(
                listOf("This backup was made by a newer version of NormPix. Update the app, then try again."),
            )
        }
        return try {
            val snapshot = parse(root)
            val problems = validate(snapshot)
            if (problems.isEmpty()) BackupReadResult.ok(snapshot) else BackupReadResult.invalid(problems)
        } catch (e: BackupFormatError) {
            BackupReadResult.invalid(listOf(e.message ?: "The backup is damaged."))
        }
    }

    private fun parse(root: JsonObject): FinanceSnapshot {
        val currency = root.requireString("currency", "backup")
        val defaultAccountId = (root["settings"] as? JsonObject)?.longOrNull("defaultAccountId", "settings")

        fun list(key: String): List<JsonObject> {
            val value = root[key]
            if (value == null || value is JsonNull) return emptyList()
            if (value !is JsonArray) throw BackupFormatError("\"$key\" must be a list.")
            return value.map { it as? JsonObject ?: throw BackupFormatError("\"$key\" contains an invalid entry.") }
        }

        return FinanceSnapshot(
            currencyCode = currency,
            defaultAccountId = defaultAccountId,
            accounts = list("accounts").map { m ->
                Account(
                    id = m.requireLong("id", "account"),
                    name = m.requireString("name", "account"),
                    kind = m.enumValue(AccountKind.entries, { it.key }, "kind", "account"),
                    openingBalance = m.requireLong("openingBalance", "account"),
                    archived = m.bool("archived"),
                    sortOrder = (m.longOrNull("sortOrder", "account") ?: 0).toInt(),
                    createdAt = m.date("createdAt", "account"),
                    updatedAt = m.date("updatedAt", "account"),
                    bankKey = accountBank(m),
                    creditLimit = m.longOrNull("creditLimit", "account"),
                    statementDay = m.dayOrNull("statementDay", "account"),
                    dueDay = m.dayOrNull("dueDay", "account"),
                )
            },
            banks = list("banks").map { m ->
                CustomBank(
                    key = m.requireString("key", "bank"),
                    name = m.requireString("name", "bank"),
                    createdAt = m.date("createdAt", "bank"),
                )
            },
            people = list("people").map { m ->
                Person(
                    id = m.requireLong("id", "person"),
                    name = m.requireString("name", "person"),
                    emoji = m.stringOrNull("emoji"),
                    color = (m.longOrNull("color", "person") ?: 0).toInt(),
                    archived = m.bool("archived"),
                    createdAt = m.date("createdAt", "person"),
                    updatedAt = m.date("updatedAt", "person"),
                )
            },
            categories = list("categories").map { m ->
                Category(
                    id = m.requireLong("id", "category"),
                    name = m.requireString("name", "category"),
                    type = m.enumValue(CategoryType.entries, { it.key }, "type", "category"),
                    icon = m.requireString("icon", "category"),
                    color = (m.longOrNull("color", "category") ?: 0).toInt(),
                    archived = m.bool("archived"),
                    sortOrder = (m.longOrNull("sortOrder", "category") ?: 0).toInt(),
                    createdAt = m.date("createdAt", "category"),
                    updatedAt = m.date("updatedAt", "category"),
                    parentId = m.longOrNull("parentId", "category"),
                )
            },
            transactions = list("transactions").map { m ->
                FinanceTransaction(
                    id = m.requireLong("id", "transaction"),
                    type = m.enumValue(TransactionType.entries, { it.key }, "type", "transaction"),
                    amount = m.requireLong("amount", "transaction"),
                    accountId = m.requireLong("accountId", "transaction"),
                    toAccountId = m.longOrNull("toAccountId", "transaction"),
                    categoryId = m.longOrNull("categoryId", "transaction"),
                    occurredAt = m.date("occurredAt", "transaction"),
                    payee = m.stringOrNull("payee"),
                    note = m.stringOrNull("note"),
                    recurringId = m.longOrNull("recurringId", "transaction"),
                    createdAt = m.date("createdAt", "transaction"),
                    updatedAt = m.date("updatedAt", "transaction"),
                    personId = m.longOrNull("personId", "transaction"),
                    // Only a name the app could have given it: anything else is not a picture of this app's.
                    picture = m.stringOrNull("picture")?.takeIf(::isPictureName),
                )
            },
            budgets = list("budgets").map { m ->
                Budget(
                    id = m.requireLong("id", "budget"),
                    categoryId = m.longOrNull("categoryId", "budget"),
                    amount = m.longOrNull("amount", "budget"),
                    effectiveMonth = m.month("effectiveMonth"),
                )
            },
            recurringRules = list("recurringRules").map { m ->
                RecurringRule(
                    id = m.requireLong("id", "recurring rule"),
                    type = m.enumValue(TransactionType.entries, { it.key }, "type", "recurring rule"),
                    amount = m.requireLong("amount", "recurring rule"),
                    accountId = m.requireLong("accountId", "recurring rule"),
                    toAccountId = m.longOrNull("toAccountId", "recurring rule"),
                    categoryId = m.longOrNull("categoryId", "recurring rule"),
                    payee = m.stringOrNull("payee"),
                    note = m.stringOrNull("note"),
                    frequency = m.enumValue(RecurrenceFrequency.entries, { it.key }, "frequency", "recurring rule"),
                    interval = (m.longOrNull("interval", "recurring rule") ?: 1).toInt(),
                    startDate = m.date("startDate", "recurring rule"),
                    endDate = if (m["endDate"].let { it == null || it is JsonNull }) null else m.date("endDate", "recurring rule"),
                    nextDate = m.date("nextDate", "recurring rule"),
                    active = (m["active"] as? JsonPrimitive)?.booleanOrNull != false,
                    createdAt = m.date("createdAt", "recurring rule"),
                    updatedAt = m.date("updatedAt", "recurring rule"),
                    personId = m.longOrNull("personId", "recurring rule"),
                )
            },
        )
    }

    /**
     * An account's bank. Backups made before banks existed have no `bankKey` at all (not even null), so they get the same
     * leading-bank-name link the database migration gives the same account; a `bankKey` that is present is taken as it is.
     */
    private fun accountBank(m: JsonObject): String? {
        if ("bankKey" in m) return m.stringOrNull("bankKey")
        if (m.string("kind") == AccountKind.Cash.key) return null
        return m.string("name")?.let { BankCatalog.BuiltIn.leading(it)?.key }
    }

    /**
     * Referential and semantic checks (the same invariants the database
     * enforces), reported as a short list of readable problems.
     */
    fun validate(s: FinanceSnapshot): List<String> {
        val problems = mutableListOf<String>()
        fun add(message: String) {
            if (problems.size < 8) problems += message
        }

        if (CurrencySpec.all.none { it.code == s.currencyCode }) {
            add("Unsupported currency \"${s.currencyCode}\".")
        }

        fun ids(values: List<Long?>, what: String): Set<Long> {
            val seen = mutableSetOf<Long>()
            for (id in values) {
                if (id == null || !seen.add(id)) {
                    add("The backup has missing or duplicate $what ids.")
                    break
                }
            }
            return seen
        }

        val accountIds = ids(s.accounts.map { it.id }, "account")
        val categoryIds = ids(s.categories.map { it.id }, "category")
        val ruleIds = ids(s.recurringRules.map { it.id }, "recurring rule")
        val personIds = ids(s.people.map { it.id }, "person")
        ids(s.transactions.map { it.id }, "transaction")
        ids(s.budgets.map { it.id }, "budget")
        val categoryTypes = s.categories.associate { it.id to it.type }

        for (a in s.accounts) if (a.name.isBlank()) add("An account has no name.")
        for (a in s.accounts) {
            val limit = a.creditLimit ?: continue
            if (limit <= 0) add("Account \"${a.name}\" has a credit limit that is not above zero.")
            if (a.kind != AccountKind.Card) add("Account \"${a.name}\" has a credit limit but is not a card.")
        }
        for (a in s.accounts) {
            if (a.statementDay == null && a.dueDay == null) continue
            if (a.statementDay != null && a.statementDay !in BILLING_DAYS) add("Account \"${a.name}\" has a statement date that is not a day of the month.")
            if (a.dueDay != null && a.dueDay !in BILLING_DAYS) add("Account \"${a.name}\" has a payment due date that is not a day of the month.")
            if (a.kind != AccountKind.Card) add("Account \"${a.name}\" has a statement or due date but is not a card.")
        }
        val bankKeys = s.banks.mapTo(mutableSetOf()) { it.key }
        if (bankKeys.size != s.banks.size) add("The backup has duplicate banks.")
        for (b in s.banks) if (b.name.isBlank()) add("A bank has no name.")
        for (a in s.accounts) {
            val bank = a.bankKey ?: continue
            if (bank !in bankKeys && BankCatalog.BuiltIn.byKey(bank) == null) add("Account \"${a.name}\" refers to a missing bank.")
        }
        for (p in s.people) {
            if (p.name.isBlank()) add("A person has no name.")
            if (p.emoji != null && !isSingleEmoji(p.emoji)) add("Person \"${p.name}\" has a picture that is not one emoji.")
        }
        if (s.people.map { cleanPersonName(it.name).lowercase() }.let { it.size != it.toSet().size }) {
            add("The backup has two people with the same name.")
        }
        for (c in s.categories) if (c.name.isBlank()) add("A category has no name.")
        val categoriesById = s.categories.associateBy { it.id }
        for (c in s.categories) {
            if (c.icon.startsWith(EMOJI_ICON_PREFIX) && emojiOf(c.icon) == null) add("Category \"${c.name}\" has an icon that is not one emoji.")
            val parentId = c.parentId ?: continue
            val parent = categoriesById[parentId]
            when {
                parent == null -> add("Category \"${c.name}\" is under a category that is not in the backup.")
                parent.parentId != null -> add("Category \"${c.name}\" is more than one level down.")
                parent.type != c.type -> add("Category \"${c.name}\" is not the same kind as its parent.")
            }
        }

        fun shapeProblem(
            type: TransactionType,
            amount: Long,
            accountId: Long,
            toAccountId: Long?,
            categoryId: Long?,
        ): String? {
            if (amount <= 0) return "has an amount that is zero or negative"
            if (accountId !in accountIds) return "refers to a missing account"
            if (type == TransactionType.Transfer) {
                if (toAccountId == null || toAccountId !in accountIds) return "is a transfer to a missing account"
                if (toAccountId == accountId) return "transfers to the same account"
                if (categoryId != null) return "is a transfer with a category"
            } else {
                if (toAccountId != null) return "has a destination but is no transfer"
                if (categoryId == null || categoryId !in categoryIds) return "refers to a missing category"
                if (categoryTypes[categoryId] != CategoryType.forTransaction(type)) {
                    return "uses a category of the wrong type"
                }
            }
            return null
        }

        for (t in s.transactions) {
            shapeProblem(t.type, t.amount, t.accountId, t.toAccountId, t.categoryId)
                ?.let { add("Transaction #${t.id} $it.") }
            if (t.recurringId != null && t.recurringId !in ruleIds) {
                add("Transaction #${t.id} refers to a missing recurring rule.")
            }
            if (t.personId != null) {
                if (t.type != TransactionType.Expense) add("Transaction #${t.id} names a person but is not an expense.")
                if (t.personId !in personIds) add("Transaction #${t.id} refers to a missing person.")
            }
        }
        for (r in s.recurringRules) {
            shapeProblem(r.type, r.amount, r.accountId, r.toAccountId, r.categoryId)
                ?.let { add("Recurring rule #${r.id} $it.") }
            if (r.interval < 1) add("Recurring rule #${r.id} has an invalid interval.")
            if (r.personId != null) {
                if (r.type != TransactionType.Expense) add("Recurring rule #${r.id} names a person but is not an expense.")
                if (r.personId !in personIds) add("Recurring rule #${r.id} refers to a missing person.")
            }
        }
        val budgetTargets = mutableSetOf<String>()
        for (b in s.budgets) {
            if (b.amount != null && b.amount <= 0) add("Budget #${b.id} has an amount that is zero or negative.")
            if (b.categoryId != null && b.categoryId !in categoryIds) add("Budget #${b.id} refers to a missing category.")
            if (!budgetTargets.add("${b.categoryId}:${b.effectiveMonth.toInt()}")) {
                add("The backup has two budgets for the same month and target.")
            }
        }
        if (s.defaultAccountId != null && s.defaultAccountId !in accountIds) {
            add("The default account in the backup does not exist.")
        }
        return problems
    }

    // ----------------------------------------------------------------- CSV

    /**
     * Transactions as CSV (one row each, newest first as given). Amounts are
     * plain decimals; text cells are quoted and neutralized against formula
     * injection when opened in a spreadsheet.
     */
    fun encodeCsv(s: FinanceSnapshot, formatDate: (LocalDateTime) -> String): String {
        val currency = CurrencySpec.byCode(s.currencyCode)
        val accounts = s.accounts.associate { it.id to it.name }
        val categories = s.categories.associate { it.id to s.categories.pathName(it) }
        val people = s.people.associate { it.id to it.name }
        return buildString {
            appendLine(
                csvRow(listOf("Date", "Type", "Amount", "Currency", "Account", "To account", "Category", "Payee", "Note", "Person")),
            )
            for (t in s.transactions) {
                appendLine(
                    csvRow(
                        listOf(
                            formatDate(t.occurredAt),
                            t.type.key,
                            minorToDecimalString(t.amount, currency.decimals),
                            currency.code,
                            accounts[t.accountId] ?: "",
                            t.toAccountId?.let { accounts[it] } ?: "",
                            t.categoryId?.let { categories[it] } ?: "",
                            t.payee ?: "",
                            t.note ?: "",
                            t.personId?.let { people[it] } ?: "",
                        ),
                    ),
                )
            }
        }
    }

    private fun csvRow(cells: List<String>): String = cells.joinToString(",") { csvCell(it) }

    private val csvSpecial = Regex("[\",\\r\\n]")

    private fun csvCell(value: String): String {
        var text = value
        // Spreadsheet formula-injection guard. Amounts are unaffected: they are
        // always plain positive decimals starting with a digit.
        if (text.isNotEmpty() && text[0] in "=+-@\t\r") text = "'$text"
        if (csvSpecial.containsMatchIn(text)) text = "\"" + text.replace("\"", "\"\"") + "\""
        return text
    }

    // ------------------------------------------------------- JSON readers

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.requireString(key: String, what: String): String =
        string(key) ?: throw BackupFormatError("A $what has an invalid \"$key\".")

    private fun JsonObject.stringOrNull(key: String): String? = string(key)?.takeIf { it.isNotEmpty() }

    private fun JsonObject.requireLong(key: String, what: String): Long =
        (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
            ?: throw BackupFormatError("A $what has an invalid \"$key\".")

    private fun JsonObject.longOrNull(key: String, what: String): Long? {
        val value = this[key]
        if (value == null || value is JsonNull) return null
        return (value as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
            ?: throw BackupFormatError("A $what has an invalid \"$key\".")
    }

    /**
     * A day of the month, or null where the key is missing (a backup from before cards had dates). A number that is no day
     * is kept as one that [validate] will refuse, instead of being wrapped into range by a narrowing conversion.
     */
    private fun JsonObject.dayOrNull(key: String, what: String): Int? =
        longOrNull(key, what)?.coerceIn(-1L, 100L)?.toInt()

    private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true

    private fun JsonObject.date(key: String, what: String): LocalDateTime =
        string(key)?.let(::parseDateTime) ?: throw BackupFormatError("A $what has an invalid date in \"$key\".")

    private fun JsonObject.month(key: String): MonthKey {
        val value = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
        if (value != null && value % 100 in 1..12 && value in 100..999_912) return MonthKey.fromInt(value.toInt())
        throw BackupFormatError("A budget has an invalid month.")
    }

    private fun <T> JsonObject.enumValue(values: List<T>, key: (T) -> String, field: String, what: String): T {
        val value = string(field)
        return values.firstOrNull { key(it) == value } ?: throw BackupFormatError("A $what has an unknown \"$field\".")
    }
}

/**
 * Parses an ISO-8601 moment leniently: local date-times (what NormPix
 * writes), plain dates, and zoned timestamps (converted to local time).
 */
internal fun parseDateTime(text: String): LocalDateTime? {
    val value = text.trim()
    return try {
        when {
            value.endsWith("Z") || value.matches(Regex(".*[+-]\\d\\d:?\\d\\d$")) && value.contains('T') ->
                OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
            value.contains('T') || value.contains(' ') ->
                LocalDateTime.parse(value.replace(' ', 'T'))
            else -> LocalDate.parse(value).atStartOfDay()
        }
    } catch (_: DateTimeParseException) {
        try {
            Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDateTime()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
