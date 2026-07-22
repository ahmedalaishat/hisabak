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

## How it's wired (`src/test/java/com/hisabak/testutil/`)

- **`TestClock`** — a `Clock` with a fixed, mutable instant (UTC) so time-dependent
  logic is deterministic.
- **`MainDispatcherRule`** — swaps `Dispatchers.Main` for a `TestDispatcher` so
  `viewModelScope` coroutines are controllable. Use `advanceUntilIdle()` after sending
  intents.
- **`FakeRepositories.kt`** — in-memory, `StateFlow`-backed fakes for every repository
  interface, plus `RecordingNotifier` and `FakeCategoryLimitAlertDao`. Prefer these over
  a mocking framework; build the real use case around a fake repo.
- **`TestData.kt`** — terse builders (`brand()`, `category()`, `transaction()`, …) with
  sensible defaults.

### Notes

- Domain logic that observes hot flows (e.g. `CategoryLimitMonitor`) is tested with an
  `UnconfinedTestDispatcher` scope so emissions process eagerly; cancel the scope at the
  end of the test.
- ViewModel tests assert on `vm.state.value` / `vm.effect.value` after
  `advanceUntilIdle()`.

## Not yet covered (future passes)

Room DAO tests (in-memory SQLite), Compose UI / navigation tests, screenshot tests,
GitHub Actions CI, and Jacoco coverage.
