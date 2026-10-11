package com.example.normpix.finance.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.normpix.banks.BankCatalog
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.ui.brandNamesToKey
import java.time.LocalDateTime

/**
 * Local SQLite database for the expense tracker (accounts, categories,
 * transactions, budgets, recurring rules, ledger settings).
 *
 * Lives in its own file, separate from the reminders database, so each
 * feature migrates independently. Integrity rules that must never be violated
 * (positive amounts, transfer shape, foreign keys) are enforced here as well
 * as in the repository, so even a bug upstream can't write an impossible
 * ledger.
 *
 * Pass a null [name] for a private in-memory database (tests).
 */
class FinanceDatabase(
    context: Context,
    name: String? = DB_NAME,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() },
) : SQLiteOpenHelper(context.applicationContext, name, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createSchema(db)
        seedDefaults(db, clock())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the first schema. Migrations add `if (oldVersion < N) { ... }`
        // steps here (ALTER TABLE / new tables), never drop-and-recreate, so users
        // keep their financial history.
        if (oldVersion < 2) addBanks(db)
        if (oldVersion < 3) addSubCategories(db)
        if (oldVersion < 4) addCreditLimits(db)
        if (oldVersion < 5) addPeople(db)
        if (oldVersion < 6) addRulePeople(db)
        if (oldVersion < 7) migrateBrandCategoryIcons(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        migrateBrandCategoryIcons(db)
    }

    companion object {
        const val DB_NAME = "normpix_finance.db"
        const val VERSION = 7

        const val ACCOUNTS = "accounts"
        const val PEOPLE = "people"
        const val BANKS = "banks"
        const val CATEGORIES = "categories"
        const val TRANSACTIONS = "transactions"
        const val BUDGETS = "budgets"
        const val RECURRING_RULES = "recurring_rules"
        const val META = "meta"

        fun createSchema(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $ACCOUNTS (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT NOT NULL,
                  kind TEXT NOT NULL,
                  opening_balance INTEGER NOT NULL DEFAULT 0,
                  archived INTEGER NOT NULL DEFAULT 0,
                  sort_order INTEGER NOT NULL DEFAULT 0,
                  created_at INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL,
                  bank_key TEXT,
                  credit_limit INTEGER
                )
                """.trimIndent(),
            )
            createBanksTable(db)
            createPeopleTable(db)
            db.execSQL(
                """
                CREATE TABLE $CATEGORIES (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT NOT NULL,
                  type TEXT NOT NULL CHECK (type IN ('expense', 'income')),
                  icon TEXT NOT NULL,
                  color INTEGER NOT NULL DEFAULT 0,
                  archived INTEGER NOT NULL DEFAULT 0,
                  sort_order INTEGER NOT NULL DEFAULT 0,
                  created_at INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL,
                  parent_id INTEGER REFERENCES $CATEGORIES(id)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX idx_category_parent ON $CATEGORIES(parent_id)")
            db.execSQL(
                """
                CREATE TABLE $RECURRING_RULES (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  type TEXT NOT NULL CHECK (type IN ('expense', 'income', 'transfer')),
                  amount INTEGER NOT NULL CHECK (amount > 0),
                  account_id INTEGER NOT NULL REFERENCES $ACCOUNTS(id),
                  to_account_id INTEGER REFERENCES $ACCOUNTS(id),
                  category_id INTEGER REFERENCES $CATEGORIES(id),
                  payee TEXT,
                  note TEXT,
                  frequency TEXT NOT NULL
                    CHECK (frequency IN ('daily', 'weekly', 'monthly', 'yearly')),
                  interval INTEGER NOT NULL DEFAULT 1 CHECK (interval >= 1),
                  start_date INTEGER NOT NULL,
                  end_date INTEGER,
                  next_date INTEGER NOT NULL,
                  active INTEGER NOT NULL DEFAULT 1,
                  created_at INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL,
                  person_id INTEGER REFERENCES $PEOPLE(id) ON DELETE SET NULL,
                  CHECK (
                    (type = 'transfer' AND to_account_id IS NOT NULL
                       AND to_account_id != account_id AND category_id IS NULL)
                    OR (type != 'transfer' AND to_account_id IS NULL
                       AND category_id IS NOT NULL)
                  )
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE $TRANSACTIONS (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  type TEXT NOT NULL CHECK (type IN ('expense', 'income', 'transfer')),
                  amount INTEGER NOT NULL CHECK (amount > 0),
                  account_id INTEGER NOT NULL REFERENCES $ACCOUNTS(id),
                  to_account_id INTEGER REFERENCES $ACCOUNTS(id),
                  category_id INTEGER REFERENCES $CATEGORIES(id),
                  occurred_at INTEGER NOT NULL,
                  payee TEXT,
                  note TEXT,
                  recurring_id INTEGER REFERENCES $RECURRING_RULES(id) ON DELETE SET NULL,
                  created_at INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL,
                  person_id INTEGER REFERENCES $PEOPLE(id) ON DELETE SET NULL,
                  CHECK (
                    (type = 'transfer' AND to_account_id IS NOT NULL
                       AND to_account_id != account_id AND category_id IS NULL)
                    OR (type != 'transfer' AND to_account_id IS NULL
                       AND category_id IS NOT NULL)
                  )
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX idx_tx_occurred ON $TRANSACTIONS(occurred_at DESC, id DESC)")
            db.execSQL("CREATE INDEX idx_tx_account ON $TRANSACTIONS(account_id, type)")
            db.execSQL("CREATE INDEX idx_tx_to_account ON $TRANSACTIONS(to_account_id)")
            db.execSQL("CREATE INDEX idx_tx_category ON $TRANSACTIONS(category_id, occurred_at)")
            db.execSQL("CREATE INDEX idx_tx_type_occurred ON $TRANSACTIONS(type, occurred_at)")
            db.execSQL("CREATE INDEX idx_tx_recurring ON $TRANSACTIONS(recurring_id)")
            db.execSQL("CREATE INDEX idx_tx_person ON $TRANSACTIONS(person_id, occurred_at)")
            createPersonGuards(db)
            createPersonGuards(db, RECURRING_RULES, "rule")
            db.execSQL(
                """
                CREATE TABLE $BUDGETS (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  category_id INTEGER REFERENCES $CATEGORIES(id) ON DELETE CASCADE,
                  amount INTEGER CHECK (amount IS NULL OR amount > 0),
                  effective_month INTEGER NOT NULL,
                  created_at INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX idx_budget_target_month " +
                    "ON $BUDGETS(COALESCE(category_id, -1), effective_month)",
            )
            db.execSQL("CREATE TABLE $META (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        }

        /** The banks the user added (built-in banks are not stored; accounts refer to them by directory id). */
        private fun createBanksTable(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE $BANKS (key TEXT PRIMARY KEY, name TEXT NOT NULL, created_at INTEGER NOT NULL)")
        }

        /**
         * Version 2: accounts can belong to a bank. Adds the column and the table for the user's own banks, then links the
         * accounts whose name *starts with* a known bank's ("HDFC Savings", "ICICI Credit Card", "Paytm Wallet") - the only
         * evidence there is. Everything else stays unlinked for the user to set when editing; nothing is removed or renamed.
         */
        fun addBanks(db: SQLiteDatabase) {
            db.execSQL("ALTER TABLE $ACCOUNTS ADD COLUMN bank_key TEXT")
            createBanksTable(db)
            val catalog = BankCatalog.BuiltIn
            db.rawQuery("SELECT id, name, kind FROM $ACCOUNTS", null).use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(2) == AccountKind.Cash.key) continue
                    val bank = catalog.leading(cursor.getString(1)) ?: continue
                    db.execSQL("UPDATE $ACCOUNTS SET bank_key = ? WHERE id = ?", arrayOf<Any>(bank.key, cursor.getLong(0)))
                }
            }
        }

        /** The people expenses can be spent on. Names are unique whatever their case, so one person is never two. */
        private fun createPeopleTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $PEOPLE (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT NOT NULL,
                  emoji TEXT,
                  color INTEGER NOT NULL DEFAULT 0,
                  archived INTEGER NOT NULL DEFAULT 0,
                  created_at INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX idx_people_name ON $PEOPLE(name COLLATE NOCASE)")
        }

        /**
         * Only an expense can name a person (spending is what is tracked per person; income and transfers are not). The
         * repository checks this first and says so kindly; the triggers keep a bug upstream from writing it anyway, and they
         * hold for a database that was migrated just as for a new one.
         */
        private fun createPersonGuards(db: SQLiteDatabase, table: String = TRANSACTIONS, prefix: String = "tx") {
            for ((name, event) in listOf("insert" to "INSERT", "update" to "UPDATE OF type, person_id")) {
                db.execSQL(
                    """
                    CREATE TRIGGER trg_${prefix}_person_$name BEFORE $event ON $table
                    WHEN NEW.person_id IS NOT NULL AND NEW.type != 'expense'
                    BEGIN SELECT RAISE(ABORT, 'Only an expense can be linked to a person'); END
                    """.trimIndent(),
                )
            }
        }

        /**
         * Version 5: expenses can be spent on a person. Adds the people table and the (empty) link on transactions - every
         * existing transaction stays exactly as it was, with no one on it - and an index to find a person's expenses by.
         */
        fun addPeople(db: SQLiteDatabase) {
            createPeopleTable(db)
            db.execSQL("ALTER TABLE $TRANSACTIONS ADD COLUMN person_id INTEGER REFERENCES $PEOPLE(id) ON DELETE SET NULL")
            db.execSQL("CREATE INDEX idx_tx_person ON $TRANSACTIONS(person_id, occurred_at)")
            createPersonGuards(db)
        }

        /**
         * Version 6: a recurring payment can be for a person, so what it records is too. Adds the (empty) link - every existing
         * rule stays as it was, for no one - and the same only-an-expense guard the transactions have.
         */
        fun addRulePeople(db: SQLiteDatabase) {
            db.execSQL("ALTER TABLE $RECURRING_RULES ADD COLUMN person_id INTEGER REFERENCES $PEOPLE(id) ON DELETE SET NULL")
            createPersonGuards(db, RECURRING_RULES, "rule")
        }

        /**
         * Version 7: updates categories whose names match known services/brands to use their authentic brand icon key.
         */
        fun migrateBrandCategoryIcons(db: SQLiteDatabase) {
            for ((name, brandKey) in brandNamesToKey) {
                db.execSQL(
                    "UPDATE $CATEGORIES SET icon = ? WHERE LOWER(TRIM(name)) = ? AND icon != ?",
                    arrayOf<Any>(brandKey, name, brandKey),
                )
            }
        }

        /** Version 4: a credit card can have a limit. Adds the (empty) column: every account stays as it was. */
        fun addCreditLimits(db: SQLiteDatabase) {
            db.execSQL("ALTER TABLE $ACCOUNTS ADD COLUMN credit_limit INTEGER")
        }

        /**
         * Version 3: a category can sit under another. Adds the (empty) parent column - every existing category stays
         * top-level, exactly as it was - and an index to find a category's sub-categories by.
         */
        fun addSubCategories(db: SQLiteDatabase) {
            db.execSQL("ALTER TABLE $CATEGORIES ADD COLUMN parent_id INTEGER REFERENCES $CATEGORIES(id)")
            db.execSQL("CREATE INDEX idx_category_parent ON $CATEGORIES(parent_id)")
        }

        /**
         * First-run defaults: one Cash account and a small, editable set of
         * categories. These are starting points, not user data.
         */
        fun seedDefaults(db: SQLiteDatabase, now: LocalDateTime) {
            val stamp = now.toStorage()
            db.insertOrThrow(
                ACCOUNTS,
                null,
                ContentValues().apply {
                    put("name", "Cash")
                    put("kind", "cash")
                    put("opening_balance", 0)
                    put("archived", 0)
                    put("sort_order", 0)
                    put("created_at", stamp)
                    put("updated_at", stamp)
                },
            )
            val parents = mutableMapOf<String, DefaultCategory>()
            val parentIds = mutableMapOf<String, Long>()
            var order = 0
            for (category in defaultCategories) {
                parentIds[category.name] = db.insertOrThrow(
                    CATEGORIES,
                    null,
                    ContentValues().apply {
                        put("name", category.name)
                        put("type", category.type)
                        put("icon", category.icon)
                        put("color", category.color)
                        put("archived", 0)
                        put("sort_order", order++)
                        put("created_at", stamp)
                        put("updated_at", stamp)
                    },
                )
                parents[category.name] = category
            }
            // The ready-made sub-categories under them: a new user starts with a full tree to rename, archive or
            // extend (and the Categories page folds each family away), instead of an empty one to build.
            for ((parentName, subs) in CategorySuggestions.byParent) {
                val parent = parents[parentName] ?: continue
                val parentId = parentIds.getValue(parentName)
                for (sub in subs) {
                    db.insertOrThrow(
                        CATEGORIES,
                        null,
                        ContentValues().apply {
                            put("name", sub.name)
                            put("type", parent.type)
                            put("icon", sub.icon)
                            // A sub-category takes its parent's colour, so a family reads as one.
                            put("color", parent.color)
                            put("archived", 0)
                            put("sort_order", order++)
                            put("created_at", stamp)
                            put("updated_at", stamp)
                            put("parent_id", parentId)
                        },
                    )
                }
            }
            db.insertOrThrow(
                META,
                null,
                ContentValues().apply {
                    put("key", "currency")
                    put("value", "INR")
                },
            )
        }

        data class DefaultCategory(val name: String, val type: String, val icon: String, val color: Int)

        /** name, type, icon key, palette index */
        val defaultCategories = listOf(
            DefaultCategory("Food & dining", "expense", "restaurant", 0),
            DefaultCategory("Groceries", "expense", "grocery", 1),
            DefaultCategory("Transport", "expense", "transport", 2),
            DefaultCategory("Shopping", "expense", "shopping", 3),
            DefaultCategory("Bills & utilities", "expense", "bills", 4),
            DefaultCategory("Rent & housing", "expense", "home", 5),
            DefaultCategory("Health", "expense", "health", 6),
            DefaultCategory("Entertainment", "expense", "entertainment", 7),
            DefaultCategory("Education", "expense", "education", 2),
            DefaultCategory("Travel", "expense", "travel", 5),
            DefaultCategory("Subscriptions", "expense", "subscriptions", 3),
            DefaultCategory("Other", "expense", "other", 4),
            DefaultCategory("Salary", "income", "salary", 1),
            DefaultCategory("Freelance", "income", "work", 0),
            DefaultCategory("Business", "income", "business", 5),
            DefaultCategory("Interest", "income", "interest", 6),
            DefaultCategory("Gifts", "income", "gift", 7),
            DefaultCategory("Other income", "income", "refund", 2),
        )
    }
}
