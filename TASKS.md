# NormPix — Kotlin + Jetpack Compose migration (completed)

**Decision (user, this session):** replace the Flutter app with a native
Kotlin + Jetpack Compose app. The Flutter app (`lib/`, `android/`, `test/`)
stays untouched in the repo as the behavioral reference until the Kotlin app
reaches parity; deleting it needs explicit confirmation. The Flutter work
below this section is **parked** (its stale-test cleanup is intentionally not
finished).

The new project lives in `native/` (own Gradle build, AGP 9.1 / Kotlin 2.4 /
Compose BOM 2026.09 / Material 3). Application id `com.example.normpix.kt`
(+ `.debug` / `.qa` suffixes), so installing it can never touch the Flutter
build's data. `FLAG_SECURE` is on in every build type except `qa` (used only
for screenshots). No `INTERNET` permission (stripped from the merged manifest).

Design decisions carried over: integer minor units; balances derived on read;
budgets versioned by month; recurring materialized locally and idempotently;
JSON backup v1 (same format as the Flutter app) + CSV; card numbers never in
backups. New: moments are stored as wall-clock time (no time-zone drift),
SQL needs no `localtime`.

## Kotlin migration checklist
- [x] Gradle project, version catalog, manifest (no network permission), icons, bank SVG assets copied
- [x] Domain: money (deterministic integer formatting, Indian grouping), period, entities, ledger, recurrence — JUnit tests ported
- [x] Data: SQLite schema/constraints/indexes, rows, repository (validation, keyset pagination, aggregates, recurring, restore, reset), backup codec + CSV — 16 Robolectric tests incl. the brief's 6 scenarios + time-zone test
- [x] Design system: tokens, theme (seed palettes via material-kolor, dynamic color), status colors, overlay host (sheets/dialogs), snackbars, haptics, page scaffold, grouped lists, status surface, empty/error/loading
- [x] Finance UI: controller + query helper, transaction form (discard guard), category/account sheets, detail sheet, flows, history (search/filters/pagination), budgets (+history), recurring (+form), categories, insights + charts
- [x] Navigation: root, routes, page transitions (5 styles), adaptive shell (bar/rail, per-tab FAB, keep-alive tabs)
- [x] Home dashboard (balance card, monthly summary, quick actions, budgets preview, upcoming recurring, active reminders, recent transactions, search)
- [x] Reminders: model, repeat rule, DAO, controller, alarms + notifications + boot receiver, sheets, list/alert UI, notification-tap navigation (`container.reminderAlerts`), unit + Robolectric tests
- [x] Wallet: vault (AES-256-GCM), cards controller, bank directory + designs (SVG vectors + presets), card view, add-card sheet, OCR scan, NFC reveal, app lock (biometric session/timeout), wallet screen, unit + Robolectric tests
- [x] Settings: catalog + live search, appearance, finance, data & backup (SAF export/import/erase), wallet, security
- [x] UI flow tests (Compose + Robolectric: dashboard, tab switching, search filtering, full root shell) — 92/92 tests passing (100% success rate)
- [x] `:app:lintDebug` (0 errors), debug + release builds (`:app:assembleRelease` minified, R8/ProGuard optimized), merged-manifest verified (0 network/SMS/listener permissions)
- [x] Final review; ask whether to delete the Flutter app

---

# NormPix — Material 3 revamp + offline expense tracker

Master checklist for two consecutive briefs:

- **Part A** — Material 3 / Essentials-inspired UI revamp (design-system
  foundation for everything else).
- **Part B** — turn NormPix into a fully offline personal expense tracker
  (manual entry only), reusing the existing app.

Items are ticked only after they are implemented **and** verified
(analyzer, tests, and/or a visual check).
Legend: `[x]` done & verified · `[ ]` open · `[~]` partially done (see note)

---

## 0. Baseline (before any change)

- [x] Flutter 3.47.2 / Dart 3.13.2, Android target (plus desktop/web scaffolding)
- [x] `flutter analyze` → no issues; `flutter test` → 80/80 passing
- [x] Snapshot of `lib/`, `test/`, `pubspec.yaml` taken outside the repo for
      the final diff review (repo has no commits yet)
- [x] Test device: motorola edge 50 neo, Android 16, 1200×2670 @ 449 dpi.
      The real NormPix install holds real user data (2 cards, reminders) →
      on-device QA only ever uses a separate `com.example.normpix.qa`
      sandbox build installed with `adb install -r` (never `flutter run`,
      which can uninstall on a signature mismatch). `FLAG_SECURE` stays on
      in the real app.
- [x] Visual QA harness (`tool/qa/visual_qa_test.dart`) renders the real
      shell with fixture data across sizes/themes → `build/qa_screens/`.
      Baseline run found overflow errors on a 360×640 phone (10 screens)
      and at 2× text scale.

## 1. Inventory of the existing app

### Reused as-is (business logic — not modified)
| Area | What it does |
|---|---|
| `reminders/` model, `repeat_rule`, `reminder_actions`, DB/DAO, notification service, controller | Reminder lifecycle, recurrence, scheduling, background actions |
| `wallet/card_vault_service`, `stored_card`, `card_network`, `bank_directory`, `bank_card_design`, `indian_bank_card_length`, `card_scan_service`, `nfc_tag_reveal_service` | AES-256-GCM card vault, card branding, OCR scan, NFC reveal |
| `wallet/app_lock_service` | Biometric session/timeout rules |
| `ThemeController` persistence pattern, `InheritedNotifier` scopes | State management (kept; no new framework) |
| `sqflite` | Local SQL engine — reused for finance (no second DB technology) |
| `MainActivity` `FLAG_SECURE`, `allowBackup=false`, no `INTERNET` permission in the release manifest | Offline + privacy by construction |

### Reused, restyled (UI)
Card faces (`wallet_card_view`), add-card form, reminder forms/sheets,
reminder alert screen, lock gates, settings content.

### Incomplete / broken / fabricated (found in audit)
- Expenses tab: placeholder only, no data model.
- Home search: hard-coded suggestions, results discarded.
- "Display size & text" setting: preview-only, never applied.
- Invented identity ("Pixel User / pixel.device@normpix.local") and version
  ("1.0.0 Build 2026.1"; pubspec is 0.0.5+5).
- Dark mode: dynamic-color `surface` equals `surfaceContainerLow`.
- Reminder actions reachable only by swipe; overflow on small phones.
- Wallet `_load` has no error path (stuck spinner if the vault can't be read).

### Removed (obsolete or conflicting with the brief)
- Chrome-only preferences: dock style, macOS-genie popup motion, frosted
  blur + intensity, back-pill visibility/size/scale, preview-only font size,
  never-used swipe-action/confirm-delete prefs. Their prefs keys become
