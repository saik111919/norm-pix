package com.example.normpix.finance.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.example.normpix.banks.BankCatalog
import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.data.FinanceSnapshot
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.CustomBank
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.RepeatSchedule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FinanceStatus { Loading, Ready, Failed }

/**
 * The small, always-needed reference data (accounts, categories, balances,
 * currency). Everything else - history pages, totals, budgets, charts - is
 * queried from the repository by the screen that shows it.
 */
@Immutable
data class FinanceState(
    val status: FinanceStatus = FinanceStatus.Loading,
    /** Increments after every change to finance data. */
    val revision: Int = 0,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val balances: Map<Long, Long> = emptyMap(),
    val currency: CurrencySpec = CurrencySpec.INR,
    val defaultAccountId: Long? = null,
    /** The banks the user added; see [catalog] for every bank. */
    val customBanks: List<CustomBank> = emptyList(),
    /** Everyone expenses can be spent on, archived ones last. */
    val people: List<Person> = emptyList(),
) {
    val money: MoneyFormatter = MoneyFormatter(currency)

    /** Every bank accounts and cards can belong to: the built-in directory plus the user's own. */
    val catalog: BankCatalog by lazy(LazyThreadSafetyMode.NONE) { BankCatalog(customBanks) }

    val activeAccounts: List<Account> get() = accounts.filter { !it.archived }

    fun account(id: Long?): Account? = id?.let { wanted -> accounts.firstOrNull { it.id == wanted } }

    fun category(id: Long?): Category? = id?.let { wanted -> categories.firstOrNull { it.id == wanted } }

    fun person(id: Long?): Person? = id?.let { wanted -> people.firstOrNull { it.id == wanted } }

    /** The people a new expense can be spent on. */
    val activePeople: List<Person> get() = people.filter { !it.archived }

    fun categoriesOf(type: CategoryType, includeArchived: Boolean = false): List<Category> =
        categories.filter { it.type == type && (includeArchived || !it.archived) }

    fun balanceOf(accountId: Long): Long = balances[accountId] ?: 0

    /** Sum of every account's balance (archived accounts still hold money). What is owed counts against it. */
    val totalBalance: Long get() = balances.values.sum()

    /** What the accounts hold: the balances above zero, added up. */
    val heldBalance: Long get() = balances.values.filter { it > 0 }.sum()

    /** What is owed - a card's bill, a loan: the balances below zero, added up, as a positive amount. */
    val owedBalance: Long get() = -balances.values.filter { it < 0 }.sum()

    /**
     * The account new transactions start with: the chosen default if it's
     * still active, otherwise the first active account.
     */
    val defaultAccount: Account?
        get() {
            val chosen = account(defaultAccountId)
            if (chosen != null && !chosen.archived) return chosen
            return activeAccounts.firstOrNull()
        }
}

/**
 * Presentation-side entry point to the ledger. Every mutation goes through
 * here and bumps [FinanceState.revision], which makes every visible query
 * re-read from the database: one source of truth, no hand-synchronized
 * derived state.
 */
class FinanceController(val repository: FinanceRepository) {
    private val _state = MutableStateFlow(FinanceState())
    val state: StateFlow<FinanceState> = _state.asStateFlow()

    /** Records due recurring transactions, then loads reference data. */
    suspend fun load() {
        try {
            repository.processRecurring()
            reload(FinanceStatus.Ready)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.value = _state.value.copy(status = FinanceStatus.Failed)
        }
    }

    /** Catches recurring transactions up (e.g. when the app resumes). */
    suspend fun refreshRecurring() {
        if (_state.value.status != FinanceStatus.Ready) return load()
        if (repository.processRecurring() > 0) reload(FinanceStatus.Ready)
    }

    private suspend fun reload(status: FinanceStatus) {
        val accounts = repository.accounts()
        val categories = repository.categories()
        val balances = repository.accountBalances()
        val defaultAccountId = repository.defaultAccountId()
        val code = repository.currencyCode()
        val customBanks = repository.customBanks()
        val people = repository.people()
        val previous = _state.value
        _state.value = previous.copy(
            status = status,
            revision = previous.revision + 1,
            accounts = accounts,
            categories = categories,
            balances = balances,
            defaultAccountId = defaultAccountId,
            customBanks = customBanks,
            people = people,
            currency = if (code != previous.currency.code) CurrencySpec.byCode(code) else previous.currency,
        )
    }

