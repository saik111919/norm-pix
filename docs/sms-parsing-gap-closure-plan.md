# SMS Parsing: Gap Closure Plan

## Goal
Bring Cashiro's SMS parsing system to parity with Pennywise while deferring KMP/iOS support.

## How to Use This Document
Each entry below is a **self-contained task** that can be implemented, tested, and merged independently within a single commit. Tasks within a phase **have no hard ordering** unless noted with "prerequisite" or "blocked by".

---

## Phase 0 — Low-Hanging Fruit (Single-File Changes)

### P0-1: Add `BALANCE_UPDATE` to parser-core `TransactionType.kt`

**Files:**
- `parser-core/src/main/kotlin/com/ritesh/parser/core/TransactionType.kt`

**Change:**
```diff
 enum class TransactionType {
-    INCOME, EXPENSE, CREDIT, TRANSFER, INVESTMENT
+    INCOME, EXPENSE, CREDIT, TRANSFER, INVESTMENT, BALANCE_UPDATE
 }
```

**Test:** Verify `TransactionType.valueOf("BALANCE_UPDATE")` succeeds.
**Risk:** None — additive change, no enum is consumed by exhaustive `when` in parser-core.

---

### P0-2: Add `BALANCE_UPDATE` to entity `TransactionType` + update mapper

**Files:**
- `app/src/main/java/com/ritesh/cashiro/data/database/entity/TransactionEntity.kt` (entity-level enum)
- `app/src/main/java/com/ritesh/cashiro/data/mapper/ParsedTransactionMapper.kt`

**Changes:**
1. Add `BALANCE_UPDATE` to the entity `TransactionType` enum in `TransactionEntity.kt`
2. Add the mapping case in `ParsedTransactionMapper.kt`:
   - In `toEntity()` — add `BALANCE_UPDATE` entry to the `when` block
   - In `toEntityType()` — add `BALANCE_UPDATE` entry

**Test:** Verify mapper converts `com.ritesh.parser.core.TransactionType.BALANCE_UPDATE` → `com.ritesh.cashiro.data.database.entity.TransactionType.BALANCE_UPDATE`.

**Note:** At this point `BALANCE_UPDATE` is defined but never *emitted* by any parser. That's intentional — the parser base class needs a follow-up.

---

### P0-3: Plural parser dispatch (getParser → getParsers)

**Files:**
- `parser-core/src/main/kotlin/com/ritesh/parser/core/bank/BankParserFactory.kt`
- `app/src/main/java/com/ritesh/cashiro/data/manager/SmsTransactionProcessor.kt`
- `app/src/main/java/com/ritesh/cashiro/worker/OptimizedSmsReaderWorker.kt`

**Changes:**
1. **`BankParserFactory.kt`:** Add `getParsers(sender): List<BankParser>` (keeps `getParser()` for backward compat)
2. **`SmsTransactionProcessor.kt`:** Change from `getParser(sender)?.parse(...)` to `getParsers(sender).firstNotNullOfOrNull { it.parse(...) }`
3. **`OptimizedSmsReaderWorker.kt`:** Same change in both the main path and the subscription notification path

**Test:** Verify that when multiple parsers match the same sender, content decides the winner.

---

### P0-4: Add `reference` field to `TransactionEntity`

**Files:**
- `app/src/main/java/com/ritesh/cashiro/data/database/entity/TransactionEntity.kt`
- `app/src/main/java/com/ritesh/cashiro/data/database/dao/TransactionDao.kt` (if DAO queries reference it)
- `app/src/main/java/com/ritesh/cashiro/data/mapper/ParsedTransactionMapper.kt`

**Change:**
```diff
 @ColumnInfo(name = "to_account") val toAccount: String? = null,
+@ColumnInfo(name = "reference") val reference: String? = null,
 @ColumnInfo(name = "billing_cycle") val billingCycle: String? = null,
```

In mapper: `reference = reference` (the field already exists on ParsedTransaction).

