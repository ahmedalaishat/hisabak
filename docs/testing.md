# Testing

Hisabak's test safeguard. This first pass covers **pure domain logic and ViewModel
behavior** — the layers where bugs are most likely and cheapest to catch. All tests run
on the plain JVM (no emulator, no Robolectric).

## Running

```bash
./gradlew unitTests                    # run the whole unit suite (androidApp + shared)
./gradlew :androidApp:testProdDebugUnitTest --tests "com.hisabak.feature.*"   # a subset
```

`unitTests` is a root aggregate task: `:androidApp:testProdDebugUnitTest` plus
`:shared:testAndroidHostTest` (which runs the KMP module's `commonTest` +
`androidHostTest` sources on the JVM).

Reports: `androidApp/build/reports/tests/testProdDebugUnitTest/index.html` and
`shared/build/reports/tests/testAndroidHostTest/index.html`.

## Where tests live

- **`shared/src/commonTest/`** — tests for everything in `shared/commonMain` (domain
  entities/use cases, SMS parsing, backup engine policy, seed data, **and all ViewModel
  tests**). Written with **kotlin-test** (`kotlin.test.Test`, `assertEquals`,
  `assertFailsWith`, …) so they also compile for the iOS targets (test names must avoid
  characters Kotlin/Native rejects — `,` `;` `:` — enforced by a fast grep in both the
  Stop hook and the required Unit-tests CI job, since the JVM compiles them fine and only
  the non-required iOS compile job would catch it otherwise); they run on the JVM via
  `:shared:testAndroidHostTest`.
- **`androidApp/src/test/`** — only the JVM-bound tests remain (`AesGcmBackupCryptoTest`,
  `DatabaseDecryptionMigrationTest`, `BackupUseCasesTest` which drives the JVM crypto
  impl). These stay on **JUnit4**.

## What's covered

| Area | Tests |
|------|-------|
| `Money` arithmetic & currency guards | `core/common/MoneyTest` |
| SMS template detection (regex masking, first-match, `ignore`) | `sms/data/parser/RegexSmsTemplateDetectorTest` |
| SMS field parsing (amount/date/time normalization) | `sms/data/parser/TemplateSmsParserTest` |
| Budget window + progress math | `budget/domain/usecase/*Test` |
| SMS → transaction orchestration | `sms/domain/SmsTransactionProcessorTest`, `usecase/IngestSmsUseCaseTest` |
| Capture funnel (per-source side-effect policy) | `sms/domain/capture/CaptureTransactionUseCaseTest` |
| Category-limit alert monitor (thresholds, once-per-month, dips) | `notification/domain/CategoryLimitMonitorTest` |
| Dashboard metric computation | `dashboard/domain/usecase/GetDashboardMetricsUseCaseTest` |
| Misc use cases (find-or-create brand, reassign, set limit) | `*/domain/usecase/*Test` |
| ViewModels (validation, create/update, list actions) | `*/presentation/**/*ViewModelTest` |

## How it's wired (the `:testutil` KMP module)

The shared fakes live in the **`:testutil`** module
(`testutil/src/commonMain/kotlin/com/hisabak/testutil/`) so both `shared/commonTest`
and `androidApp/src/test` can use them (KMP has no multiplatform test-fixtures yet).
They are plain Kotlin — no JUnit — so they compile for every target.

- **`TestClock`** — a `Clock` with a fixed, mutable instant (UTC) so time-dependent
  logic is deterministic.
- **`FakeRepositories.kt`** — in-memory, `StateFlow`-backed fakes for every repository
  interface, plus `RecordingNotifier` and `FakeCategoryLimitAlertStore`. Prefer these
  over a mocking framework; build the real use case around a fake repo.
- **`TestData.kt`** — terse builders (`brand()`, `category()`, `transaction()`, …) with
  sensible defaults.
- **`FakeBackupCrypto`** — a pure-Kotlin stand-in for the JVM-only `AesGcmBackupCrypto`
  with the same observable contract (`HSBK` magic, round-trip, wrong-passphrase error),
  so the backup/restore ViewModel tests run in `commonTest`.

ViewModel tests extend **`MainDispatcherTest`**
(`shared/src/commonTest/kotlin/com/hisabak/testutil/`) — the multiplatform successor to
the JUnit4 `MainDispatcherRule`: `@BeforeTest`/`@AfterTest` swap `Dispatchers.Main` for a
`TestDispatcher` so `viewModelScope` coroutines are controllable. Use
`advanceUntilIdle()` after sending intents.

### Notes

- Domain logic that observes hot flows (e.g. `CategoryLimitMonitor`) is tested with an
  `UnconfinedTestDispatcher` scope so emissions process eagerly; cancel the scope at the
  end of the test.
- ViewModel tests assert on `vm.state.value` / `vm.effect.value` after
  `advanceUntilIdle()`.

## Not yet covered (future passes)

Room DAO tests (in-memory SQLite), Compose UI / navigation tests, screenshot tests,
GitHub Actions CI, and Jacoco coverage.
