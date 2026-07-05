# Cashiro — SMS Parsing Gap Closure: Progress & Context

## Overview
Closing the gap between Cashiro and Pennywise SMS parsing systems (without KMP/iOS).

## Files Changed (Complete List)

### Phase 0 — Core Correctness

| Task | Files | Status |
|------|-------|--------|
| **P0-1**: `BALANCE_UPDATE` to parser-core enum | `parser-core/.../TransactionType.kt` | ✓ |
| **P0-2**: `BALANCE_UPDATE` to entity enum + mapper | `app/.../TransactionEntity.kt`, `app/.../ParsedTransactionMapper.kt` | ✓ |
| **P0-3**: Plural parser dispatch | `BankParserFactory.kt`, `SmsTransactionProcessor.kt`, `OptimizedSmsReaderWorker.kt`, `SmsReaderWorker.kt` | ✓ |
| **P0-4**: `reference` field + migration 52→53 | `TransactionEntity.kt`, `ParsedTransactionMapper.kt`, `CashiroDatabase.kt` (version 53, MIGRATION_52_53), `DatabaseModule.kt` | ✓ |
| **Fix**: Exhaustive `when` errors from `BALANCE_UPDATE` | `CsvExporter.kt`, `AiContextRepository.kt`, `AddTransactionUseCase.kt`, `TransactionItem.kt`, `TransactionDetailScreen.kt`, `TransactionDetailViewModel.kt`, `strings.xml` | ✓ |

### Phase 1 — Deduplication Improvements

| Task | Files | Status |
|------|-------|--------|
| **P1-1**: Extract `TransactionDeduplication` class | NEW: `app/.../TransactionDeduplication.kt` + wired into `SmsTransactionProcessor` + `OptimizedSmsReaderWorker` + `SmsReaderWorker` | ✓ |
| **P1-2**: UPI reference dedup (3-min window) | `TransactionDeduplication.kt` (UPI methods), `TransactionDao.kt` (new query), `TransactionRepository.kt` (new method), wired into all 3 save paths | ✓ |
| **P1-3**: Partner bank replacement | `TransactionDeduplication.kt` (`shouldReplaceWithIncoming`), wired into all 3 save paths | ✓ |
| **P1-4**: Wire dedup into worker | (Covered by P1-1/P1-2/P1-3 — all 3 save paths already updated) | ✓ |

### Phase 2 — Duplicate Code Elimination

| Task | Description / Files | Status |
|------|---------------------|--------|
| **P2-1**: Refactor worker to reuse processor | Remove duplicate `saveParsedTransaction()` in `OptimizedSmsReaderWorker`, delegate to `SmsTransactionProcessor` | ✓ |
| **P2-2**: Extract shared `BalanceUpdateProcessor` | NEW: `app/.../BalanceUpdateProcessor.kt` (used by processor and worker) | ✓ |

### Phase 3 — Missing Bank Parsers (31 parsers)

| Group | Parsers | Files | Status |
|-------|---------|-------|--------|
| **P3-A**: Nigerian (5) | AccessBank, ZenithBank, KeystoneBank, JaizBank, OpayBank | `parser-core/.../bank/AccessBankParser.kt` etc. | ✓ |
| **P3-B**: Indian (5) | NSDLPaymentsBank, PunjabSindBank, KeralaBank, Cashfree, NaviMutualFund | `parser-core/.../bank/` + `test/.../IndianBanksParsersTest.kt` | ✓ |
| **P3-C**: Middle East (8) | EmiratesIslamic, SNBAlAhli, STCBank, SabbBank, MellatBank, Bankino, BluBank, ArabBank | `parser-core/.../bank/` | ✓ |
| **P3-D**: Africa (9) | StandardBankMozambique, MillenniumBim, EMola, MPesaMozambique, CrdbBank, DiamondTrust, MixxByYas, NMBTanzania, GreaterBank | `parser-core/.../bank/` | ✓ |
| **P3-E**: Other (4) | SampathBank, EnparaBank, SparkasseRheinMaas, AltanaFCU | `parser-core/.../bank/` | ✓ |

(AUBankParser and EverestBankParser already existed — skipped)

### Phase 4 — Notification Listener Service