**DB migration:** Add `ALTER TABLE transactions ADD COLUMN reference TEXT` (nullable, no default needed).

**Why:** Prerequisite for UPI dedup — the dedup logic matches on `reference`.

---

## Phase 1 — Deduplication Improvements

### P1-1: Extract `TransactionDeduplication` class (inline → dedicated)

**Files:**
- NEW: `app/src/main/java/com/ritesh/cashiro/data/manager/TransactionDeduplication.kt`
- `app/src/main/java/com/ritesh/cashiro/data/manager/SmsTransactionProcessor.kt`
- `app/src/main/java/com/ritesh/cashiro/worker/OptimizedSmsReaderWorker.kt`

**Change:**
1. Create `TransactionDeduplication` singleton with the existing hash-based dedup logic, extracted from the inline code in `SmsTransactionProcessor.saveParsedTransaction()` and `OptimizedSmsReaderWorker.saveParsedTransaction()`
2. Both callers delegate to `TransactionDeduplication.isDuplicate(hash)` instead of inline checks

**Test:** Same behavior — pure extraction, no logic change.

---

### P1-2: Add UPI reference dedup (3-minute window)

**Files:**
- `app/src/main/java/com/ritesh/cashiro/data/manager/TransactionDeduplication.kt`

**Change:**
Add `isSameUpiTransaction()` method:
- Transactions must have matching `reference` (12-digit UPI ref pattern)
- Same amount, transaction type, currency, and account
- Within 3-minute window

Add `duplicateIdsToDelete()` method:
- Groups by `reference + amount + account + type + currency`
- Within each group, clusters by 3-minute window
- Prefers non-SBI entries (SBI often double-reports)
- Returns IDs of inferior duplicates

**Test:** Unit test with known UPI double-reporting scenarios.

**Prerequisite:** P0-4 (`reference` field on entity).

---

### P1-3: Add partner bank replacement logic

**Files:**
- `app/src/main/java/com/ritesh/cashiro/data/manager/TransactionDeduplication.kt`
- `app/src/main/java/com/ritesh/cashiro/data/manager/SmsTransactionProcessor.kt`

**Change:**
When saving a transaction, check if an existing UPI duplicate exists where:
- The existing is from SBI and the incoming is from a non-SBI bank (→ replace the existing with the incoming)
- OR the existing lacks `balanceAfter` while incoming has it (→ upgrade)

**Logic:** `shouldReplaceWithIncoming(existing, incoming): Boolean`

**Test:** Scenario: SBI sends debit notification first, then the actual UPI app sends credit notification — the SBI entry should be replaced.

---

### P1-4: Wire dedup into worker's save path

**Files:**
- `app/src/main/java/com/ritesh/cashiro/worker/OptimizedSmsReaderWorker.kt`

**Change:**
Replace the inline hash-check in `OptimizedSmsReaderWorker.saveParsedTransaction()` with calls to `TransactionDeduplication`, including UPI window dedup and partner bank replacement. Currently the worker and `SmsTransactionProcessor` have parallel save logic — both need the same upgrade.

---

## Phase 2 — Duplicate Code Elimination

### P2-1: Refactor worker to reuse `SmsTransactionProcessor.saveParsedTransaction()`

**Files:**
- `app/src/main/java/com/ritesh/cashiro/worker/OptimizedSmsReaderWorker.kt`
- `app/src/main/java/com/ritesh/cashiro/data/manager/SmsTransactionProcessor.kt`

**Change:**
The `OptimizedSmsReaderWorker` has its own `saveParsedTransaction()` (lines ~1024–1150) that duplicates `SmsTransactionProcessor.saveParsedTransaction()`. Refactor the worker to inject and call `SmsTransactionProcessor.saveParsedTransaction()` instead.

**Exceptions:** The worker's `processBalanceUpdate()` handles balance differently (batch async) — that portion stays. Everything else (hash dedup, merchant mapping, rules, subscriptions) should delegate.