    private suspend fun <T> mutate(op: suspend (FinanceRepository) -> T): T {
        val result = op(repository)
        reload(FinanceStatus.Ready)
        return result
    }

    // ---------------------------------------------------------------- writes

    suspend fun saveTransaction(t: FinanceTransaction): FinanceTransaction = mutate { it.saveTransaction(t) }

    suspend fun deleteTransaction(id: Long) = mutate { it.deleteTransaction(id) }

    suspend fun saveAccount(a: Account): Account = mutate { it.saveAccount(a) }

    /** The key of the bank called [name] - an existing one if it matches, otherwise a new one the user adds. */
    suspend fun bankKeyFor(name: String): String = mutate { it.bankKeyFor(name) }

    suspend fun setAccountArchived(id: Long, archived: Boolean) = mutate { it.setAccountArchived(id, archived) }

    suspend fun deleteAccount(id: Long) = mutate { it.deleteAccount(id) }

    suspend fun savePerson(p: Person): Person = mutate { it.savePerson(p) }

    suspend fun setPersonArchived(id: Long, archived: Boolean) = mutate { it.setPersonArchived(id, archived) }

    /** Deletes the person; their expenses stay and name no one. Returns how many that was. */
    suspend fun deletePerson(id: Long): Int = mutate { it.deletePerson(id) }

    suspend fun saveCategory(c: Category): Category = mutate { it.saveCategory(c) }

    /** Adds the ready-made sub-categories that are missing; returns how many. */
    suspend fun addSuggestedSubCategories(): Int = mutate { it.addSuggestedSubCategories() }

    suspend fun setCategoryArchived(id: Long, archived: Boolean) = mutate { it.setCategoryArchived(id, archived) }

    suspend fun deleteCategory(id: Long) = mutate { it.deleteCategory(id) }

    suspend fun setBudget(categoryId: Long? = null, from: MonthKey, amount: Long?) =
        mutate { it.setBudget(categoryId, from, amount) }

    /**
     * Saves the rule, then immediately records any occurrences already due
     * (e.g. a rule that started last month).
     */
    suspend fun saveRecurringRule(rule: RecurringRule): RecurringRule = mutate {
        val saved = it.saveRecurringRule(rule)
        it.processRecurring()
        saved
    }

    /** Saves [t] as the first of a new repeating payment (see [FinanceRepository.saveTransactionAndRepeat]), recording any repeats already due. */
    suspend fun saveTransactionAndRepeat(t: FinanceTransaction, schedule: RepeatSchedule): FinanceTransaction = mutate {
        val (saved, _) = it.saveTransactionAndRepeat(t, schedule)
        it.processRecurring()
        saved
    }

    suspend fun deleteRecurringRule(id: Long) = mutate { it.deleteRecurringRule(id) }

    suspend fun setCurrency(code: String) = mutate { it.setCurrency(code) }

    suspend fun setDefaultAccountId(id: Long?) = mutate { it.setDefaultAccountId(id) }

    suspend fun restore(snapshot: FinanceSnapshot) = mutate { it.restore(snapshot) }

    suspend fun resetAll() = mutate { it.resetAll() }
}

val LocalFinance = compositionLocalOf<FinanceController> { error("No FinanceController provided") }

/** The current finance reference data, recomposing when it changes. */
@Composable
fun rememberFinanceState(): FinanceState = LocalFinance.current.state.collectAsState().value

/** Explicit async state (instead of isLoading/hasError/hasData flags). */
sealed interface AsyncValue<out T> {
    data object Loading : AsyncValue<Nothing>

    data class Data<T>(val value: T) : AsyncValue<T>

    data class Failure(val error: Throwable) : AsyncValue<Nothing>
}

/**
 * Runs [query] against the finance repository and exposes its result. Re-runs
 * automatically whenever finance data changes ([FinanceState.revision]) or
 * [key] changes, keeping the previous result available while refreshing so
 * updates don't flash a spinner.
 */
@Composable
fun <T> rememberFinanceQuery(key: Any?, query: suspend FinanceRepository.() -> T): State<AsyncValue<T>> {
    val finance = LocalFinance.current
    val revision = finance.state.collectAsState().value.revision
    val result = remember { mutableStateOf<AsyncValue<T>>(AsyncValue.Loading) }
    val retry = remember { mutableStateOf(0) }
    LaunchedEffect(revision, key, retry.value) {
        try {
            result.value = AsyncValue.Data(finance.repository.query())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            result.value = AsyncValue.Failure(e)
        }
    }
    return result
}