| Task | Description / Files | Status |
|------|---------------------|--------|
| **P4-1**: `BankNotificationConfig` | package→alias mapping | ✓ |
| **P4-2**: `BankNotificationRepository` | Repository + DAO + Entity | ✓ |
| **P4-3**: `BankNotificationListenerService` | Service extending `NotificationListenerService` | ✓ |
| **P4-4**: `BankNotificationRetryWorker` | WorkManager retry worker for notification ingestion | ✓ |

### Phase 5 — Worker Pipeline

| Task | Description / Files | Status |
|------|---------------------|--------|
| **P5-1**: Channel-based streaming pipeline | Feed→Parse→Save pipeline in `OptimizedSmsReaderWorker` | ✓ |
| **P5-2**: Smart auto-batching | Message volume-based batching parameter calculations | ✓ |


### Phase 6 — LLM Migration

| Task | Description / Files | Status |
|------|---------------------|--------|
| **P6-1**: Add LiteRT-LM dependency | `libs.versions.toml` + `app/build.gradle.kts` — added `litertlm-android:0.13.1` alongside legacy `tasks-genai` | ✓ |
| **P6-2**: `LiteRtLmServiceImpl` | `data/service/LiteRtLmServiceImpl.kt` — Engine + GPU/CPU fallback + SamplerConfig (topK=10, topP=0.95, temp=0.8) + Flow<Message>→String extraction | ✓ |
| **P6-3**: Hilt binding swap | `di/LlmModule.kt` — switched from `@Binds` abstract to `@Provides` object; selects backend via `BuildConfig.USE_LITERT_LM` (default: `true`) | ✓ |

**Notes:**
- `BuildConfig.USE_LITERT_LM = true` (default) → uses `LiteRtLmServiceImpl` (LiteRT-LM 0.13.1)
- `BuildConfig.USE_LITERT_LM = false` → falls back to `LlmServiceImpl` (MediaPipe tasks-genai)
- GPU (OpenCL) backend is auto-detected at runtime; falls back to CPU if `libOpenCL.so` is absent
- `sendMessageAsync()` returns `Flow<Message>` — text extracted via `Content.Text` filterIsInstance
- Model format: `.litertlm` (e.g. `Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task`)


### Next: Phase 7 — Polish

- P7-1: RCS streaming into channel pipeline
- P7-2: Notification permission flow

## Architecture

```
SMS (BroadcastReceiver / ContentProvider)
  │
  ├── SmsTransactionProcessor (shared save path)
  │     ├── BankParserFactory.getParsers(sender).firstNotNullOfOrNull { it.parse() }
  │     │     └── 134 parsers (103 existing + 31 new)
  │     ├── ParsedTransaction.toEntity()  [mapper with BALANCE_UPDATE + reference support]
  │     ├── TransactionDeduplication.checkHash()  [hash dedup]
  │     ├── TransactionDeduplication UPI dedup  [3-min window + partner bank replacement]
  │     ├── Merchant mapping
  │     ├── Rule engine
  │     ├── Subscription matching
  │     └── BalanceUpdateProcessor.process()
  │
  ├── OptimizedSmsReaderWorker (background scan)
  │     └── Same save pipeline via SmsTransactionProcessor [P2-1]
  │
  └── SmsReaderWorker (legacy, will be deprecated)
        └── Same dedup calls
```

## Key Design Decisions

1. **`TransactionDeduplication` is a plain `object`** (not injectable). Callers pass data or repository references — avoids circular DI issues and keeps it testable with mocks.

2. **`BALANCE_UPDATE` is additive** — it's defined but no parser emits it yet. Parsers need an explicit override to produce it (planned for base class update in future).

3. **Plural dispatch uses `getParsers().firstNotNullOfOrNull { it.parse() }`** — the first parser still handles subscription notifications; all parsers compete for transaction parsing.

4. **`BalanceUpdateProcessor`** is a `@Singleton` with `@Inject constructor` — extracted from both `SmsTransactionProcessor` and the worker for shared card/account balance logic.

5. **Parser bank count**: Cashiro now has ~134 parsers, matching Pennywise coverage.

6. **LLM backend is compile-time toggled** via `BuildConfig.USE_LITERT_LM`. Both implementations remain in the codebase side-by-side. The legacy `tasks-genai` dependency is kept during the migration period.

## Build Status
```bash
./gradlew :app:compileStandardDebugKotlin   # BUILD SUCCESSFUL (Phase 6 ✓)
./gradlew :app:compileStandardReleaseKotlin # BUILD SUCCESSFUL
./gradlew :parser-core:test                 # BUILD SUCCESSFUL
```