**Test:** Run both SMS flows (real-time via receiver, background via worker) and verify identical save behavior.

---

### P2-2: Extract `processBalanceUpdate()` into shared utility

**Files:**
- NEW: `app/src/main/java/com/ritesh/cashiro/data/manager/BalanceUpdateProcessor.kt`
- `app/src/main/java/com/ritesh/cashiro/data/manager/SmsTransactionProcessor.kt`
- `app/src/main/java/com/ritesh/cashiro/worker/OptimizedSmsReaderWorker.kt`

**Change:**
Extract the balance update logic (card vs account detection, available limit calculation, upserting balance records) into a dedicated class. Both `SmsTransactionProcessor` and the worker use it.

---

## Phase 3 — Missing Bank Parsers (by Region)

### P3-A: Nigerian Banks (5 parsers)

**Files (new, all under `parser-core/src/main/kotlin/.../bank/`):**
- `AccessBankParser.kt`
- `ZenithBankParser.kt`
- `KeystoneBankParser.kt`
- `JaizBankParser.kt`
- `OpayBankParser.kt`

**Registration:** Add all 5 to `BankParserFactory.parsers` list.

**Tests:** Each parser gets a test class following the `ParserTestUtils` pattern in `docs/parser-test-standards.md`.

---

### P3-B: Additional Indian Banks (6 parsers)

**Files (new):**
- `NSDLPaymentsBankParser.kt`
- `AU Small Finance Bank` → `AUBankParser.kt`
- `PunjabSindBankParser.kt`
- `KeralaBankParser.kt`
- `CashfreeParser.kt`
- `NaviMutualFundParser.kt`

**Registration:** Add to `BankParserFactory.parsers`.
**Tests:** Unit tests per parser.

---

### P3-C: Middle East Expansion (8 parsers)

**Files (new):**
- `EmiratesIslamicParser.kt` (UAE)
- `SNBAlAhliBankParser.kt` (Saudi Arabia)
- `STCBankParser.kt` (Saudi Arabia)
- `SabbBankParser.kt` (Saudi Arabia)
- `MellatBankParser.kt` (Iran)
- `BankinoBankParser.kt` (Iran)
- `BluBankParser.kt` (Iran)
- `ArabBankParser.kt` (Egypt)

**Registration:** Add to `BankParserFactory.parsers`.
**Tests:** Unit tests per parser.

---

### P3-D: Africa Expansion (9 parsers)

**Files (new):**
- `MPesaMozambiqueParser.kt` (Mozambique)
- `StandardBankMozambiqueParser.kt` (Mozambique)
- `MillenniumBimParser.kt` (Mozambique)
- `EMolaParser.kt` (Mozambique)
- `CrdbBankParser.kt` (Tanzania)
- `DiamondTrustBankParser.kt` (Tanzania)
- `MixxByYasParser.kt` (Tanzania)
- `NMBTanzaniaParser.kt` (Tanzania)
- `GreaterBankParser.kt` (regional)

**Registration:** Add to `BankParserFactory.parsers`.
**Tests:** Unit tests per parser.

---

### P3-E: Other Regions (5 parsers)

**Files (new):**
- `SampathBankParser.kt` (Sri Lanka)
- `EnparaBankParser.kt` (Turkey)
- `SparkasseRheinMaasParser.kt` (Germany)
- `EverestBankParser.kt` (Nepal)
- `AltanaFCUParser.kt` (US Credit Union)

**Registration:** Add to `BankParserFactory.parsers`.
**Tests:** Unit tests per parser.

---

## Phase 4 — Notification Listener Service

### P4-1: Add `BankNotificationConfig` object

**Files:**
- NEW: `app/src/main/java/com/ritesh/cashiro/receiver/BankNotificationConfig.kt`

