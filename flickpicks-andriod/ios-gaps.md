# FlykPicks iOS — Gaps Found During the Android Port

Issues found in the iOS app while reading its source for the Android port. Each one is verified against the code; references are relative to `../../iOS/FlykPicks-iOS/FlykPicks-iOS/`. The Android port avoids or handles these as noted, so the two apps will behave differently on these points until iOS is fixed.

New findings get appended as the port continues (the phase that found each one is noted).

Severity: **High** — user-visible failure in normal use · **Medium** — wrong behavior in a plausible scenario · **Low** — inconsistency, fragility, hygiene · **Backend** — needs a server change

## Summary

| # | Sev | Gap | Found in |
|---|---|---|---|
| [G2](#g2-network-failure-during-token-refresh-logs-the-user-out) | High | A network failure during token refresh logs the user out | P1 |
| [G1](#g1-429-specific-error-branches-are-unreachable) | Medium | 429-specific error branches never run | P1 |
| [G3](#g3-session-survives-app-reinstall) | Medium | Session survives an app reinstall | P1 |
| [G16](#g16-library-fetch-reads-only-the-first-page) | Medium | The library fetch reads only the first page | P1 |
| [G4](#g4-impression-retry-queue-grows-without-bound) | Medium | Impression retry queue grows without bound while offline | P1 |
| [G5](#g5-raw-error-text-shown-to-users-in-search) | Medium | Search shows raw Swift error text to users | P1 |
| [G17](#g17-poster-requests-send-the-bearer-token-to-any-host) | Medium | Poster requests send the bearer token to any host, CDN included | P2 |
| [G21](#g21-the-previous-accounts-data-survives-sign-out) | Medium | The previous account's profile and recommendations show after someone else signs in | P4 |
| [G6](#g6-cancelled-requests-reported-as-no-internet-connection) | Low | Cancelled requests are reported as "No internet connection" | P1 |
| [G7](#g7-multipart-uploads-skip-telemetry-headers) | Low | Multipart uploads skip the telemetry headers | P1 |
| [G8](#g8-placeholder-keychain-service-name) | Low | Placeholder keychain service name `com.yourcompany.flickpicks` | P1 |
| [G9](#g9-tokenstoresave-is-not-atomic) | Low | `TokenStore.save` is not atomic | P1 |
| [G10](#g10-decode-logging-runs-in-release-builds) | Low | Decode logging runs in release builds | P1 |
| [G11](#g11-silent-fallbacks-misrepresent-unknown-values) | Low | Silent fallbacks show unknown values as "Free" / "New" | P1 |
| [G15](#g15-search-fails-entirely-on-one-malformed-result) | Low | Search fails entirely on one malformed result | P1 |
| [G14](#g14-changelostpassword-requires-a-response-body) | Low | `changeLostPassword` treats an empty 2xx as failure | P1 |
| [G19](#g19-topband-covers-the-wordmark-on-iphones-with-a-short-status-bar) | Low | TopBand covers the wordmark on iPhones with a short status bar | P2 |
| [G18](#g18-see-all-looks-tappable-but-does-nothing) | Low | "See All" on genre rows looks tappable but does nothing | P2 |
| [G20](#g20-signing-in-lands-on-the-tab-the-last-session-left-off) | Low | Signing in lands on whatever tab the last session left off on | P3 |
| [G12](#g12-error-handling-depends-on-server-message-wording) | Backend | Error handling depends on server message wording | P1 |
| [G13](#g13-barcode-lookup-and-submission-are-stubbed) | Backend | Barcode lookup and submission are stubbed | P1 |

---

## Details

### G1. 429-specific error branches are unreachable
- [ ] Fixed

**Where:** `Service/AuthService.swift:223` (OTP verify → `AuthError.otpLocked`), `Service/SecurityService.swift:89` (SMS resend → `MFAError.smsResendRateLimited`)

**Problem:** Both catch `APIError.http(429, _, _)`, but `API/APIClient.swift:229` turns every 429 into `APIError.rateLimited` before the services see it. Those branches never run. Users get the generic rate-limit message instead of the specific copy, and the recovery flow never enters its "locked" state.

**Fix:** Catch `APIError.rateLimited` in those two services.

**Android:** Maps rate-limited responses to the intended typed errors.

### G2. Network failure during token refresh logs the user out
- [ ] Fixed

**Where:** `API/AuthRetryAPIClient.swift:153`

**Problem:** `refreshTokenOnce()` calls `onAuthFailure()` on *any* refresh error, including `networkUnavailable` and timeouts. If the access token expires while the device is briefly offline, the next request clears the session and shows "Your session expired. Please sign in again."

**Fix:** Force logout only when the refresh endpoint returns an HTTP rejection (or `token_revoked`). On network errors, rethrow and keep the session.

**Android:** Only an HTTP rejection of the refresh clears the session (plan D14).

### G3. Session survives app reinstall
- [ ] Fixed

**Where:** `Stores/AuthStore.swift:72`, `Stores/TokenStore.swift`

**Problem:** Keychain items outlive app deletion, and `AuthStore` treats any stored access token as signed in. Nothing resets the keychain on first launch. Deleting and reinstalling the app resumes the previous user's session. That's surprising for "sign out by deleting the app", and on shared devices it's a privacy problem.

**Fix:** On first launch (detected by a `UserDefaults` flag, which *is* wiped on uninstall), clear the session keys. Decide whether device trust should also reset.

**Android:** Not affected. Encrypted prefs are removed on uninstall and excluded from backup.

### G4. Impression retry queue grows without bound
- [ ] Fixed

**Where:** `Service/ImpressionBatcher.swift:84`, `:34`

**Problem:** On any failure, `pendingRetry = rows` re-queues *everything*, despite the "retry once" comment. While offline the queue keeps growing. Once `buffer + pendingRetry ≥ 50`, every new impression triggers an immediate flush, so each impression costs one failing network request.

**Fix:** Retry a batch at most once (then drop it), cap the queue size, and back off instead of flushing on every record.

### G5. Raw error text shown to users in search
- [ ] Fixed

**Where:** `Stores/SearchStore.swift:54`

**Problem:** `errorMessage = "Failed to load search: \(error)"` interpolates the Swift error value. Users can see text like `networkUnavailable`, or decoding details in debug builds.

**Fix:** Use `error.userFacingMessage`, as the other stores do.

### G6. Cancelled requests reported as "No internet connection"
- [ ] Fixed

**Where:** `API/APIClient.swift:125`

**Problem:** The `default:` branch maps every `URLError`, including `.cancelled`, to `.networkUnavailable`. Only `SearchStore` and `RecommendationsStore` check `Task.isCancelled`. Other stores can show an offline error when a view's `.task` is cancelled, for example when navigating away mid-load.

**Fix:** Map `URLError.cancelled` to `CancellationError`.

### G7. Multipart uploads skip telemetry headers
- [ ] Fixed

**Where:** `API/APIClient.swift:280` (`uploadMultipart`)

**Problem:** Every other request sends `X-Flyk-Session` / `X-Flyk-Client-Ts`; avatar uploads don't.

**Fix:** Add the same telemetry block used in `request` and `requestFireAndCheck`.

### G8. Placeholder keychain service name
- [ ] Fixed

**Where:** `Stores/TokenStore.swift:9`, `Service/DeviceIDProvider.swift:16`

**Problem:** Both use `"com.yourcompany.flickpicks"`. Renaming it later orphans existing items: every user gets signed out, loses device trust (MFA prompts return), and gets a new device ID.

**Fix:** Pick the final name before launch. After launch, any rename needs a read-old/write-new migration.

### G9. `TokenStore.save` is not atomic
- [ ] Fixed

**Where:** `Stores/TokenStore.swift:23–25`

**Problem:** Three separate keychain writes. If the second or third fails, the stored access token, refresh token and user ID can come from different sessions.

**Fix:** Store them as a single encoded item, or write all three and roll back on failure.

### G10. Decode logging runs in release builds
- [ ] Fixed

**Where:** `Models/MediaItem.swift:19`, `:26`

**Problem:** `MediaItem` tries the movie shape first and `print`s the failure without an `#if DEBUG` guard. Every TV item in every search logs a "❌ Movie decode error" in production.

**Fix:** Guard with `#if DEBUG`, or choose the shape from `media_type` first.

### G11. Silent fallbacks misrepresent unknown values
- [ ] Fixed

**Where:** `Models/Profile/MembershipDTO.swift:29`, `Models/LibraryItem.swift:34`

**Problem:** An unknown membership tier falls back to `.free`, even though `MembershipTier` has a `.default_` case ("—"). An unknown item condition falls back to `.new`. If the server adds a value, users see "Free" or "New" instead of a neutral placeholder.

**Fix:** Fall back to `.default_` for tiers. Consider a neutral/unknown case for condition.

**Android:** Currently matches iOS, so the two apps display the same thing. Revisit when iOS changes.

### G12. Error handling depends on server message wording
- [ ] Fixed (needs backend)

**Where:** `Service/SecurityService.swift:109` (the substring `"last"` separates last-method from wrong-password on MFA disable), `Models/Profile/OrderDTO.swift:122` (`OrderError.map409` matches substrings of the 409 message)

**Problem:** Any copy change on the server silently changes client behavior.

**Fix:** Ask the backend for stable `code` values in the error envelope for these cases, then switch on `serverCode`.

**Android:** Ported with the same substring logic for parity.

### G13. Barcode lookup and submission are stubbed
- [ ] Fixed (needs backend)

**Where:** `Service/BarcodeService.swift:57` (lookup always returns `nil`), `:67` (submission always throws `noTMDBMatch`)

**Problem:** Waiting on backend DEP-12 (`GET /v1/search/barcode`, `POST /v1/media/submissions`). Scanning always reports an unknown title.

**Fix:** Remove the stubs once the endpoints ship.

**Android:** Calls the real endpoints; a 404 is treated as "unknown title".

### G14. `changeLostPassword` requires a response body
- [ ] Fixed

**Where:** `Service/AuthService.swift:234`

**Problem:** The response is decoded as `{ message }` but never used. If the server ever returns an empty 2xx, the user sees an error even though the password *was* changed.

**Fix:** Decode as an empty-tolerant response (`requestVoid`). Same consideration for `requestMFAReset`.

**Android:** Ignores the response body for both calls.

### G15. Search fails entirely on one malformed result
- [ ] Fixed

**Where:** `Service/SearchService.swift:12`

**Problem:** Search decodes `[MediaItem]` strictly, while the recommendation rows use `LenientArray`. If one result matches neither the movie nor the TV shape, the whole search fails, and G5 makes it worse by showing the raw error.

**Fix:** Decode `LenientArray<MediaItem>`.

**Android:** Search results are lenient.

### G16. Library fetch reads only the first page
- [ ] Fixed (verify server page size)

**Where:** `Service/LibraryService.swift:20`

**Problem:** `GET /v1/users/inventory/media` returns the paginated envelope `{items, page, totalPages, totalItems}`, but iOS sends no `page`/`limit` and never requests later pages. A collection larger than the server's default page size is silently truncated: the missing titles don't show in the Library and the header counts are low.

**Fix:** Confirm the server's default page size. If collections can exceed it, page until `hasMore` is false, or pass an explicit large `limit`.

**Android:** Matches iOS for now; tracked in the plan (Phase 5).

---

### G17. Poster requests send the bearer token to any host
- [ ] Fixed

**Where:** `Components/AsyncPosterImage.swift:86`

**Problem:** The poster loader attaches `Authorization: Bearer <access token>` to every poster request, whatever host `posterUrl` names. The API spec shows `posterUrl` values on `cdn.flykpicks.com`, so the access token goes to the CDN (and into its logs), and would go to any third-party image host the backend ever returns.

**Fix:** Attach the token only when the URL's scheme, host and port match `AppConfig.baseURL`.

**Android:** The image client attaches the token only to the API origin (`AuthInterceptor(onlyOrigin = …)`, tested in `ImageClientTest`).

---

### G18. "See All" looks tappable but does nothing
- [ ] Fixed

**Where:** `Components/SearchGenreRow.swift:33`, `Components/LibraryGenreRow.swift:33`

**Problem:** Every genre row header ends in a brass "See All", styled like the app's other links, but it is a plain `Text` with no action.

**Fix:** Either open a full list for the genre, or remove the label.

**Android:** Matches iOS (a plain label) until the product decides.

---

### G19. TopBand covers the wordmark on iPhones with a short status bar
- [ ] Fixed

**Where:** `Components/TopBand.swift:13,34`; `topBandHeight` in `DecoAuthScreen.swift:6` and seven screens under `Views/`

**Problem:** `TopBand` is 84pt tall (plus 4pt of rules) measured from the top of the screen, but screens start their content `topBandHeight` = 52pt below the *safe area*. On iPhones with a 59pt safe area that leaves a 23pt gap; on an iPhone SE (20pt status bar, supported on iOS 18) content starts at 72pt while the band ends at 88pt, so the band, which is drawn on top, hides the top 16pt of the wordmark.

**Fix:** Size the band from the safe area (status bar + a fixed height), and derive `topBandHeight` from the same constant.

**Android:** The band is the status bar inset + 28dp, and content starts 52dp below the status bar (`TopBandDefaults`), so the gap is the same 20dp on every device.

---

### G20. Signing in lands on the tab the last session left off
- [ ] Fixed

**Where:** `RootView.swift:16` (`@State private var selectedTab = 0`)

**Problem:** `selectedTab` belongs to `RootView`, which stays alive across sign-out; only its `if auth.isAuthenticated` branch is swapped. Signing out happens on the Profile tab, so the next sign-in, possibly by a different user, opens on Profile instead of Library.

**Fix:** Reset `selectedTab` to 0 when `isAuthenticated` becomes true (or key the `TabView` on the session).

**Android:** Each sign-in builds a fresh shell that starts on Library.

---

### G21. The previous account's data survives sign-out
- [ ] Fixed

**Where:** `Stores/AuthStore.swift` (`logout()` resets only auth state); `Stores/ProfileStore.swift:14,19` (one `cached_user_profile` key, loaded at init); `Stores/RecommendationsStore.swift:91–92` (`loadIfNeeded` runs once per app run)

**Problem:** Signing out clears tokens and auth state but no other store. On a shared device the next account sees the previous account's cached profile until a fetch replaces it, and recommendations built from the previous account's library and search history until something marks them stale. The library shows the old list until its reload finishes.

**Fix:** Reset every per-user store on sign-out (and on session expiry), and key the profile cache by user id.

**Android:** No per-user stores exist yet; Phase 5 makes resetting them on sign-out a requirement.

---

## ANDROID_BLUEPRINT.md corrections

`ANDROID_BLUEPRINT.md` (in the iOS repo) disagrees with the iOS source in ways that would break a port built only from the blueprint. The Android port follows the iOS source. Fix the doc if it will be reused.

| Blueprint says | iOS source actually does |
|---|---|
| JSON keys `deviceId`, `userId` (auth requests/responses) | `deviceID`, `userID` |
| Enum raw values in camelCase (`likeNew`, `uhd4k`, `pendingPayment`, …) | SCREAMING_SNAKE: `LIKE_NEW`, `FOUR_K_ULTRA_HD`, `PENDING_PAYMENT`, `UNDER_REVIEW`, `NOT_AS_DESCRIBED`, `FULL_REFUND`, … |
| `isDefault`, `isCurrent`, `isCanceled`, `isCompleted` as JSON keys | Wire keys are `default`, `current`, `canceled`, `completed` |
| MFA verify errors: 400 / 401 / 429 | 401 (`invalid_mfa_challenge` / `invalid_mfa_code`), 403 (`mfa_challenge_locked`), 410 (`mfa_challenge_expired`) |
| `GET /v1/users/inventory/media` returns a list | Returns the paginated envelope `{items, page, totalPages, totalItems}`; the add/remove/update/seasons endpoints return bare lists |
| Media `id: Int`; items told apart by `media_type` | `id: String`; told apart by shape (movie then TV); recommendation arrays are decoded leniently (bad items skipped) |
| `X-Flyk-Source: discover.popular_movies`, … | `recommendation.popularMovies`, `recommendation.topRatedTV`, …; library calls send `library.detail` |
| Library request DTOs undefined | `{mediaItemFlyksID, format, condition, ownedSeasons?, selectAllSeasons}`, `{mediaInventoryItemID, …}` |
| Missing endpoints | `GET /v1/media/{flyksId}`, `GET /v1/media/tv/{flyksId}/seasons`, `POST /v1/telemetry/impressions`, `GET /v1/search/barcode`, `POST /v1/media/submissions`, `GET /v1/media/poster`, `GET /v1/media/detailImage` |
| `UserProfileDTO.displayName` optional | Required |
| `SellerReviewDTO.rating: Double`; `OrderTimelineEntry.date` optional | `Int`; required |
| Membership tiers unspecified | Hard-coded client-side (`MembershipTierInfo.allTiers`); `changePlan` sends the tier lowercased |
| Pagination `hasMore = page < totalPages - 1` | `page < totalPages` |
| Optional request fields sent as `null` | Omitted entirely (`trustToken`, `note`, `rememberDevice` only when true, etc.) |
| MFA challenge: method-specific subtitle; 6-digit numeric field that auto-submits | One fixed subtitle; one free-text field that also accepts backup codes; submit by button / keyboard action only |
| Backup-code alert: "You have X backup codes remaining…" | Shown after every backup-code sign-in; three messages (0 / 1–2 / 3+); "Go to Security" only when ≤ 2 remain |
| Palette hex: Surface `#FDFDFA`, Brass `#C7A454`, Jade `#26A696`, NavBar `#0D1220` | `DS` values convert to `#FCFAF5`, `#C7A354`, `#26A699`, `#0D121F` |
| Textile texture ≈4% diagonal hatch; ink-paper is an emboss effect on heading text | Textile is grain + weave at ~1–2% effective opacity; ink-paper is a mottled-paper texture on the navy `TopBand`, not a text effect |
| `DecoTextField` 18dp corners, brass leading icon, 15sp text | 14pt corners, ink-55% icon, body (17pt) text, 6% drop shadow |
| `DecoCTAButton` filled brass with cream text; danger is filled red | Tinted fill (15%) with a 55% outline and tint-colored text; danger is ember (orange) |
| `DecoButton` outline-only, 14dp corners; `DecoCard` 18dp corners with an ink border | `DecoButton` canvas fill, 2pt corners, ≤128pt wide; `DecoCard` 12pt corners with a brass-35% border |
| `DecoSectionHeader` serif with ink rules, rules on by default | Brass caption text, brass rules, rules off by default, optional subtitle |
| `DecoAuthScreen` centers content vertically | Top-aligned scroll under the wordmark |
| `AsyncPosterImage` shimmer placeholder; plain Coil load | Cream-to-jewel gradient with a film icon and spinner; retries 202/404 (2, 4, 8 s, then once after 30 s); 204 = no poster |
| `TopBand` is three thin rules; wordmark reads "FlykPicks" | `TopBand` is an 84pt navy ink-paper band plus ink/brass/ink rules; the wordmark reads "FLICK PICKS" |
| `HeaderStats` 28sp serif numbers | Title3 (20pt) semibold sans |
| Genre rows: `DecoSectionHeader` + 12dp spacing | Header "GENRE · N titles … See All", brass rules, 24pt poster spacing; search posters 130pt wide, library 160pt |
| Toasts last 3 s, rounded rectangle | 2 s, ink capsule with a brass checkmark |
| `NavRow` has an optional badge | No badge; a row without a destination ("Appearance") is muted with no chevron |
| Tab bar: navy (`NavBar`) background, cream-50% unselected items, "Search" label | Ink background, brass-25% top hairline, brass-45% unselected, label "Discover" |
| Routes `library/detail/{itemId}`, `mfa_setup/{method}`, `trusted_devices` | Library detail is keyed by title id and pushes a TV-season screen; MFA setup takes no method; trusted devices are a section of Active Sessions; Change Password and File Dispute are pushed screens |
| Every tab keeps its back stack | Leaving the Discover tab resets it (`searchScreenState.reset()`) |
| Login rate-limit countdown "Try again in X:XX" | "Try again in Ns" (whole seconds), the same on registration, recovery and resend |
| Forgot password: OTP step has a resend | No resend on the code step; going back to the email step sends another code |
| MFA reset: only 429 is shown | Any error is shown (429 is the only one the server is expected to return) |
| Auth methods (`register`, `requestOTP`, …) live on `AuthViewModel` | iOS `AuthStore` has them as pass-throughs, but each screen holds its own state |
| `DecoFrame`, `DecoSelectorButton`, `PosterHeaderView` are components to build | iOS defines them but no screen uses them |
