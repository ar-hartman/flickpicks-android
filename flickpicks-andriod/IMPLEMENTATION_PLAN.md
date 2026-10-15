# FlykPicks Android — Implementation Plan

Tracks the port of FlykPicks iOS to native Android, phase by phase.

**Sources of truth**

| What | Where |
|---|---|
| Android spec | `../../iOS/FlykPicks-iOS/ANDROID_BLUEPRINT.md` (cited below as §n) |
| iOS source (wins when the blueprint is silent or ambiguous) | `../../iOS/FlykPicks-iOS/FlykPicks-iOS/` (`Models/`, `API/`, `Service/`, `Stores/`, `Views/`, `Components/`, `Styles/`) |
| Supporting docs | `../../iOS/FlykPicks-iOS/` — `FEATURE-INVENTORY-FlykPicks.md`, `ARCHITECTURE-FlykPicks.md`, `PRD-FlykPicks.md`, `GAP-ANALYSIS-FlykPicks.md`, `docs/OPEN_QUESTIONS.md`, `docs/IOS-FEATURE-018-api-spec.md` |

**How to use this file**

- Work one phase at a time, in order; later phases depend on earlier ones. A phase can be split across sessions.
- Tick tasks as they land (`- [x]`) and update the status table.
- A phase is **done** when every "Done when" item holds **and** `./gradlew assembleDebug testDebugUnitTest` is green.
- Any deviation from the blueprint gets a row in [Decisions](#decisions--deviations). Resolve [Open questions](#open-questions) before or during the phase that needs them, and record the answer inline.

Status legend: ✅ done · 🟡 in progress · ⬜ not started · ⛔ blocked

---

## Status

| # | Phase | Status | Depends on |
|---|---|---|---|
| 0 | [Project foundation](#phase-0--project-foundation) | ✅ | — |
| 1 | [Core data & networking](#phase-1--core-data--networking) | ✅ | 0 |
| 2 | [Design system components](#phase-2--design-system-components) | ✅ | 0 |
| 3 | [App shell & navigation](#phase-3--app-shell--navigation) | ✅ | 1, 2 |
| 4 | [Authentication flows](#phase-4--authentication-flows) | 🟡 | 3 |
| 5 | [Library](#phase-5--library) | 🟡 | 4 |
| 6 | [Search & discovery](#phase-6--search--discovery) | 🟡 | 5 |
| 7 | [Profile root & feature flags](#phase-7--profile-root--feature-flags) | ⬜ | 4 |
| 8 | [Security settings](#phase-8--security-settings) | ⬜ | 7 |
| 9 | [Payments, addresses & membership](#phase-9--payments-addresses--membership) | ⬜ | 7 |
| 10 | [Orders & disputes](#phase-10--orders--disputes) | ⬜ | 9 |
| 11 | [Seller, insights, referral & notifications](#phase-11--seller-insights-referral--notifications) | ⬜ | 7 |
| 12 | [Barcode scanning](#phase-12--barcode-scanning) | ⬜ | 6 |
| 13 | [Telemetry & impressions](#phase-13--telemetry--impressions) | ⬜ | 6 |
| 14 | [Hardening & release readiness](#phase-14--hardening--release-readiness) | ⬜ | all |

Phases 1 and 2 can run in parallel. After Phase 4, phases 5–6 (content) and 7–11 (profile) are independent tracks.

---

## Phase 0 — Project foundation

- [x] Convert the Android Studio template (Java, `com.example.flick_picks_android`) to Kotlin + Jetpack Compose with package and applicationId `com.flykpicks.android`
- [x] Gradle: AGP 9.4.1 built-in Kotlin, Kotlin 2.4.20, Compose BOM 2026.09.00, KSP 2.3.12, Hilt 2.60.1
- [x] Version catalog declares the full §1 stack (Retrofit/OkHttp/Moshi, Coil, Navigation, Hilt, CameraX, ML Kit, ZXing, Stripe, Biometric, Browser, security-crypto), so dependency conflicts show up now
- [x] `BuildConfig.API_BASE_URL` per build type and `BuildConfig.STRIPE_PUBLISHABLE_KEY`, read from `local.properties` or env vars (see [Working notes](#working-notes))
- [x] Cleartext HTTP allowed **only in debug builds** (`src/debug` network security config), because the dev API is plain HTTP
- [x] `@HiltAndroidApp FlykPicksApp` (Stripe init, skipped when no key is configured), `@AndroidEntryPoint MainActivity`, edge-to-edge
- [x] Design tokens: `FlykColors`, `FlykTypography`, `FlykShapes`, `DS` spacing, `FlykPicksTheme` (§3.1–3.4, §3.7)
- [x] Window theme on the cream background (no white flash before Compose draws)
- [x] Template Java tests replaced with Kotlin tests (`AppConfigTest`, `AppContextTest`)
- [x] Smoke test: the debug APK installs and launches on the API 37 emulator

**Done when:** ✅ `assembleDebug` + `testDebugUnitTest` green; app launches and shows the placeholder wordmark screen.

---

## Phase 1 — Core data & networking

Blueprint §6, §7, §8.1–8.2, §16, Appendix C. No UI. **Wire formats follow the iOS source, not the blueprint.** The blueprint's errors are listed in `ios-gaps.md` → "ANDROID_BLUEPRINT.md corrections".

**Models** (`model/…`)
- [x] Resolved Q1–Q3 (and Q4, Q6–Q8, Q12) from the iOS source
- [x] Auth models + `LoginOutcome`, `AuthError` (`deviceID` / `userID` keys; absent optional fields omitted)
- [x] Profile, MFA (+ `MFAError`), sessions/devices
- [x] Orders (+ `OrderError.map409`), disputes, payment, addresses, membership (+ hard-coded tier catalog), notifications
- [x] Seller, insights, referral, feature flags (`FeatureFlagKey` checked against iOS)
- [x] Inventory/media DTOs, `MediaItemImpl`, `LibraryItemImpl`; SCREAMING_SNAKE enums with `fromRaw()` + iOS fallbacks
- [x] `PaginatedResponse`, `ApiErrorEnvelope`, `ApiError`, `UserFacingError` / `userFacingMessage`
- [x] Mappers: `InventoryTitleDTO.toLibraryItems()`, media DTO → `MediaItemImpl` (two genres joined with " • ")

**JSON**
- [x] Moshi codegen only (no reflection); camelCase properties + `@Json(name = …)` (D10)
- [x] `MediaItem` adapter: picks movie vs. TV by shape, trying `media_type` first
- [x] `@Lenient` lists (skip malformed elements); applies at the Retrofit method level
- [x] `PublicNameSource` falls back on unknown values; other enums stay `String` on the wire and use `fromRaw()` fallbacks

**HTTP**
- [x] `ApiClient` builders shared by Hilt and tests; `BASIC` logging with the token header redacted, debug only
- [x] `TelemetryInterceptor` + `TelemetrySession` (second-precision `X-Flyk-Client-Ts`, skipped for `/media/poster`)
- [x] `AuthInterceptor`; `@Unauthenticated` endpoints never get a bearer token or trigger a refresh
- [x] `TokenAuthenticator` (D2, D14): single-flight refresh on a separate client, one retry per request, `token_revoked` / rejected refresh → clear the session + `SessionEvents`
- [x] `ApiErrorCallAdapterFactory` + `ApiErrorMapper`: every call fails with `ApiError` (429 deadline clamped to 1 s–15 min, empty 2xx → `Decoding`, `Unit` endpoints accept 204)
- [x] Per-endpoint typed errors in the services, matching iOS (`AuthError`, `MFAError`, `OrderError`, `MembershipError`, `PaymentError`, `AddressError`, `BarcodeServiceError`)
- [x] Retrofit interfaces for every endpoint iOS calls: all of §16 plus `/v1/media/{id}`, `/v1/media/tv/{id}/seasons`, `/v1/search/barcode`, `/v1/media/submissions`, `/v1/telemetry/impressions`
- [x] `X-Flyk-Source` values from iOS (`recommendation.*`, `library.detail`, `search.*`)

**Storage & identity**
- [x] `TokenStore` (iOS key names; atomic writes; `clear()` keeps device trust) over `SecureStore`
- [x] `KeystoreSecureStore`: AES-256-GCM, Keystore key, pref key bound in as AAD, unreadable values dropped (D3)
- [x] Secure prefs excluded from backup and device transfer
- [x] `DeviceIdProvider` (ANDROID_ID, falling back to a persisted UUID; "Manufacturer Model (Android N)" name), injected

**DI**
- [x] `AppModule` (Clock, IO dispatcher, SecureStore, DeviceIdProvider), `NetworkModule`, `ServiceModule`; services are `@Inject` singletons
- [x] Hilt test runner + `DependencyGraphTest` (injects every service on a device)

**Tests**: 77 JVM tests (MockWebServer against the production stack) + 3 instrumented test classes
- [x] Headers: bearer, telemetry (and the poster exclusion), `X-Flyk-Source`, relative paths under the base URL
- [x] Refresh: one refresh shared by concurrent 401s, retry at most once, revoked / rejected / 5xx-refresh behavior, unauthenticated 401s
- [x] Error mapping: envelope, raw body, empty, all three 429 deadline sources + clamping, decoding, offline
- [x] Wire format: key names, null omission, renamed booleans, enum fallbacks, `MediaItem` shapes, lenient lists via Retrofit
- [x] Services: login/MFA/trust handling, OTP/recovery mapping, logout, MFA disable, orders, membership, payments, barcode, library payloads, genres
- [x] `KeystoreSecureStore` on a device: round trip, ciphertext not readable, tamper and swap detection

**Done when:** ✅ every endpoint is callable through an injected service; unit and instrumented tests pass.

**Carried forward:** the library fetch reads only page 1, like iOS (ios-gaps G16) → Phase 5.

---

## Phase 2 — Design system components

Blueprint §3.5–3.6, §11.1–11.16, §11.19. **Visuals follow the iOS source, not the blueprint**; the blueprint's errors are listed in `ios-gaps.md` → "ANDROID_BLUEPRINT.md corrections". Each component has a `@Preview`.

- [x] Font decision (D12): the platform serif, as iOS uses the system serif
- [x] Palette corrected to the iOS `DS` values; iOS text styles as `FlykType` (1pt = 1sp)
- [x] Textile background and ink-paper texture (§3.5–3.6, `ui/theme/Textures.kt`). On iOS, ink-paper is the TopBand's texture, not a text effect
- [x] `DecoTextField`, `DecoSecureField`, `DecoSearchField` (§11.1–11.3): autofill content types, show/hide password, IME action hook
- [x] `DecoButton`, `DecoCTAButton` (primary / danger = ember, loading) (§11.4–11.5)
- [x] `Modifier.decoCard()`, `DecoSectionHeader` (§11.7–11.8), plus `ErrorBanner` and `BrassRule`, which the auth and profile screens use
- [x] `DecoAuthScreen` (§11.9): top-aligned like iOS, `imePadding`, 400dp max width
- [x] `AsyncPosterImage` (§11.10): Coil 3 on the shared connection pool, iOS placeholder and backfill retries, token only to the API origin (D19)
- [x] `WordmarkHeader`, `TopBand` (D18), `HeaderStats` (§11.14–11.16)
- [x] `SearchRowPosterCard`, `LibraryRowPosterCard` (long-press "Remove from library"), `SearchGenreRow`, `LibraryGenreRow` (§11.11–11.13)
- [x] `NavRow` (§10.8): icon, label, chevron; null `onClick` gives the inert "Appearance" style. No badge: iOS has none
- [x] `ToastStore` (2 s, as on iOS) + `ToastOverlay` (§9.7, §11.19)
- [x] Not ported, because iOS never uses them: `DecoFrame`, `DecoSelectorButton`, `PosterHeaderView` (D17)
- [x] Side-by-side with iOS: the login-screen components against the iOS app on an iPhone 16 simulator (matching metrics); the rest on the emulator against the iOS source, since other iOS screens need a signed-in session

**Done when:** ✅ every component renders in previews; `ui/gallery/ComponentGallery.kt` shows them all together and is what the app launches into until Phase 3.

**Tests:** 14 new JVM tests (91 total): poster URL resolution, backfill retry timing, the image client's token scope and refresh, toast timing.

---

## Phase 3 — App shell & navigation

Blueprint §4. **Behavior follows iOS `RootView`** where it differs from §4.

- [x] `NavRoutes` (string routes, D7), adjusted to the screens iOS actually has (D21)
- [x] `RootNavigation`: auth graph while signed out, tabbed app while signed in; each side owns its NavController, so switching discards the other's back stack
- [x] `AuthViewModel` shell state (activity-scoped, iOS `AuthStore`): session restored from `TokenStore`, `SessionEvents` → Login with "Your session expired…", tab requests, backup-code alert. Phase 4 adds the sign-in calls
- [x] `MainScaffold`: iOS-style ink tab bar (Library / Discover / Profile, D23); one NavHost with a nested graph per tab
- [x] Tabs keep their back stacks except Discover, which resets when you leave it (as on iOS); re-selecting a tab pops it to its first screen
- [x] Placeholder screen for every route (`PlaceholderScreens.kt`, `MainGraphs.kt`), plus placeholder sign-in / sign-out / alert / toast triggers. The Phase 2 gallery is reachable from the placeholder Profile
- [x] Overlays: toast 31dp above the tab bar (iOS position), backup-code `AlertDialog` ("Go to Security" switches to Profile)
- [x] `pendingTabSelection` → programmatic tab switch
- [x] Force logout from the network layer returns to Login (`AuthViewModelTest`)
- [x] System bar icons follow the surface behind them: `TopBand` and the tab bar set light icons, `DecoTopBar` and the auth screens dark (`SystemBars.kt`)
- [x] Back: pops within the tab, then returns to Library, then leaves the app. Pushes slide like iOS; tab switches are instant
- [x] `DecoTopBar` for pushed screens (D22)

**Done when:** ✅ every route is reachable with placeholder auth, and tab / back-stack behavior matches iOS (`MainNavigationTest`, 7 on-device tests; checked by hand on the API 37 emulator).

**Tests:** 6 JVM (`AuthViewModelTest`, 97 total) + 7 instrumented (`MainNavigationTest`, 14 total). AndroidX Test bumped to Espresso 3.7.0 / ext-junit 1.3.0: the template's Espresso 3.5.1 crashes on API 37.

---

## Phase 4 — Authentication flows

Blueprint §8.3, §8.5, §8.6, §9.1, §10.1–10.4. **Screens and behavior follow the iOS views** (`LoginView`, `MFAChallengeView`, `RegistrationView`, `ForgotPassword*View`).

- [x] `AuthViewModel` (activity-scoped, iOS `AuthStore`): login, MFA verify / cancel, logout, session restore and expiry, tab requests, backup-code alert. Field text is Compose state so typing stays synchronous; the Phase 3 placeholders are gone
- [x] Registration, recovery and the MFA reset request have their own ViewModels (D24)
- [x] Login: email → Next → password → Go, error banner, rate-limit countdown "Try again in Ns" (iOS counts seconds, D25), stored trust token sent, "Forgot password?" only when the cached flag is on (Q4)
- [x] MFA challenge (Q13): fixed subtitle, one free-text field (whitespace stripped, no auto-submit), 30-day "remember this device" toggle, a wrong code clears the field and keeps the challenge, invalid / expired / locked returns to Login with the message, back cancels
- [x] Backup-code alert (Q14) after every backup-code sign-in
- [x] Collect `SessionEvents` → Login with "Your session expired. Please sign in again." (Phase 3)
- [x] MFA reset sheet ("Can't access any of your methods?"): same confirmation whether or not the account exists; state cleared when the sheet closes
- [x] Registration → "Check Your Email" (no auto-login), resend with its own countdown, "Back to sign in" pre-fills Login's email
- [x] Email verification link handling (Q9): not handled in the app, as on iOS. `AuthService.verifyEmail` stays unused
- [x] Forgot password: one route with steps (email → 6-digit code, auto-submit → new password → done); back walks the steps; too many attempts locks with "Start Over". No resend on the code step, as on iOS (D25)
- [x] Logout: revokes the session, signs out locally even if the call fails, keeps device trust, clears the fields. There are no per-user stores yet; Phase 5 adds the reset rule (ios-gaps G21)
- [x] `FeatureFlagStore` (cached flags, refreshed after sign-in); Phase 7 builds on it
- [x] Fixed: the fields' clear (×) and show-password buttons took keyboard focus, so with a hardware keyboard Enter on Email moved onto × and the next Enter cleared the email. They're out of the focus order now (`DecoFieldsTest`)
- [x] Tests for every `LoginOutcome` / error branch and the other flows, against the production stack and a mock server
- [ ] Check against the dev API. It was unreachable on 2026-10-08; every flow was clicked through on the emulator against a scripted mock instead (see Working notes)

**Done when:** login (with and without MFA), registration, recovery and logout all work against the dev API. Everything but the dev-API run holds.

**Tests:** 36 new JVM tests (135 total): `AuthViewModelTest`, `AuthFlowViewModelsTest`, `FeatureFlagStoreTest`, plus a path-routing MockWebServer helper (`Routes`). 2 new instrumented tests (`DecoFieldsTest`, 16 total).

---

## Phase 5 — Library

Blueprint §9.2, §10.7, §11.13, §11.18, Appendix A. **Screens and behavior follow the iOS views** (`LibraryView`, `LibraryItemDetailView`, `TVSeasonDetailView`, `EditLibraryItemSheet`); where iOS has a bug, Android does what it intends (D30). Paging and payloads were checked against the backend source (`UserInventoryController`).

- [x] `GenreStore` wrapping `GenreService.load()`: loads once, callers arriving mid-load wait for it, a failure is silent (unknown genres show "—") and the next caller retries. Loaded on sign-in and on a signed-in cold start; every library load waits for it (ios-gaps G23)
- [x] Library pagination: the server gives 20 titles a page by default and 100 at most, so the fetch reads every page at `limit=100` (ios-gaps G16)
- [x] Per-user state resets when the session changes hands: stores implement `PerUserState`, bound into a Hilt set that `AuthViewModel` resets on sign-out, on session expiry and on sign-in; a result still in flight for the old account is dropped (ios-gaps G21). Only the library is in the set so far: Phases 6 and 7 add theirs
- [x] `LibraryStore` (app-wide, D28) + `LibraryViewModel`: load, `item(id)`, `items(forTitleId)`, `isSeasonOwned`; edit and remove show at once and roll back on failure (D29); seasons update; add from search (used by Phase 6)
- [x] `LibraryScreen`: title filter as you type (ios-gaps G22), `HeaderStats`, genre rows, and one of loading / error with retry / empty / no results (ios-gaps G26). Reloads each time it comes back into view, as on iOS
- [x] Remove item: long-press → confirmation dialog; a refusal puts the copy back and says why
- [x] `LibraryItemDetailScreen` (Q5): backdrop (`AsyncDetailImage`), overview, copies with appraisal, and for a series its seasons with "Add All Seasons". Closes itself when the title has left the library (ios-gaps G27)
- [x] `TVSeasonDetailScreen`: which copies own the season; add it to a copy or take it off one; taking off a copy's last season removes the copy, after a confirmation
- [x] `EditLibraryItemSheet`: format and condition menus, Save, "Remove from library" behind a confirmation; errors show in the sheet; it can't be dismissed mid-request
- [x] Every change the server accepts emits `LibraryStore.changes` (iOS `onDidChangeLibrary`). Phase 6 has recommendations mark `LIBRARY_CHANGED` stale from it
- [x] Fixed along the way: the poster fallback URL sent `mediaID` where the server takes `flyksId` (copied from iOS in Phase 2, ios-gaps G25); the detail image now carries the telemetry headers the backend counts detail views by
- [ ] Check against the dev API, with Phase 4's. Every flow was clicked through on the emulator against a scripted mock instead

**Done when:** you can view, filter, edit, re-season and remove library items, and the list stays consistent when the network fails. Everything but the dev-API run holds.

**Tests:** 60 new JVM tests (195 total): `LibraryStoreTest` (paging, optimistic changes and rollback, loads racing changes, account switches), `GenreStoreTest`, `LibraryViewModelTest`, `LibraryItemDetailViewModelTest`, plus additions to `AuthViewModelTest`, `ImageClientTest`, `PosterLoadingTest` and `MappingTest`. 13 new on-device tests (30 total): `LibraryScreensTest`, and the per-user set in `DependencyGraphTest`. `MainNavigationTest` now runs the shell over a stand-in Library graph (D31).

---

## Phase 6 — Search & discovery

Blueprint §9.3–9.4, §10.5–10.6, §11.11–11.12, §11.17. **Screens and behavior follow the iOS views** (`SearchView`, `SearchItemDetailView`, `AddToLibrarySheet`) and stores (`RecommendationsStore`, `SearchStore`, `SearchScreenState`); where iOS has a bug, Android does what it intends (D33).

- [x] `RecommendationsStore` (app-wide, D32): the 8 rows fetched in parallel after the genres; popular / top-rated kept for the session, "Because You Own It" reloaded on library changes, "Based on Searches" after each search. `loadIfNeeded` (callers mid-load wait), `refresh` (pull to refresh), `refreshSection` (a row's Retry), `markStale`. Each row keeps only its latest fetch's answer; search-history rows leave out what the matching "Because You Own It" row shows
- [x] `LibraryStore.changes` and `SearchStore.completions` → `markStale`. Like iOS, a stale row reloads right away (the blueprint says on next appearance); a change during the first load re-asks the affected rows (ios-gaps G35). `LibraryViewModel.load` starts `loadIfNeeded` after the library, as iOS `LibraryView` does
- [x] Per-user: `RecommendationsStore` and `SearchStore` are in the `PerUserState` set. Titles are mapped only after `GenreStore.loadIfNeeded()`
- [x] `SearchStore` (results, iOS `SearchStore`) + `SearchViewModel` (query, has-searched, iOS `SearchScreenState`). The detail stack is the Discover tab's back stack; leaving the tab pops it and ends the ViewModel, which clears the results (D32)
- [x] `SearchScreen`: the 8 rows (skeletons until the first answer, "Nothing to show yet.", "Couldn't load this section." + Retry), pull to refresh; searching spinner; results by genre; "No results found"; a failed search shows why with "Try Again" (iOS says "No results found", ios-gaps G5)
- [x] `X-Flyk-Source` on every search / recommendation / detail call: rows send their `recommendation.*` source, and a title opened from a row sends it with its attribution call, backdrop and seasons (checked request by request against the mock)
- [x] `SearchItemDetailScreen`: backdrop, title, "date • genres", type chip, "Overview:", "Add To Inventory"; seasons are fetched for the add sheet (iOS shows no season list here). The attribution call (`fetchMediaItem`) runs in the application scope and never blocks the screen. "In Your Library" chip when a copy is owned (not on iOS; D33). After process death the title comes from that call
- [x] `AddToLibrarySheet`: format and condition; for a series, "All Seasons" or pick → confirm → a format and condition per season, sent as one copy per format and condition → `LibraryStore.addFromSearchItem`. If a later copy fails, the seasons already added leave the selection (iOS re-adds them, ios-gaps G34). Format shows display names (iOS shows raw values, G33)
- [x] Searching marks `SEARCH_PERFORMED` stale (only a search the server answered, as iOS)
- [x] Shared with the Library: `groupByPrimaryGenre`, `LoadErrorNotice` / `EmptyNotice`, `GroupedCard` / `GroupedDivider`, `DetailHeroBanner`, `PosterRowFrame`, and the edit sheet's sheet parts (`LibraryItemSheet`, `SheetTopBar`, `SheetTitleCard`, `ValueMenu`, …)
- [ ] Check against the dev API, with Phases 4–5's. Every flow was clicked through on the emulator against a scripted mock instead

**Done when:** you can discover titles, search, open details and add to the library from search, and the library and recommendations update. Everything but the dev-API run holds.

**Tests:** 38 new JVM tests (233 total): `RecommendationsStoreTest` (parallel load, sources, staleness and its races, dedupe, reset), `SearchViewModelTest`, `SearchItemDetailViewModelTest` (attribution, seasons, process death, every add path incl. partial failure), one in `LibraryViewModelTest`. 12 new on-device tests (42 total): `SearchScreensTest`, and the per-user set in `DependencyGraphTest`. `MainNavigationTest` now runs the shell over stand-in Library and Discover graphs (D31).

---

## Phase 7 — Profile root & feature flags

Blueprint §9.5–9.6, §10.8, §12.

- [ ] Feature flags in Profile: `FeatureFlagStore` (Phase 4) already caches, refreshes after sign-in and reads a missing key as `false`; also refresh when Profile appears, as iOS does
- [ ] `ProfileViewModel`: profile + membership fetched in parallel, JSON cache loaded on init. The cache is per-user: add it to the `PerUserState` set and key it by user id (ios-gaps G21)
- [ ] `ProfileScreen`: header card (flag), Account / Activity / Preferences / Support sections, each row gated by its flag; logout with confirmation
- [ ] Edit profile (names, display name, public-name source; "display name taken" error)
- [ ] Avatar upload (Android photo picker → multipart) and remove
- [ ] `AppearanceScreen` placeholder, `HelpSupportScreen` (Q5)

**Done when:** profile rows show and hide according to server flags, and profile editing and avatar changes round-trip.

---

## Phase 8 — Security settings

Blueprint §8.4, §10.9–10.12, §11.21.

- [ ] `SecurityScreen` sections (§10.9)
- [ ] Change-password sheet; delete-account dialog (password confirmation → logout)
- [ ] MFA status card
- [ ] `MFASetupScreen` SMS: phone → code (resend, rate limit) → backup codes
- [ ] `MFASetupScreen` TOTP: QR (`MFAQRImage` via ZXing) + manual secret → code → backup codes; regenerate secret
- [ ] Disable MFA with password (wrong password / last method / not enrolled errors)
- [ ] Backup codes: regenerate, view-once display with copy; block screenshots on that screen (`FLAG_SECURE`)
- [ ] Active sessions: list, revoke (not the current one), revoke all others
- [ ] Trusted devices: list, remove one or all; clear local trust when it's this device
- [ ] Biometrics, if iOS uses them (Q10)

**Done when:** every MFA enrollment and disable path, plus session and device management, works against the dev API.

---

## Phase 9 — Payments, addresses & membership

Blueprint §10.13–10.16, §15.

- [ ] Payment methods: list, set default, swipe-to-delete with confirmation
- [ ] Add a payment method: SetupIntent → Stripe `PaymentSheet` (setup mode) → refresh; failure shows a toast
- [ ] Payouts: status/balance; onboarding opens a Chrome Custom Tab, status refreshes on return
- [ ] Addresses: list (default badge, edit, delete with confirmation), form (create/edit, validation, set default)
- [ ] Membership: current plan card, tier cards (Q12), change plan, cancel, billing history

**Done when:** you can add a card with a Stripe test card, manage addresses end to end, and change plans.

---

## Phase 10 — Orders & disputes

Blueprint §10.18–10.21, Appendix B.

- [ ] Shared `PaginatedListState` + load-more (Appendix B)
- [ ] `OrderHistoryScreen`: type and status filter chips, paginated `OrderCard`s with color-coded status
- [ ] `OrderDetailScreen`: price breakdown, tracking, rental info + extend dialog, timeline stepper
- [ ] File a dispute from an order (rule for when it's allowed + sheet contents) (Q5)
- [ ] `DisputeListScreen`: status filters, pagination
- [ ] `DisputeDetailScreen`: order summary, details, photo pager, message thread + reply, accept/escalate with confirmation

**Done when:** you can browse orders and disputes with pagination, extend rentals, and message on and resolve disputes.

---

## Phase 11 — Seller, insights, referral & notifications

Blueprint §10.17, §10.22–10.24.

- [ ] `SellerDashboardScreen`: stats grid, recent reviews with star rating
- [ ] `CollectionInsightsScreen`: summary, format/genre bar charts (Compose Canvas), rarest-items row
- [ ] `ReferralScreen`: copy code/link, Android share sheet, stats
- [ ] `NotificationsScreen`: master push toggle + per-category push/email switches with optimistic updates
- [ ] Push delivery decision (Q11): FCM setup + `POST_NOTIFICATIONS` runtime permission

**Done when:** every flagged profile destination is functional.

---

## Phase 12 — Barcode scanning

Blueprint §11.20, §14.

- [ ] `CAMERA` permission in the manifest, requested at runtime when the barcode button is tapped, with a rationale screen and a "denied permanently" path
- [ ] `BarcodeScannerView`: CameraX preview + ML Kit (EAN-13, UPC-A, ISBN), targeting overlay, haptic on detection, no duplicate detections
- [ ] Barcode button beside the search field (iOS `barcode.viewfinder`, brass); Phase 6 left it out rather than show a dead button
- [ ] `BarcodeFlowSheet` from `SearchScreen`; `BarcodeService.lookupBarcode` → detail with `source = "search.barcode"`, or the unknown-title submission form (`submitUnknownTitle`). Both endpoints are pending on the backend (iOS DEP-12)

**Done when:** scanning a real disc barcode opens the matching title (once DEP-12 ships).

---

## Phase 13 — Telemetry & impressions

Blueprint §13.

- [ ] Session UUID per app launch shared by every request (wired in Phase 1, checked here)
- [ ] Row visibility tracking on recommendation rows (`RecommendationRow`): item IDs seen, scroll depth, dwell time
- [ ] `ImpressionBatcher` (iOS behavior): flush at 50 events, on a 30-second timer, on leaving the Search tab and on `ON_STOP`; a 404 disables it for the session; retry a batch once with the same `eventId`s. Cap the retry queue (unlike iOS, ios-gaps G4)
- [x] Endpoint + DTOs: `POST /v1/telemetry/impressions` (`TelemetryService`, Phase 1)

**Done when:** impressions arrive server-side in batches with correct sources.

---

## Phase 14 — Hardening & release readiness

- [ ] Release build with R8: verify Moshi/Retrofit/Stripe/ML Kit keep rules; smoke-test the minified APK
- [ ] Trim dependencies: replace `material-icons-extended` with the icons actually used (debug APK is ~130 MB unminified today)
- [ ] App icon and splash screen from iOS `Assets.xcassets`
- [ ] Accessibility: content descriptions, 48dp touch targets, font scaling, TalkBack pass
- [ ] Tablet/large screen: max content widths, landscape
- [ ] Process death and config changes: `SavedStateHandle` for in-progress flows (MFA, recovery, forms)
- [ ] Check whether the signed-in shell's ViewModels are cleared on sign-out. Its NavController is dropped with the shell, and its back stack entries may keep their ViewModels until the activity dies. (Per-user data is safe either way: it lives in the stores that sign-out resets.)
- [ ] Offline / `NetworkUnavailable` UX consistent across screens
- [ ] Instrumented UI tests for critical paths (login + MFA, add to library, logout)
- [ ] CI: build + unit tests + lint on PRs
- [ ] Release signing, versioning, Play Console data-safety answers

**Done when:** a signed release build passes a full manual regression against the iOS feature inventory.

---

## Decisions & deviations

Changes from the blueprint, and choices it leaves open.

| # | Decision | Why | Status |
|---|---|---|---|
| D1 | Colors live in `object FlykColors` rather than top-level vals | Top-level `Canvas` / `Surface` vals collide with the Compose `Canvas()` / `Surface()` composables | Done |
| D2 | Token refresh uses an OkHttp `Authenticator` with a dedicated refresh client, not the §7.3 `runBlocking` interceptor | §7.3 references an un-injected `authApi`, calls `DeviceIdProvider.getDeviceId()` without a context, and would send the refresh through the same intercepted client | Done |
| D3 | Secure token storage: Android Keystore AES-GCM wrapper (`KeystoreSecureStore`) instead of `EncryptedSharedPreferences`; `security-crypto` dependency removed | `androidx.security:security-crypto` 1.1.0 deprecates EncryptedSharedPreferences. It sits behind the `SecureStore` interface, so it's a one-class swap if you'd rather keep ESP. | Done — **awaiting your confirmation** |
| D4 | Leave out `READ_PHONE_STATE` and `USE_FINGERPRINT` (Appendix D) | `ANDROID_ID` needs no permission; READ_PHONE_STATE triggers a runtime prompt and Play review. USE_FINGERPRINT is deprecated in favor of USE_BIOMETRIC. `CAMERA` / `USE_BIOMETRIC` get added in the phases that use them. | Done |
| D5 | Coil 3 (`io.coil-kt.coil3`) instead of Coil 2; `zxing:core` instead of `zxing-android-embedded` | Coil 3 is current. Only QR *generation* is needed; the embedded library is a scanner UI. | Done |
| D6 | Retrofit 3 / OkHttp 5 | Current stable versions | Done |
| D7 | String routes as in §4.2 | Follows the blueprint. Navigation 2.10 type-safe routes are an option if argument handling gets painful. | Planned (P3) |
| D8 | Package / applicationId `com.flykpicks.android` (replaced the template's `com.example.flick_picks_android`); Gradle root project name left as `Flick-Picks-Android` | Matches blueprint §2 | Done |
| D9 | API base URL per build type from `local.properties` / env; cleartext only in debug | Mirrors the iOS Debug/Release xcconfigs without allowing HTTP in release | Done |
| D10 | Kotlin camelCase properties + `@Json(name = …)` for snake_case JSON keys | Idiomatic Kotlin; the blueprint copies raw JSON names into properties | Done |
| D11 | AGP 9 built-in Kotlin: never apply `org.jetbrains.kotlin.android` in a module; KGP is pinned through the root build file | AGP 9 compiles Kotlin itself and errors if the plugin is applied | Done |
| D12 | Display font = platform serif | iOS uses the system serif (`design: .serif`), not a custom face; the blueprint suggests Playfair Display / Libre Baskerville. Swapping is one line in `Type.kt`. | Done |
| D13 | Light-only color scheme | Blueprint §3.7. Confirm iOS has no dark mode. | Done |
| D14 | A failed token refresh ends the session only when the server *rejects* it (4xx). Offline, 5xx or an unreadable body keep the session and surface the original 401. | iOS logs the user out on any refresh error, including offline (ios-gaps G2) | Done |
| D15 | Where iOS has an outright bug, Android does what iOS evidently intends: 429 → `OTPLocked` / `SmsResendRateLimited` (G1), lenient search (G15), empty-body-tolerant recovery calls (G14), logout clears tokens even when the server call fails. Display-level quirks (G11 fallbacks, G12 message matching) are kept for parity. | Keeps the apps consistent for users without copying crashes or dead code | Done |
| D16 | Wire DTOs keep server enums as `String` with computed enum accessors (as iOS does), except `PublicNameSource` | An unknown server value can never fail a whole response | Done |
| D17 | Components iOS defines but never uses aren't ported: `DecoFrame`, `DecoSelectorButton`, `PosterHeaderView`, `DecoTitle`, `LibraryFilterSelector`, `LibraryMediaCard`, `SearchMediaCard` | No iOS screen references them, so there's nothing to match; add one if a later phase needs it | Done |
| D18 | `TopBand` = status-bar inset + 28dp of navy; content starts 52dp below the status bar (`TopBandDefaults`) | iOS draws a fixed 84pt from the screen top, which covers the wordmark on short status bars (ios-gaps G19). 28dp matches what current iPhones show. | Done |
| D19 | Images: Coil 3 with `PosterNetworkClient` (iOS 202/404 retries, 204 = no poster) on an OkHttp client that shares the base pool, sends the token only to the API origin and refreshes on 401. Coil's memory + disk caches. | iOS sends the token to any poster host (G17) and caches in memory only; failures and pending posters are never cached | Done |
| D20 | Material's surface-container colors set to the creams | Defaults are lavender-tinted and leaked into menus; dialogs and sheets use them too | Done |
| D21 | Routes differ from §4.2 where iOS differs: `library/detail/{titleId}` (iOS keys detail by title) plus a TV-season child; Change Password and File Dispute are screens; MFA setup takes no method; no Trusted Devices route (iOS shows them inside Active Sessions); File Dispute is `profile/file_dispute` so it can't match `profile/disputes/{disputeId}` | Routes mirror the iOS navigation | Done |
| D22 | Pushed screens use one inline `DecoTopBar` (back arrow, centered title on cream) | iOS shows large titles on a few profile screens; one bar keeps Android consistent. Revisit per screen if needed. | Done |
| D23 | Tab bar as iOS configures it: ink background, brass-25% top hairline, brass selected / brass-45% unselected, label "Discover", 49dp. Signing in always starts on Library. | §4.3's navy/cream bar and "Search" label aren't what iOS does. iOS keeps the last tab across sign-out (ios-gaps G20). | Done |
| D24 | `AuthViewModel` holds app-wide auth (login, MFA, logout, session). Registration, password recovery and the MFA reset request have their own ViewModels instead of §9.1's pass-through methods. | Each flow's state survives rotation and dies with its screen; iOS keeps that state in the views | Done |
| D25 | Auth UX follows iOS where the plan or blueprint differ: countdowns read "Try again in Ns" (not X:XX); no resend on the recovery code step (go back a step instead); the MFA reset sheet shows any error, not only 429 | iOS behavior | Done |
| D26 | `-Pflyk.apiBaseUrl.debug=…` (and the other `flyk.*` keys) override `local.properties` for one build | Lets a build target a local mock or another server without editing `local.properties` | Done |
| D27 | The library detail screens and the edit sheet use iOS's grouped-list colors (gray page, white cards) at their light-mode values: `FlykColors.GroupedBackground` / `GroupedCard` / `GroupedSeparator` | That is what those iOS screens show; they don't use the Deco creams (and break in dark mode, ios-gaps G28). Three tokens in `Color.kt` if you'd rather have creams. | Done — **awaiting your confirmation** |
| D28 | The library lives in a `@Singleton LibraryStore`, not only in §9.2's `LibraryViewModel`. `LibraryViewModel` and `LibraryItemDetailViewModel` hold what belongs to a screen; the season screen shares the title screen's ViewModel | The Library tab's three screens and the Discover tab all read and change the same list (iOS `LibraryStore` is app-wide too). The season screen needs the seasons the title screen fetched, and fetching them again would count a second detail view on the backend | Done |
| D29 | Edits and removals show at once as pending edits laid over the server's last list; a failure drops the edit. Requests run in the application scope. `updateItem` is optimistic too (iOS waits for the server on an edit) | A saved copy of the list to restore can bring back stale data when a load or another change lands in between. A screen closing mid-request can't leave a change half applied | Done |
| D30 | Library behavior follows what iOS intends where its code falls short: the filter filters (G22); loads wait for genres (G23); an empty season list is the whole series (G24); one of loading / error / empty shows (G26); a screen closes when its title is gone (G27); no developer notes on the season screen (G29); "Add All Seasons" reports failure (G30). Kept as on iOS: one poster per copy (G31), no "Remove" on a whole-series copy (G32), "See All" (G18) | Same rule as D15 | Done |
| D31 | `MainScaffold` takes the tab graphs as a parameter; pushes and pops in the Library graph act only for the screen on top | The shell's tests run it over stand-in screens now that the Library's need Hilt and a server. A screen still animating out can't push or pop a second time | Done |
| D32 | Discover's state is split three ways: the rows in a `@Singleton RecommendationsStore` (§9.4's `RecommendationsViewModel`), the last search's results in a `@Singleton SearchStore`, and the query and has-searched flag in `SearchViewModel`, scoped to the Discover screen. The detail route carries the title id and its row's `X-Flyk-Source`; the detail screen finds the title in the two stores, or, after process death, uses the attribution call's answer | The Library tab starts the rows loading and library changes mark them stale while Discover isn't showing. Leaving the tab pops the Discover screen, so iOS's reset-on-leave is simply the ViewModel ending (it clears the results). Passing ids, not the title, keeps routes short and survives process death | Done |
| D33 | Discover follows iOS where the blueprint differs: all eight rows always shown, with iOS's titles and order; stale rows reload at once; the detail screen as iOS draws it (no season list, poster header or rating chips); the add sheet's menus, season picking and one copy per format and condition. Android adds: an "In Your Library" chip on the detail screen (iOS gives no sign); a failed search shows its error (G5); a change during the first load re-asks the affected rows (G35); a partly failed add doesn't add the saved seasons twice (G34); the format menu shows display names (G33). Not ported: the barcode button until Phase 12, and the "Based on what's trending" caption, which iOS never shows | Same rule as D15 / D30; the chip is the plan's "already in library" state, kept small because owning a second copy is normal | Done |
| D34 | The detail screen's attribution call (`GET /v1/media/{id}`) runs in the application scope, not the screen's | iOS fires it as an unstructured task, so backing out at once still lets the backend count the view | Done |

## Open questions

Resolve these from the iOS source and supporting docs (check `docs/OPEN_QUESTIONS.md` first), or ask the backend. Record answers here.

| # | Question | Needed in | Answer |
|---|---|---|---|
| Q1 | `AddLibraryItemRequest`, `RemoveLibraryItemRequest`, `UpdateLibraryItemRequest`, `UpdateSeasonsRequest` are used in §7.4 but never defined | P1 | `{mediaItemFlyksID, format, condition, ownedSeasons?, selectAllSeasons}`, `{mediaInventoryItemID}`, `{mediaInventoryItemID, format, condition}`, `{mediaInventoryItemID, ownedSeasons?, selectAllSeasons}` (iOS `LibraryService`) ✅ |
| Q2 | JSON raw values for `PublicNameSource`, `MFAMethod`; server date formats | P1 | `PublicNameSource`: `DISPLAY_NAME` / `FIRST_NAME`. `MFAMethod`: `sms` / `totp` / `email` (compared case-insensitively). Dates: ISO-8601 strings, with or without fractional seconds. All other enums are SCREAMING_SNAKE. ✅ |
| Q3 | §16 lists seller dashboard, collection insights and referral endpoints that §7.4 has no interface for; `ListingDTO` has no endpoint | P1 | All three exist (`/v1/users/me/seller/dashboard`, `/collection/insights`, `/referral`, in iOS `ServiceLocator`). `ListingDTO` is unused on iOS too. ✅ |
| Q4 | `accountRecoveryEnabled` gates a Login-screen link, but flags are fetched after auth. Is `/v1/feature-flags` callable unauthenticated, or does the cached value apply? | P4 | Flags require auth. iOS loads the cached flags at startup, so the Login link uses the last-known value (default off). ✅ |
| Q5 | Screens referenced but not specified: LibraryItemDetail, HelpSupport, Appearance, `DecoFrame`, ChangePassword/DeleteAccount dialogs, FileDisputeSheet and the `canDispute` rule | P2, P5, P7, P8, P10 | `DecoFrame`: unused on iOS, not ported (D17). LibraryItemDetail (and the season screen it pushes): ported from the iOS views in Phase 5. The rest still open. |
| Q6 | Which endpoint provides `SeasonSummaryDTO` for TV details? | P6 | `GET /v1/media/tv/{flyksId}/seasons` (bare array) ✅ |
| Q7 | Endpoint and payload for submitting impression batches | P13 | `POST /v1/telemetry/impressions {rows:[{eventId, source, clientTs, itemIds≤20, scrollDepth, dwellMs?}]}`; 404 = flag off ✅ |
| Q8 | Barcode lookup: dedicated endpoint, or search with the raw code? | P12 | `GET /v1/search/barcode?upc=` (404 = unknown) + `POST /v1/media/submissions`; both pending on the backend (DEP-12) ✅ |
| Q9 | Email verification: deep link / App Links domain and format? | P4 | None, matching iOS (no URL scheme, Universal Links or associated domains). The link opens wherever the backend's email points; the user then signs in. App Links can come later alongside iOS Universal Links (needs a link format + `assetlinks.json`). ✅ |
| Q10 | `androidx.biometric` is in the stack, but no feature uses it. What does iOS do with biometrics? | P8 | |
| Q11 | Push notifications: there's a preferences screen but no FCM in the stack. Is push in scope? | P11 | |
| Q12 | Membership tiers (price, features): from the API or hard-coded? | P9 | Hard-coded on the client (`MembershipTierInfo.allTiers`) ✅ |
| Q13 | The MFA challenge subtitle depends on the method, but the login response has no method field | P4 | It doesn't on iOS: one subtitle ("Enter the code from your authenticator app, the SMS we sent, or a backup code.") and one free-text field; the server works out the code type. The blueprint's 6-digit numeric auto-submit field would block backup codes. ✅ |
| Q14 | When is the backup-codes-remaining alert shown (threshold)? | P4 | No threshold: after every backup-code sign-in (the server sends `backupCodesRemaining` only then). Title "Backup Code Used"; messages for 0 / 1–2 / 3+ from iOS `BackupCodeAlertContext`; "Go to Security" at ≤ 2 switches to the Profile tab. ✅ |

---

## Working notes

**Build & test**
```sh
./gradlew assembleDebug testDebugUnitTest      # every phase's gate
./gradlew installDebug                         # to a running emulator/device
```

**Environment config** (`local.properties`, gitignored; CI uses the env var names)

| Key | Env var | Default |
|---|---|---|
| `flyk.apiBaseUrl.debug` | `FLYK_API_BASE_URL_DEBUG` | `http://10.0.2.2:8080` (host machine from the emulator) |
| `flyk.apiBaseUrl.release` | `FLYK_API_BASE_URL_RELEASE` | `https://api.flykpicks.com` |
| `flyk.stripePublishableKey` | `FLYK_STRIPE_PUBLISHABLE_KEY` | empty (payments disabled) |

Any key can also be passed as a Gradle property for one build, which wins over `local.properties`:
`./gradlew installDebug -Pflyk.apiBaseUrl.debug=http://localhost:8090` (with `adb reverse tcp:8090 tcp:8090`, since the emulator couldn't reach a host server on `10.0.2.2`).

**Known noise**
- `hiltJavaCompileDebug` prints "Kapt support in Moshi Kotlin Code Gen is deprecated". Moshi codegen *does* run through KSP; Hilt's aggregating javac step just sees Moshi's legacy processor on its path. Harmless.
- `stripDebugDebugSymbols` "Unable to strip…" for ML Kit / CameraX native libraries. Expected for prebuilt `.so` files.

## Changelog

- **2026-10-14**: Phase 6 built: Discover's eight rows with their staleness rules, search, the title screen and the add sheet (per-season copies). 38 JVM + 12 on-device tests; every flow clicked through on the emulator against a scripted mock (row failure and retry, failed search, partly failed add, pull to refresh, leaving the tab, process death on a detail screen), with each request's `X-Flyk-Source` checked. Pending, with Phases 4–5: a run against the dev API, which can't be reached. New iOS gaps G33–G36; G5 corrected (iOS never shows the search error).
- **2026-10-10**: Phase 5 built: `LibraryStore` with paging, optimistic edit / remove and per-account reset; the Library, title and season screens and the edit sheet. 60 JVM + 13 on-device tests; every flow clicked through on the emulator against a scripted mock (two accounts, forced failures, 25 titles over three pages). Paging and payloads checked against the backend source. Pending, with Phase 4: a run against the dev API. New iOS gaps G22–G32; G16 confirmed (the server pages at 20).
- **2026-10-08**: Phase 4 built: real Login, MFA challenge and reset, registration, password recovery, logout, cached feature flags. 36 JVM + 2 on-device tests; every flow clicked through on the emulator against a scripted mock. Pending: a run against the dev API, which was down.
- **2026-10-08**: Phase 3 complete. Auth gate, tabbed shell with per-tab back stacks (Discover resets, as on iOS), placeholder screens for every route, overlays, force logout, per-surface system bar icons. 6 JVM + 7 on-device tests; AndroidX Test bumped for API 37.
- **2026-10-08**: Phase 2 complete. Design system ported from the iOS components (the blueprint's visuals were off in many places); poster loading with the iOS backfill retries; component gallery on launch. 14 new tests (91). New iOS gaps G17–G19.
- **2026-10-08**: Phase 4 questions answered from the iOS source: Q9 (no in-app verification link, as iOS), Q13 (one free-text MFA field), Q14 (alert after every backup-code sign-in).
- **2026-10-07**: Phase 1 complete. Full model/network/storage layer ported from the iOS source (the blueprint was wrong on several wire formats); 77 unit + 3 instrumented test classes. iOS gaps recorded in `ios-gaps.md` (G1–G16).
- **2026-10-07**: Phase 0 complete. Project converted to Kotlin/Compose/Hilt, full dependency set resolved, theme tokens in place, app launches on the API 37 emulator.