**Change:**
Create config mapping package names to sender aliases (matching existing bank parser `canHandle()` values):
- `com.avanza.ambitwizfbl` → `FaysalBank`
- `finansbank.enpara` / `com.enparabank.retail` → `Enpara`

Includes `isAllowed(packageName): Boolean`, `senderAlias(packageName): String`, and `extractMessage(notification): String`.

---

### P4-2: Add `BankNotificationRepository` + DAO

**Files:**
- NEW: `app/src/main/java/com/ritesh/cashiro/data/database/dao/BankNotificationDao.kt`
- NEW: `app/src/main/java/com/ritesh/cashiro/data/database/entity/BankNotificationEntity.kt`
- NEW: `app/src/main/java/com/ritesh/cashiro/data/repository/BankNotificationRepository.kt`
- Modified: `app/src/main/java/com/ritesh/cashiro/data/database/CashiroDatabase.kt` (add entity + DAO)

**Change:**
Room entity to log incoming notifications and track their processing status (processed, skipped, failed). DAO with insert and mark-processed queries.

---

### P4-3: Implement `BankNotificationListenerService`

**Files:**
- NEW: `app/src/main/java/com/ritesh/cashiro/receiver/BankNotificationListenerService.kt`
- Modified: `app/src/main/AndroidManifest.xml`

**Change:**
1. Service extending `NotificationListenerService`
2. Hilt `@EntryPoint` to get `SmsTransactionProcessor`
3. On `onNotificationPosted()`:
   - Check `BankNotificationConfig.isAllowed(packageName)`
   - Extract message body from notification extras
   - Cross-dedup with existing SMS transactions (±2-min window by amount)
   - Call `SmsTransactionProcessor.processAndSaveTransaction()`
4. Manifest: add `<service>` declaration with `BIND_NOTIFICATION_LISTENER_SERVICE` permission

**Test:** Manual — send test notifications from a white-listed package.

---

### P4-4: Add `BankNotificationRetryWorker`

**Files:**
- NEW: `app/src/main/java/com/ritesh/cashiro/worker/BankNotificationRetryWorker.kt`

**Change:**
WorkManager worker that retries unprocessed notifications from `BankNotificationRepository`. Enqueued when `processAndSaveTransaction()` fails.

---

## Phase 5 — Worker Pipeline Optimization

### P5-1: Channel-based streaming pipeline

**Files:**
- `app/src/main/java/com/ritesh/cashiro/worker/OptimizedSmsReaderWorker.kt`

**Change:**
Refactor from batch-read-then-process to a 3-stage channel pipeline:

```
Stage 1 (Feed, 1 coroutine)       → Stage 2 (Parse, N coroutines)  → Stage 3 (Save, 1 coroutine)
streamSmsToChannel()                 parseSms() in parallel            sequential save + balance
streamRcsToChannel()
```

**Details:**
- Stage 1: streams SMS + RCS messages into a `Channel<SmsMessage>` (from `Telephony.Sms.CONTENT_URI` and MMS provider)
- Stage 2: N = `availableProcessors - 1` coroutines each pull from channel, parse via parser, emit `ParseResult` (Transaction / Discard / Unrecognized / SpecialNotification)
- Stage 3: Single coroutine receives results, saves sequentially to avoid DB races

**Prerequisites:** P2-1 (worker reuses `SmsTransactionProcessor`) and P2-2 (shared balance processor) — makes Stage 3 cleaner.

---

### P5-2: Smart auto-batching based on message volume

**Files:**
- `app/src/main/java/com/ritesh/cashiro/worker/OptimizedSmsReaderWorker.kt`

**Change:**
Currently `calculateOptimalParallelism()` returns 1. After P5-1's channel pipeline, parallelism is inherently safe (balance updates are serialized separately). Remove the hard-coded sequential override.

**Batch sizes** (for reading, not parsing):
- <100 messages → 10
- <500 → 25
- <2000 → 50
- ≥2000 → 200

---

## Phase 6 — LLM Modernization

### P6-1: Add LiteRT-LM dependency

**Files:**
- `app/build.gradle.kts`

**Change:**
Replace `com.google.mediapipe:tasks-genai` with `com.google.ai.edge.litert:litert-lm` (or add alongside during migration).

---

### P6-2: Implement `LiteRtLmServiceImpl`

**Files:**
- NEW / modified: `app/src/main/java/com/ritesh/cashiro/data/service/LlmServiceImpl.kt`

**Change:**
Implement a second implementation of `LlmService` using the LiteRT-LM API:
- `Engine` / `EngineConfig` (CPU backend, cache dir)
- `Conversation` / `ConversationConfig` with `SamplerConfig` (topK=10, topP=0.95, temperature=0.8)
- `sendMessageAsync()` streaming via `Flow<String>`
- Optional GPU acceleration (`libOpenCL.so`)

---

### P6-3: Swap DI binding (feature-flag controlled)

**Files:**
- `app/src/main/java/com/ritesh/cashiro/di/LlmModule.kt`

**Change:**
Add a feature flag or BuildConfig toggle to switch between `LlmInference`-based and LiteRT-LM-based implementations. Default to LiteRT-LM once validated.

---

## Phase 7 — Polish & Hardening

### P7-1: Add RCS streaming to channel pipeline

**Files:**
- `app/src/main/java/com/ritesh/cashiro/worker/OptimizedSmsReaderWorker.kt`

**Change:**
Integrate RCS reading into the Stage-1 channel stream (currently both workers have separate blocking RCS methods). Stream RCS messages into the same channel as SMS.

### P7-2: Bank notification listener permission flow

**Files:**
- `app/src/main/AndroidManifest.xml`
- NEW: settings UI or onboarding step

**Change:**
Add runtime permission screen that guides users to Settings → Notification Access → enable Cashiro. This is required for `NotificationListenerService` to work — it's not a standard Android runtime permission.

---

## Implementation Order (Recommended)

```
Phase 0 (P0-1, P0-2, P0-3, P0-4)     → Immediate: core correctness, no regressions
    ↓
Phase 1 (P1-1, P1-2, P1-3, P1-4)     → Fewer duplicates, cleaner data
    ↓
Phase 2 (P2-1, P2-2)                 → DRY up save logic before adding more parsers
    ↓
Phase 3 (P3-A through P3-E)          → Expand coverage (any order within phase)
    ↓
Phase 4 (P4-1, P4-2, P4-3, P4-4)     → New capability: notification ingestion
    ↓
Phase 5 (P5-1, P5-2)                 → Performance: parallel pipeline
    ↓
Phase 6 (P6-1, P6-2, P6-3)          → Tech refresh: newer LLM API
    ↓
Phase 7 (P7-1, P7-2)                → Polish
```

Each phase can be started independently once its prerequisites (listed above) are met. Phases 3, 4, 5, and 6 have no interdependencies after Phase 2 — they can be worked on in parallel by different developers.

---

## Dependency Graph (Brief)

```
P0-1 (no deps)
P0-2 (depends: P0-1)
P0-3 (no deps)
P0-4 (no deps)

P1-1 (no deps, pure extraction)
P1-2 (depends: P0-4, P1-1)
P1-3 (depends: P1-1)
P1-4 (depends: P1-1, P1-2, P1-3)

P2-1 (depends: P1-1, P1-2, P1-3 — want dedup settled before refactor)
P2-2 (depends: P2-1)

P3-A..E (each parser is independent, no deps)

P4-1 (no deps)
P4-2 (no deps)
P4-3 (depends: P4-1, P4-2, P2-1 — wants clean save path)
P4-4 (depends: P4-2, P4-3)

P5-1 (depends: P2-1, P2-2)
P5-2 (depends: P5-1)

P6-1 (no deps)
P6-2 (depends: P6-1)
P6-3 (depends: P6-2)

P7-1 (depends: P5-1)
P7-2 (depends: P4-3)
```
