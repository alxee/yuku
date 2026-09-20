# CLAUDE.md

Guidance for Claude Code (or any future agent) working in this repo.
The full, unabridged change narrative is kept in `CLAUDE.md.full.bak` — go
there when an entry below is too terse to act on.

## What this is

A minimal Android browser called **Yuku**: WebView engine, 100% Jetpack Compose
UI, `MainActivity` (a `FragmentActivity`, forced by androidx.biometric) plus
`CustomTabActivity` for the link overlay. Package `com.yuku.browser`; launcher
label is `app_name` in `res/values/strings.xml`; Gradle root project `Yuku`.
See `README.md` for the feature/stub inventory and layout map.

## Building and running

```bash
export JAVA_HOME="/Users/alex/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
./gradlew :app:installDebug
```

- Android Studio's bundled JBR is JDK 25 here — **too new** for Gradle 8.9 /
  AGP 8.7.3. Use the JBR 21 path above. The daemon caches the JDK per project:
  on a JVM-version error, re-run the exports in the same shell and
  `./gradlew --stop` first.
- Dev package is `com.yuku.browser.debug`. Launch:
  `adb shell am start -n com.yuku.browser.debug/com.yuku.browser.MainActivity`.
  Emulator AVD: `browser_test`.
- **Never judge animation smoothness on the debug build.** Measured with
  `dumpsys gfxinfo <pkg> framestats` on a 120Hz device: cold launch 1945ms
  debug vs 862ms release; switcher UI thread 6.9 vs 3.9ms/frame (p99 40 vs
  12ms); sheet recomposition 1.66 vs 0.61ms. Debug drops frames release does
  not (`ui-tooling`, unoptimised bytecode, no R8). To measure honestly:
  `./gradlew :app:assembleRelease`, sign with the debug keystore
  (`apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android
  --ks-key-alias androiddebugkey`), install — it is `com.yuku.browser`, so it
  coexists with `.debug`.
- **Smoothness is cadence, not average frame time.** Arc Search's frames are
  slower than ours (13.7 vs 11.3ms at launch) and feel smoother because
  118/118 present on consecutive vsyncs. Our release build does too
  (114/115 through the switcher); the debug build does not (107/115, gaps of
  2–11 vsyncs). Remaining real ceilings: the switcher's UI thread
  (`anim+recompose` 1.77ms even in release) and its GPU cost (12.6ms/frame —
  full-screen grain + thumbnail + live WebView compositing at once).
- No test suite. Verification is manual: `adb shell input tap/swipe` +
  `adb exec-out screencap`, or `uiautomator dump` for exact bounds when
  entrance animations shift layout between screenshot and tap. Pure-logic JS
  (the injected scripts) can be checked off-device with `node` against a stub
  DOM; parsers/matchers with `kotlin-compiler-embeddable` from the Gradle cache.

## Searching the source (shell gotchas)

zsh; repo root `/Users/alex/Downloads/browser`. Two false exit-1s:

- `--include=*.kt` — zsh expands it and fails with `no matches found`.
  **Quote it**: `--include='*.kt'`.
- The Bash tool's cwd *persists between calls*, so a relative `cd` that worked
  once fails the next time. Use absolute paths or prefix each call with
  `cd /Users/alex/Downloads/browser &&`.

```bash
cd /Users/alex/Downloads/browser && grep -rn 'quickSwitch' app/src/main/java/com/yuku/browser
```

`core/` and `ui/` are the two source packages; resources in `app/src/main/res`.

## Workflow policy: build, install, then stop

After a code change, build and `installDebug` so the new build is ready — then
stop and hand off. Don't drive the UI yourself (tapping, screenshotting,
verifying) unless explicitly asked to investigate or verify something specific.

## Architecture conventions

- **`core/` has no Compose dependency.** `BrowserViewModel` and its data
  classes are plain Kotlin/Android; Compose-only types live in
  `ui/theme/Theme.kt`, which maps `core/` enums to colors/schemes.
- **Theming is dynamic, not static vals.** `Ink`, `PageBg`, `BarBg` etc. are
  `@Composable val`s backed by `LocalBrowserPalette`; `AccentColor` reads
  `MaterialTheme.colorScheme.primary`. Don't hardcode `Color(0x…)` for chrome —
  add a token to `BrowserPalette` and both `LightPalette`/`DarkPalette`. The
  one deliberate exception is the private-tab placeholder `Color(0xFF121212)`.
  `BrowserTheme` lerps between THREE ends: the user's light and dark schemes
  (`darkness`) plus a fixed dark `PrivateAccent` one (`privacy`, 420ms,
  published as `LocalPrivacy`) — private is always dark whatever the setting.
  `LocalChromeDarkness` is `t + (1 - t) * p`. Settings re-provides the ordinary
  theme for its own subtree (`BrowserTheme(privateMode = false)`) because it is
  where the accent is chosen; `SystemBarIcons` takes `ignorePrivacy` for that.
- **Full-screen "destination" pattern.** Settings, Bookmarks, History,
  Downloads, Ad blocker are not sheets — they're overlays toggled by a
  `xOpen: Boolean` in `BrowserScreen`, wired into the shared `BackHandler`
  chain. Use `Destination` (hand-rolled slide+fade with a pre-warm frame), not
  `AnimatedVisibility`, which composes its content on the first animating
  frame and hitches on a list of dozens of rows.
- **Sheets share one `ModalBottomSheet`** via a `sheet: Sheet?` enum + `when`
  in `BrowserScreen`, so swapping NewTab↔Menu is one state write. Every open of
  the + sheet goes through `openNewTabSheet()`, which bumps `newTabSheetOpens`
  — the sheet's field state is `remember(openKey)`-keyed, since the composable
  never leaves composition and an effect would reset it a frame late.
- **Persistence.** `BrowserStore` (one JSON blob, 500ms-debounced `markDirty` /
  immediate `persistNow`) holds tabs, history, visit tallies, bookmarks and all
  settings. Four things are deliberately outside it: thumbnails
  (`ThumbnailStore`, WEBP per tab id), favicons (`FaviconStore`, PNG per host),
  parked page states (`PageStateStore`) and the password vault (encrypted).
  Private tabs are excluded from all of them. `privateMode` itself is never
  persisted.
- **Icons**: Material Icons Extended, Filled/Outlined pairs for on/off state
  (filled = on). Prefer `Icons.AutoMirrored.*` for direction-sensitive glyphs.
- **`res/font/bitcount_grid_double.ttf` is the variable font** (live `wght`
  axis), from the `google/fonts` repo, not the CSS API. Render weights via
  `FontVariation.weight(n)` (`@OptIn(ExperimentalTextApi::class)`).
- **Motion vocabulary lives in `ui/Motion.kt`.** Anything that ARRIVES carries
  ~1.2% of its TRAVEL past and settles back (`Overshoot`; `OvershootSoft`
  ~0.5% for screen-crossing motion); anything that LEAVES takes `Accelerate`.
  Durations with an overshoot are ~15% longer, so the first arrival at target
  is where they used to end. Easing tweens, not springs — nearly every
  animation here is a duration something else is timed against.
  **Overshoot in size, never in position** where a settle lands at 0: past 0 a
  page is off-centre and uncovers bare background, while one grown slightly
  larger is clipped by the window. A corner radius must not follow an
  overshoot below zero.

## Compose/Android traps that have bitten here

- **Read animated floats in layout/draw, not composition.** `progress`,
  `offsetX`, `offsetY` read as `.value` in `BrowserScreen`'s scope recomposed
  the Scaffold, toolbar, both sheets and every card per frame. Composition now
  gets BOOLEANS off `derivedStateOf` (`progressAtZero`, `progressAtRestOpen`,
  `progressEngaged`) and everything else is handed a `() -> Float` called
  inside a `graphicsLayer`. Same for `TabSwitcher`'s `zoomScale`/
  `stackProgress`, `TabListSwitcher`'s fade, `BottomToolbar`'s rotations.
  The one unavoidable composition read is `FloatingTabLabel` (its width is a
  layout input), confined to that one row.
- **A draw modifier declared before a `graphicsLayer` is OUTSIDE it** — e.g.
  `grainedBackground` above the layer painted at full opacity regardless of the
  layer's alpha.
- **`pointerInput` blocks are a double trap.** State read inside one is read
  through a snapshot that does not advance with the composition (a
  `MutableState` answered `false` to the gesture while composition read
  `true`), and the block keeps everything it CLOSED OVER — callbacks included —
  until a key changes, so stale `onX` lambdas write to a previous open's state.
  The fix pattern is a plain (non-snapshot) latch object written from
  composition via `SideEffect`, carrying live callbacks; gestures compute their
  own `from`/`to` inside the block. Changing a `pointerInput` key cancels the
  press in flight, so never key it on something the press itself changes.
- **`SideEffect` vs `LaunchedEffect`**: `LaunchedEffect` is dispatched at the
  start of the NEXT frame. Anything that must be true for the frame that just
  changed (e.g. `pageExposed` when a sheet covers the page) uses `SideEffect`.
- **`Modifier.width` is coerced into incoming constraints** and silently
  no-ops; use `requiredWidth` for deliberate overscan. A `Box` reports its
  child's full width without clamping, so a centered parent already hangs
  overscan off both edges evenly — don't add a corrective offset.
- **`Crossfade` puts content in a Box of its own**, so a `weight(1f)` inside it
  becomes parent data the enclosing Row never sees. It compiles and lays out
  wrong. Animate one face at a time inside the Row instead.
- **A `FocusRequester` requests focus when the field ENTERS composition**, not
  when the flag flips.
- **The platform refuses an elevation shadow while a layer's alpha < 1**, so a
  fading surface's shadow snaps on/off in one frame and reads as the corner
  changing shape. Use a hairline border (drawn as content) instead.
- **A popup's content is laid out in a window whose origin is not the host's**
  (measured 125px out — the status bar). Place popups with a
  `PopupPositionProvider`, where `anchorBounds` and the returned offset share
  one space; never with an offset on the content. A non-focusable popup keeps
  the keyboard; a focusable one steals it. To swallow outside taps, stack a
  second popup window UNDER the menu — the app window is told about outside
  touches too, so dismiss-on-outside-touch still delivers the tap.
- **`MutableTransitionState` keeps a popup window alive for its exit**; on a
  bare boolean the window is torn down on the first frame.
- **A `remember(open)` `MutableInteractionSource`** for a row that is unplaced
  in the same frame it is tapped — otherwise the armed ripple replays seconds
  later on the next open.
- **`fadeEdges`/offscreen layers**: apply on a weighted Row, never on a
  wrap-content `Text` (it eats the first and last character). Ask for the layer
  only when the content actually overflows — a layer torn down and reallocated
  shows as a dark frame.
- **Kotlin block comments NEST**: a `text/*` inside a KDoc comment opens one,
  and the next `*/` in real code closes the doc — the file parses as garbage
  from there down.

## WebView: what its defaults actually are

**The default for most `WebChromeClient`/`WebViewClient` hooks is SILENCE, not
refusal** — an unhandled file input does nothing, an unhandled fullscreen
request leaves the video boxed, `confirm()` resolves false unasked, a missing
`setDownloadListener` makes a download link a dead tap, an unhandled
`onPermissionRequest` hangs `getUserMedia` forever, and `window.open()` becomes
a navigation of the CALLING view with `opener === window`. All of these are
handled in `ui/WebPlatform.kt` (five requests: file chooser, fullscreen, JS
dialogs, SSL errors, permissions) plus the ViewModel's "web platform requests"
section, drawn as ONE composable at the END of both `BrowserScreen` and
`CustomTabScreen` (last = its `BackHandler`s outrank the chain, and it draws
over everything). Every request carries a tab id.

- **A dropped callback is not a declined one.** An unanswered `ValueCallback`
  kills that form's input for the page's life; an unanswered `JsResult` stops
  the document's script thread for good. So: a cancelled picker still reports,
  a replaced chooser is answered first, a navigation cancels the outgoing
  document's dialog, and a second dialog raised while one is up is DECLINED
  (a page can `alert()` in a loop). A *cancelled* permission request is
  dropped, never denied — `deny()` on a withdrawn request calls into a frame
  that stopped listening.
- **Permissions are two separate permissions**: the site's (our card, three
  answers — Allow / Allow this time / Block) and Android's own, raised only
  AFTER the site is allowed. `onOsPermissionResult` ignores what the system
  returned and re-checks what is actually held. Keyed by ORIGIN, not
  registrable domain (unlike history/bookmarks/vault); opaque origins refused;
  the trailing slash on a `PermissionRequest` Uri trimmed. Session answers are
  read before stored ones; a standing answer removes the session's. Unknown
  resources (protected media, MIDI) are denied, never folded into a neighbour.
  Geolocation `retain = false` on purpose — we keep the answer where Settings
  can revoke it.
- **`ActivityResultRegistry` request codes vs fragment 1.2.5**: it generates
  codes across the whole int range, and `FragmentActivity` in fragment 1.2.5
  (dragged in by `biometric:1.1.0`) rejects anything above the low 16 bits —
  `IllegalArgumentException: Can only use lower 16 bits for requestCode`,
  swallowed by a `runCatching` and answered as a denial. Fragment is pinned to
  1.8.5. Don't unpin it.
- **Downloads inherit nothing**: `DownloadManager` is another process, so
  cookies, `Referer` and UA are attached by hand, and the cookies are read
  where the WebView is (`WebViewCompat.getProfile(web).cookieManager`) because
  a private tab's jar is its own. `data:` is decoded and written locally;
  `blob:` is an explained failure. `FileDownloads.saveBytes` is the one writer.
- **SSL errors are remembered per host for the process** (never on disk) — a
  page with twenty subresources on one bad cert raises twenty errors, so asking
  twenty times is asking nobody. Proceed is drawn in the error colour, not the
  accent. The page-supplied `beforeunload` message is deliberately not rendered.
- **Third-party cookies are ON** — off is right for an embedder, wrong for a
  browser (OAuth popups, checkout iframes). Set via `cookieManagerFor`.
- **`setSupportMultipleWindows` + `onCreateWindow`**: a real tab, its WebView
  built on the spot (the transport must be answered on that call), arriving
  with no url and `load = false` — the renderer navigates it itself. An opener
  cannot be evicted by `trimViews` while its window is open. Gestures only;
  `javaScriptCanOpenWindowsAutomatically` stays off. `onCloseWindow` finds its
  tab by view IDENTITY (the callback carries the CHILD's view).
- **WebView has no FedCM / Web Notifications / Bluetooth / USB.** Google One
  Tap logs `unsupportedBrowser` forever; UA spoofing doesn't change it.
  Notifications are POLYFILLED (`core/WebNotifications.kt`): a document-start
  script defines `Notification`, `ServiceWorkerRegistration.showNotification`
  and the `notifications` answer of `permissions.query`, over a
  `addWebMessageListener` bridge — not `@JavascriptInterface`, because the
  message carries the sending frame's ORIGIN and permissions are keyed by it.
  `Notification.permission` is read synchronously, so the answers are
  compiled INTO the script and it is re-registered on any change
  (`refreshNotificationScripts`). It is the fourth `SitePermission`, through
  the ordinary card and Settings rule, with `defaultRule` = Block (off until
  the user picks Ask). Asking needs transient user activation; subframes,
  private tabs and the overlay are refused without asking. Allowed-but-app-
  can't-post reports "default" so the page asks and reaches Android's
  dialog. No push: a notification exists only while its page is loaded. A
  tap routes through `MainActivity` (`ACTION_OPEN`) to its tab if still the
  same site, else a new tab, and reports `click` to the page.
- **WebViews must be built on a context that unwraps to an Activity** or
  Chromium builds no autofill provider at all — the feature looks
  unimplemented when it was never asked for. They're held in the ViewModel
  across config changes, so the context is a `MutableContextWrapper` whose base
  is swapped: Activity while one hosts (`attachHost` from `onCreate`, BEFORE
  `setContent` — Chromium reads the context once at construction),
  Application when none (`detachHost`, identity-checked in `onDestroy`).
- **`WebView.setPadding` is ignored by Chromium entirely** (verified at 800px).
  Only layout size reaches the viewport.
- **A WebView hanging past the window's VISIBLE bottom is given a shorter
  visual viewport**, by exactly the overhang, and `innerHeight` does not
  change: measured, 77px of overscan below the screen's edge took 29 CSS px
  off `visualViewport.height`. The first scroll down then pans that shorter
  viewport inside the layout viewport BEFORE the document moves, which carries
  everything `position: fixed` up the screen by it — a site's header under the
  status bar, every bottom bar off the line it was put on (auto.ria.com's nav,
  29 CSS px too high, only after a scroll). The keyboard's top is the window's
  visible bottom too. So nothing hangs past it: the page lens overscans only
  ABOVE the page box (`ui/PageLens.kt`), where the status bar is.
- **`View.draw(Canvas)` re-renders through a path Chromium never uses for
  display** — canvas/WebGL content comes back blank, compositor layers wrong.
  It also refuses a detached view, and only one WebView is attached at a time.
  Previews use `PixelCopy.request(Window, …)` over the WebView's window rect;
  the `Window` is found by walking the WebView's PARENT chain (`hostWindow()`),
  since `rootView.context` is a `DecorContext` over the application context.
- **`onPageCommitVisible` is the FIRST pixels, not the page.** It is when
  the incoming document commits a visible frame, which for a page being
  fetched again is a shell — header, background, content still coming. The
  page cover (`coveredTabIds`) therefore lifts on the finished load plus a
  settle instead, or it swaps a complete picture of the page for an empty
  one, which is the flash it exists to prevent.
- **A navigation in a tab already on screen is HELD** (`pendingHolds` /
  `activeHolds` in `BrowserViewModel`) — reload, link, back/forward, typed
  address. The page cover is for a view built from nothing; this is the other
  half. The picture is taken when the navigation is ASKED FOR (`prepareHold`,
  which defers the navigation itself by the frame or two a `PixelCopy` takes;
  links from `shouldOverrideUrlLoading`), shown at `onPageStarted`, and faded
  out once the page is ready: `readyState != loading` for a link, the finished
  load for reload/history (Chromium restores their offset late). Capped (1.5s /
  2.5s past commit); any touch lifts it. A native `HoldCoverView` INSIDE the
  WebView's container at index 1, not a composable: the host only checks child
  0, it rides the shrink transform, and it sits UNDER the pull spinner. A
  closing sheet still draws after `sheet = null`, so where the page is not
  exposed the tab's thumbnail stands in when it is still exact. Pull-to-refresh
  captures at pull START (`onPullStarting`), or the spinner is in the picture.
  Captures are refused while a hold is up. On a main-frame failure covers wait
  `ERROR_FADE_IN_MS` (`dismissCoversForError`) so they uncover our error
  screen, not the engine's; `FadingWebErrorState` delays its exit 120ms so a
  retry that fails again doesn't blink. The overlay never reports
  `pageExposed`, so it gets no holds.
- **A PREFETCH is reported as a main-frame request.** A site carrying
  speculation rules (Cloudflare's Speed Brain writes the header for its
  customers — keddr.com is one) has the engine prefetch every same-site link
  the moment a finger lands on one, at `conservative` eagerness, i.e. on
  pointerdown; Cloudflare answers a prefetch it will not serve with a bare
  **503**. That reaches `onReceivedHttpError` with `isForMainFrame` true, for
  a document the tab never navigated to, so the full-page error screen
  replaced a page that was loaded and on screen — the first touch on the
  home page's carousel was enough. Both error hooks therefore skip a
  SPECULATIVE request (`isSpeculative`: `Sec-Purpose`/`Purpose`/`X-Moz`
  naming prefetch or prerender), asked of the request's own headers rather
  than of the tab's state.
- **An HTTP STATUS is not a failure, and the two hooks disagree about
  when.** A 404 is GitHub's own page with a search box on it, a 402 is the
  metered article, a 503 is Cloudflare's challenge — covering any of those
  with the placeholder hides a document the user could have read. And the
  ordering makes a flag raised at `onReceivedHttpError` useless anyway: the
  status arrives BEFORE the response commits, i.e. before `onPageStarted`,
  which is where the flag is cleared, so it was wiped by the navigation it
  belonged to every time (an empty 503 came up as Chromium's own
  `net::ERR_HTTP_RESPONSE_CODE_FAILURE` page, not the error screen). A
  network failure arrives the other way round — started, then error.
  So a status is only REMEMBERED (`httpStatuses`), the failure is
  `failedLoads`, and both are `NavRecord`s carrying the address they are
  about plus a `settled` flag, re-applied at `onPageFinished` (which makes
  the flag stick under either order) and dropped by the next
  `onPageStarted` once spent (which is what lets a reload succeed).
  Measured on device: an error status with an empty body does NOT reach
  `onReceivedError` — Chromium substitutes its own page silently — so the
  finished DOCUMENT is what settles it (`NO_PAGE_JS`: nothing rendered, or
  the engine's error page, recognised by the untranslated `net::ERR_…` and
  the `Upside down Android` comment in its template). A status that reaches
  `onReceivedError` by the network path lends it its sentence: "the site is
  unavailable" beats "something went wrong".
- **`findAllAsync` reports a 0-based ordinal**; derive `activeMatch` from the
  count. An empty query is `clearMatches()`, not a search for `""`.
- **Assigning `el.value` leaves a React form's state untouched** — fill via the
  native `value` setter plus `input`/`change` events.

## Injected-script subsystems (`core/`)

All of these are document-start scripts plus a `@JavascriptInterface` bridge —
from outside WebView there is no other door.

- **`PageBottomBar`** tells the page where the toolbar is. The live WebView is
  full-height with the toolbar drawn over its bottom strip, so page-pinned bars
  sat under it. Bars are shifted by their own offsets (`bottom` + inset, `top`
  − inset, `margin-bottom`) — all three are needed, one per real case. A bar is
  measured by what it puts ON SCREEN (`min(bottom, vh) - max(top, 0)`), not by
  box height. Too big to be a bar = a PANEL, which is SHORTENED by its bottom
  only and is not reported as a bar. The document also gets `padding-bottom`.
  Probe with `elementsFromPoint` (the whole stack — a `pointer-events: none`
  overlay is what `elementFromPoint` answers), ACROSS the covered strip every
  `PROBE_STEP_PX` and not just on its two edge lines — a bar that clears the
  viewport's bottom by a margin of its own (duckduckgo.com's toast is
  `bottom: 15px`, 41px tall, under an 81px inset) rests between them and is
  found by neither, so it was only ever caught in the frames it crossed the
  edge in on its way in and otherwise left under the toolbar. And move
  already-known bars BEFORE re-measuring or they read as gone.
  **It runs in the TOP DOCUMENT ONLY, and that is load-bearing.** A
  document-start script is registered for every frame and
  `addJavascriptInterface` puts the bridge in every frame too, so a subframe
  running it reports for the WHOLE TAB through the same `bar()` — while
  measuring its own viewport, which the toolbar is not over. An ad frame's
  honest answer is 0, and it lands on top of the top document's: auto.ria.com's
  sticky nav was found and reported at 56px, the adtelligent sync iframe
  answered 0 a second later, the app took the floor back out from under the
  page, and the site's nav came to rest under the system navigation bar the
  moment our toolbar hid. It never recovered, because the top frame's own last
  report was still 56 and nothing repeats an answer that has not changed. Which
  frame speaks last is a race, which is why it read as a debug/release
  difference. (The subframe was also getting the inset written into ITS body
  and its own fixed bars shifted, for a strip nothing was covering.)
  **What is RETAINED must also be REPORTED.** The report is what makes the app
  stop the page's inset at the navigation bar, which moves the bar off every
  probe line: reporting only the hit-test finds made that a loop — found,
  reported, inset drops, not probed, reported gone, inset returns — the bar
  oscillating by the navigation bar's height every frame. Read the site's own value once before writing, write
  `!important`, skip writes when the value is already there (or the
  MutationObserver chases them forever), and restore on the way out.
  **A site may be TRANSITIONING the property the shift is written into**
  (duckduckgo.com's "try our browser" toast is `transition: all .25s` on the
  box its `bottom: 15px` holds down), so the box does not move on the frame
  the value is set — which reads as a lever that does not hold it, and the
  answer to that (restore, move to `margin-bottom`) is a second transition
  back down across the first. The correction is deferred by the transition's
  own duration instead; the write stays put and the site's transition carries
  it, arriving in the same 250ms as the entrance it is part of.
  **Fullscreen**: the inset is zero while the document is in fullscreen and the
  shifts are given back on the way IN. A panel shift that outlives its
  fullscreen cannot recover on its own — resting on the inset's line is what
  keeps it recognised as a panel, so the loop re-applies its own cause.
  **A page that cannot scroll** (`100dvh` app shells like claude.ai) gets every
  viewport-sized ancestor capped by `max-height` instead — padding buys nothing
  there, and in-flow footers cannot be shifted. Caps are RETAINED until the
  reason goes, and the element's own top is remembered rather than re-read.
  Gated on `!docScrolls()` so ordinary pages take the old path exactly.
- **`PageDarkening`** stands down on sites already dark. Three traps: measuring
  through our own `html { background-color: #fff !important }` reads OUR white
  (measure with the style element `disabled`, synchronously in one task);
  `body` paints nothing on an app shell (hit-test six viewport points, walk to
  the first OPAQUE ancestor, take a majority); and site theme flags
  (`html[dark]`, `data-theme`, `data-color-mode`, `data-bs-theme`, a
  `dark`/`night` class) are true before first paint, so read them first. A
  `light` flag is not a veto. Stand down by `disabled`, not by removing the
  `<style>`, with a `MutationObserver` on `<html>`/`<body>` attributes.
  **The DOCUMENT'S OWN CANVAS outranks the hit-test vote**, which is only
  reached when `html` and `body` are both transparent. `html`/`body`'s
  background is the one surface nothing laid over the page can move; a
  full-screen ad, a consent scrim, a sticky player or a dark hero photo is
  most of the viewport for a moment and none of them is the site's colour
  scheme. The vote skips `fixed`/`sticky` boxes and iframes on the way up
  (passing OVER them, not abandoning the point — the chain they hang off is
  still the shell).
  **And a MEASURED verdict is LATCHED**: only the first 10s of a document can
  revise it, and a disagreeing measurement has to repeat before it is
  believed. `apply()` runs on every observer tick, and an ad-heavy page ticks
  constantly — re-deciding each time is what made zdnet.com flip between dark
  and light for as long as it was open. After the window only a theme FLAG
  can flip it, because a flag is the site speaking rather than the page being
  measured. (Flags are also read without the `disabled` round trip — our CSS
  touches neither attributes nor `color-scheme` — so a settled page does no
  style toggling at all.)
  **A subframe must NOT darken itself.** The document-start script is
  registered for every frame, but the top document's filter already covers
  every iframe under it (the frame is rasterised and the ancestor filter
  applies to the result), so a frame that filters too is inverted TWICE and a
  white ad comes back white — a light slab over a dark page, which is the
  other half of the zdnet complaint. A subframe installs the
  picture-preserving `img`/`video`/`canvas` half ONLY, and holds even that off
  until the top frame's verdict reaches it by `postMessage` (each frame
  relaying to its own children; `DISABLE` broadcasts too, since an evaluate
  reaches the main frame alone) — under a page that stood down, nothing is
  inverting the frame and re-inverting its images is a negative.
  **A childless element is not automatically small**: the background-image
  scan's area cap applies to leaves too (at 0.6 of the viewport rather than
  0.4), or an empty full-bleed hero or ad slot un-inverts most of the screen.
  Each element is scanned ONCE (a `WeakSet`) — 4000 `getComputedStyle` calls
  per tick on an ad page is a frame budget — **and "once" is what a LAZY
  LOADER breaks**, so the set is re-opened for the elements that actually
  changed. keddr.com's article thumbnails are `background-image` on an `<a>`
  and, until they scroll into view, that background is a grey placeholder
  GRADIENT: read once, no `url()`, no keep, answered for good. The photo that
  arrives a screen later lands in an element nothing will look at again and
  comes out as a negative among correct ones. What changes when it lands is
  the element's own `style` or `class` — every lazy loader writes one or the
  other — so a subtree `MutationObserver` filtered to those two DELETES its
  targets from the scanned set and schedules an `apply()`; the re-read is
  bounded by what moved rather than by the size of the document, which is the
  cost the set exists for. Nothing of ours is in that filter (the keep
  attribute is `data-yuku-dark-keep`), so the tagging cannot feed it.
  **That observer starts at DOCUMENT START, on `<html>`** — not at
  DOMContentLoaded on `<body>`. A lazy loader runs while the document parses
  and hands the on-screen thumbnails their photo before DCL (keddr ~700ms,
  placeholder scanned at ~330), so a DCL-attached observer missed exactly the
  pictures in view and they stayed negatives.
  **The first frame is decided IN a `requestAnimationFrame`.** At document
  start there is no CSS, so a site whose dark theme comes from its stylesheet
  (github.com: `color-scheme` and canvas both behind `prefers-color-scheme`)
  measures as nothing and gets the filter; the CSS then blocks rendering, and
  the first frame after it painted inverted until DOMContentLoaded or a tick
  looked — the "loads inverted, snaps to dark" flash. A rAF loop (top frame,
  counted in frames, stops 3 frames past the first `paint` entry) re-decides
  after styles land and before the paint. Two helpers: "nothing painted" is
  NOT latched as light before the first paint (it is missing CSS, and a
  latched light would make the real dark canvas a disagreement needing a
  repeat — one inverted frame), and Primer's `data-color-mode="auto"` +
  `data-dark-theme*=dark` is read as dark when `matchMedia` says the scheme
  is dark (matchMedia, not `builtDark` — the filter can be on in a WebView
  built light).
- **`PullGate`** guards pull-to-refresh. `canChildScrollUp` on the WebView only
  knows the outermost scroll. Three gates: a direction lock latched at touch
  slop, an `elementFromPoint` hit test (in CSS px — view px / `WebView.scale`,
  NOT offset by scroll, stepping through shadow roots) walking ancestors for a
  scrolled-down one or one claiming `touch-action`, and a wrapper on
  `EventTarget.addEventListener` tagging nodes with **non-passive** touch
  listeners (on window/document/body/documentElement, only with an explicit
  `passive: false`). A retrospective `defaultPrevented` callback drives a
  mid-pull bail via a synthesized ACTION_CANCEL. The probe is async and never
  awaited (blocking touch dispatch on the renderer is unaffordable); a
  `gestureId` counter keeps a late answer off the next touch. When a gate says
  no, SKIP `super` entirely rather than returning its verdict.
- **`PasswordForms`** captures logins. "The user logged in" has no single
  event, so it hangs off `submit` (capture phase), a wrapped
  `HTMLFormElement.prototype.submit`, Enter in the password field, a click on
  whatever plays the button, `pagehide`, and `history.pushState`; first wins,
  rest dedupe in the page. A capture is PARKED and only prompted on the tab's
  next `onPageStarted` (2s timer for SPAs) — that is what keeps a mistyped
  password out of the prompt. **Nothing on the bridge returns a password**;
  filling is Kotlin-initiated only through `window.__pwFill`.
- **`ReaderMode`** turns a page into the article in it. The extraction does
  NOT wait to be asked: a soft paywall sends the whole article (the crawler
  gets the same HTML) and a script takes it back seconds later, so by the time
  a tap arrives the DOM has been truncated. The script therefore harvests from
  first paint — six scheduled passes plus a throttled MutationObserver — and
  **keeps the longest result it has ever seen for the current URL**; the
  truncation is just a shorter harvest that loses. Two more sources are read
  alongside the DOM and compete on length: schema.org `articleBody` in
  `ld+json` (shipped for search, rarely metered) and hydration blobs
  (`__NEXT_DATA__`, `application/json` islands — the least trustworthy, so it
  must look like prose: six sentence terminators minimum). The scorer is
  Readability's, compressed: leaves score 1 + commas + capped length, pushed up
  five ancestors with a per-level divisor, class/id patterns ±25, winner
  discounted by link density, well-scoring siblings appended. **Two deliberate
  departures, both about paywalls**: invisibility is not disqualifying (a
  metered page hides its remaining paragraphs) and `hidden` is out of the
  NEGATIVE pattern. Drawn as a `<dialog>` + `showModal()` — the TOP LAYER, so
  a paywall overlay's `z-index: 2147483647` is beaten without outbidding it,
  everything under it is inert, and closing is lossless (the page is untouched
  underneath, scroll position and all). The article lives in a SHADOW ROOT so
  the site's stylesheet cannot reach it; a `dialog` cannot host one (not on the
  spec's list), so it holds one plain `div` that does. Lazy `data-src`/
  `<noscript>` images are lifted BEFORE attributes are stripped — nothing is
  going to fill them in later. Unknown tags are UNWRAPPED, not removed, or an
  article inside a custom element comes out empty. State is per tab, in memory,
  and spent by `onPageStarted`; availability comes back over the bridge and is
  what the menu row is enabled by, in the menu sheet and the overlay's dropdown
  alike (both back chains close it before they walk page history). Verified off-device against jsdom: a
  12-paragraph body truncated to 2 still reads all 12.
  **Look and motion** are the page's job too — none of `Motion.kt` or the
  theme is reachable from inside a WebView, so the vocabulary is restated in
  the script and the two have to be kept in step. The transition is a plain
  CROSSFADE (240ms in on `PaneDecelerate`, 160 out on `Accelerate`) and
  deliberately nothing else: the reader and the page are two views of the same
  document, in the same place, at the same size, so there is no journey to
  animate — movement would invent an arrival that did not happen, and on a
  full-screen surface any of it uncovers bare page at the edge.
  **The fade runs on the shadow HOST, never on the dialog**: `all:initial` with
  `!important` — the dialog's armour against a page that styles `dialog` —
  expands to every longhand, so the dialog's own `opacity` is
  `initial !important`, and an important author declaration beats a Web
  Animation outright (the animation ran and did nothing; so did an earlier
  scale). The dialog is therefore `background:transparent` and the paper is
  painted by `.scroll` inside the shadow root, or an opaque shell would sit at
  full alpha under the fading article with nothing to see the page through.
  Chromium's UA `dialog::backdrop` 10% black wash is neutralised by a
  `<style>` in the page (a pseudo-element is out of reach of an inline style),
  removed again at teardown. The dialog
  stays in the DOM, modal, for the whole exit, so `close()` is called at the
  END; `openState.leaving` is what an open landing mid-exit cancels to bring
  the same (still-scrolled) article back, and the `harvest` guards ask
  `isOpen()` rather than `openState` because of it. Kotlin reports the state
  the moment it asks, not when the animation lands — a switch that waits 160ms
  has not taken the tap. `prefers-reduced-motion` skips the animation and the
  end state is simply written.
  **Settings** (`core/ReaderSettings.kt` — size ladder, paper, face, leading)
  are a PREFERENCE and live in `BrowserStore`, unlike whether the reader is
  open, which is per tab and in memory: how big the text is is the same answer
  for every article the user will ever read. `ReaderTheme.Auto` is resolved in
  Kotlin (`styleJson`), since "what counts as dark" is the tab's override then
  the app's setting and the page cannot ask. A change restyles every OPEN
  reader in place — the scroll position is the one thing a reader holds that
  the user cannot get back, so nothing rebuilds it. The menu row is split like
  Ad blocker's: the switch toggles, the ROW opens `Sheet.ReaderSettings`
  (a sheet, not a destination, so the article stays visible above it while it
  is being adjusted), and the row is enabled whatever the page is. Neither the
  switch nor any setting closes the sheet — unlike every other menu row that
  acts on the page: the reader is a switch, and the third of the screen above
  the sheet is enough to see that it worked and to flip it straight back.
- **`CosmeticFilters`** hides cookie banners — a consent banner is the site's
  own markup, so there is nothing to refuse at the network layer. ~300KB
  stylesheet injected at document start (before first paint); site-specific
  rules looked up by host walk in `onPageStarted` (a once-per-WebView script
  cannot know the host). CSS has no forgiving selector parsing outside `:is()`,
  so one bad selector kills its whole rule — chunk 40 to a rule, drop extended
  pseudo-classes (`:has-text`, `:upward`, `:style`), skip `~`-exception and
  `#@#` rules whole. `FALLBACK_SELECTORS` works with no download. The scroll
  lock is undone only when a `MODAL_ROOTS` element is present and only on the
  properties actually locked (read off `getComputedStyle`). **Nothing is ever
  clicked on the user's behalf** — consent is a legal declaration.

## Feature notes worth keeping

- **Ad blocker** (`AdBlocker`, `BlocklistFeeds`, `BlocklistStore`,
  `ui/AdBlockPane.kt`): three switches (ads = HaGeZi Pro wildcard, trackers =
  AdGuard CNAME-cloaked — a CNAMEd tracker looks first-party from every angle
  but the DNS record, cookie banners = cosmetic). Index is a sorted `LongArray`
  of FNV-1a hashes (450k strings = tens of MB; hashes = 3.6MB) with an
  allocation-free suffix walk. Storage in `filesDir/blocklist/`. `meta.json` /
  `config.json` carry a `schema` stamp and a report from another version is
  discarded WHOLE; `heal()` deletes a zero-length `hosts.txt`; the ABSENCE of a
  list triggers an update alongside age (otherwise a timestamp without a list
  is a silent no-op forever). Imported lists are COPIED and re-parsed, never
  kept as a `content://` URI (the read grant dies with the process); MIME
  filter is `*/*` with the CONTENT deciding, via `OpenDocument`. Own rules
  apply at `buildIndex` time (no refetch needed); feed toggles need a download.
  An ephemeral (overlay) session may block but never updates the list.
  **It is a SETTINGS PANE, not a menu destination** (`SettingsPane.AdBlock`,
  under Privacy). Everything on it is true of every site at once, and a menu
  over a page is for the page in front of the user — so the menu row it used
  to sit behind is Site settings now (below), and the two halves went opposite
  ways. `AdBlockPane` is therefore a `PaneHeader` + `Column` inside
  `SettingsScreen`'s own scroll, not a `ListScreenScaffold`.
  **The tracker feed is written TWICE**, into `hosts.txt` with everything else
  and into `trackers.txt` on its own, so that a site with a per-site tracker
  exception can have exactly that subtracted. The merged file remembers no
  provenance, so this is the only way to answer it. The two sets are DISJOINT
  by construction: `update()` walks the ads feed first and dedupes as it goes,
  so the tracker feed's *accepted* lines are precisely the domains no earlier
  list covered — which is what makes "ads but not trackers" `in hosts &&
  !in trackers` rather than a subtraction that would also unblock everything
  the two lists agree on. `buildTrackerIndex()` caches to `trackers.bin` with
  a key of its own; it is never latched on (`AdBlocker.awaitIndex` is only for
  the merged one) because a site with no exception never reads it. SCHEMA is
  3 for it: a discarded meta reads as "never updated", which is what makes the
  age gate fetch once and fill the file in, and until it does the index is
  empty and an exception simply has nothing to subtract — the ads list still
  blocks, which is the safe direction.
  **Never set `Accept-Encoding` by hand on a feed request.** OkHttp does
  transparent gzip only while the caller leaves that header alone; asking for
  gzip explicitly makes the app responsible for inflating, and all three feeds
  serve it. Setting it fed raw DEFLATE to `charStream()`, so every line failed
  to parse, all three feeds reported success with ZERO rules, and the empty
  `hosts.txt` was renamed into place — a blocker that downloaded 1.6MB and
  blocked nothing, re-doing it every launch. Hence also: a feed that ACCEPTS
  nothing has not succeeded (`accepted > 0 || cosmetics > 0`), or a 200 that
  parses to nothing overwrites a good list with an empty one.
  **The index is cached as `index.bin`** (the sorted `LongArray`, keyed by
  `hosts.txt`'s length+mtime plus the custom entries and imported lists folded
  in at build time). Parsing the 11MB list took 11.3s on device — longer than
  the splash, so the first page of every launch, which is the RESTORED TAB,
  finished loading unfiltered. Two fixes: `cleanDomainHash` skips the
  `split`/`trim`/`lowercase` per line for a file we wrote ourselves (11.3s →
  3.0s), and the cache makes an ordinary launch ~10ms. `AdBlocker.awaitIndex`
  is the belt to that brace — a bounded 1500ms latch that parks a SUBRESOURCE
  thread until an index exists (the main frame returns before it, so a page is
  never held up) and fails OPEN. Verified on device: latch engages at 1.03s and
  still blocks. `heal()` also re-stamps a config written by an older build —
  `loadConfig` reads one safely either way, but the file otherwise sits on disk
  naming feeds this build has no id for.
- **Per-site settings** (`core/SiteSettings.kt`, `ui/SiteSettingsSheet.kt`):
  the menu row where Ad blocker used to be. Keyed by REGISTRABLE DOMAIN, not
  origin (unlike `SitePermissionGrant`) — nothing here is a capability, so a
  user who turns the blocker off for a site means the site.
  **The three filter switches are EXCEPTIONS, the other two are OVERRIDES**,
  and the asymmetry is the design: `blockAds`/`blockTrackers`/
  `hideCookieBanners` are `Boolean` defaulting true, so a site nobody has
  opened the sheet for is filtered exactly like every other and a build that
  adds a filter owes nothing to records written before it; `dark`/`zoom` are
  NULLABLE, because both directions are worth pinning and "follow the app" is
  not the same fact as either value. A record that comes back to ordinary is
  REMOVED (`isDefault`), never stored as a row of defaults. The three switches
  are DISABLED where the global is off — a switch that is on while nothing is
  being blocked is telling the user something untrue.
  **The resource thread never resolves anything.** `shouldInterceptRequest`
  runs on a background thread per subresource, so `tabFilters`
  (`ConcurrentHashMap<Long, AdBlocker.Rules>`) is written once per navigation
  from `onPageStarted` (and seeded in `create`) and read as one lookup.
  A link preview keeps its own `AtomicReference`, since it is filtered by the
  site it is SHOWING and can be read onto another one.
  **Dark is applied as a filter mid-navigation, not as a rebuild.**
  `prefers-color-scheme` comes from the context a WebView was constructed
  with, and rebuilding one while it is loading would park and restore a page
  mid-flight — so `syncSiteState` moves the filter half only and `builtDark`
  stays honest about the context, with `darkApplied` recording what is
  actually on. Setting the switch from the sheet DOES rebuild (the page is in
  front of the user; it is the menu tile's own path, cross-fade and all).
  Resolution order is `pageDarkOverrides[tab]` → site → app setting, and a
  site's answer survives a change to the app setting where the tile's does
  not: one is a standing decision, the other is a passing one.
  **Cookie banners need both halves.** The stylesheet is a document-start
  script registered per WebView, which is what the NEXT navigation gets; the
  document already on screen has it in it, so `CosmeticFilters.disableScript()`
  turns those `<style data-yuku-cosmetic>` elements off in place (`disabled`,
  not removed, so giving it back costs nothing). The script also reads
  `window.__yukuCosmeticOff` at injection time, for a frame that injects after
  the switch was thrown.
  **Private tabs write to a SHADOW.** The sheet works there — a page that
  breaks under the blocker breaks there too — so writes land in the live map
  (that is what makes them take effect), the key and whatever it displaced go
  into `privateSiteSettings`, `persistNow` swaps the originals back in, and
  closing the space restores them.
  A filter change reloads the CURRENT tab only; other tabs pick it up on their
  next load rather than having a form thrown away in a tab nobody asked about.

- **The page runs UNDER the status bar** (lens off; `PageTopInset.setStrip`,
  `ui/StatusStrip.kt`). The WebView hangs above the page box by the status
  bar (`overscanTopPx`, same mechanism as the lens), the document is padded
  and edge bars moved by it. **The page MEASURES the strip, the APP DRAWS it.**
  It was first drawn in the page (a fixed element on `<html>`), which put the
  fade and the header fill into every capture — a card showed the veil, not
  the page, and nothing could take it back out without flashing it on screen.
  Now the script reports `StatusStripReport` (edge colour, header colour and
  share, scroll ramp; colours put through PageDarkening's own `feColorMatrix`
  read out of the DOM, so they are what the screen shows) and
  `drawStatusStrip` paints it over the live page AND the thumbnail stand-in at
  `1 - shrink`: none on a card, all of it at full screen, growing with the
  zoom. The top FILL is lens-only for the same reason. Looks, never a switch:
  `veil` (a PLAIN overlay: edge colour at 0.85, flat over the top half of the
  bar, linear to 0 at `STATUS_STRIP_FADE` = 4dp below it — narrow and dense,
  hugging the screen's edge), `cap` (solid, only at the page's
  top where the bar is over blank padding, gone over the first strip of
  scroll), `head` (a header's own paint — pseudo-elements included,
  github.com's is `::before` — at the share of the header on screen; the veil
  gives way to it). There is deliberately NO blur and no long eased tail: a
  `StatusBlur` RenderEffect on the WebView plus a 0.72 veil eased over 24dp
  read as a haze filter over the page, and was removed. A bar that hides by a
  TRANSFORM (keddr.com's header) is judged hidden by where its running
  transition will LAND (`settledShift`), not by the frame it is on, and a
  held-hidden bar is re-checked every measure — judged mid-slide, it was
  never moved and slid back in under the bar. **The edge sampler never reads
  `html`'s pseudo-elements**: `html::before` is our own top fill
  (`paintTopFill`), painted in the last reported colour, and reading it back
  LATCHED that colour — zdnet.com's strip stayed its navy header (and, before
  `topHeaderPaint` was limited to boxes that begin the document and are in
  the hit stack, the lime of a clipped `site-header__dropdown-menu`) over
  every white article. **A masthead that leaves the flow keeps its room**
  (`updateStartHold`): keddr.com's mobile header turns `fixed` once the
  scroll passes its own height, with no placeholder, and under our padding
  that switch lands while it is still on screen — the page jumped by 53px
  each way. When a short full-width box the document starts with turns
  fixed/absolute AND its parent shrinks by its height, that height is added
  to the body padding (same microtask as the site's class change) until it
  returns to the flow. Colours ease in
  `StatusStripPaint` and are read in draw only. Google paints an image's
  frame in the image's dominant colour, so the ground sampler skips any box
  no bigger than twice a picture it holds, and anything under half the page's
  width (a red status bar came off a red arrow in a thumbnail). Never probe
  for a pseudo via `el.pseudo`: Chromium elements HAVE that member. Colours and shares are read
  on a rAF loop that runs only while something moves (scroll, transitions,
  animations): keddr.com's header hides by a 0.3s transform AFTER the scroll,
  and a measurement taken on the scroll flipped the strip back and forth. The
  ground is sampled from `elementsFromPoint` at the edge — first opaque
  background colour OR gradient stop (github.com's hero is a gradient over a
  transparent colour; read by colour, the strip was the body's white on a
  navy page), skipping media and fixed/sticky boxes; a header that paints
  nothing (github's) counts as no header. It reports dark/light for the
  status bar icons (`statusStripDark`). **The page box's top padding sits
  INSIDE `aeroPageGlass`**: a RenderEffect crops to its layer, and outside it
  the strip vanished whenever the toolbar glass was on. The switcher's root
  is laid out taller by the strip so its ground reaches under the bar too.
  **A page that cannot scroll** (root `overflow: hidden`, kontur.systems) hangs
  its layout off `absolute` boxes whose containing block is the viewport;
  those count as fixed there (`viewportAnchored`, held while absolute).
  A moved box whose `class` changes is RE-BASED (values handed back, read
  again): our inline `!important` otherwise overrides the site's own restyle
  (kontur's buttons take `.shift-down` to clear the counters bar). And the
  site's values are read with `transition: none` for the instant of the read
  (`record`) — mid-transition a property reads as the frame it is on — but
  ONLY when the lever property itself transitions: `none` cancels every
  running transition, keddr's header slide included. Shells are taken only
  at scroll 0 and released once the document scrolls (a menu's scroll lock
  reads as "cannot scroll"). **A fixed box inside a moved fixed box is
  moved too** unless something between them (`transform`, `filter`,
  `contain`…) makes it the containing block (`containsFixed`) — auto.ria's
  filter sheet sits in a fixed scrim and was left under the bar. **A panel's
  cap is iterated, not computed**: a `fixed; margin: auto` sheet with no top
  of its own reports its resolved top, and with `top` + PageBottomBar's
  `bottom` written it is CENTRED, spilling above the line; `capPanel`
  shortens until the top lands. A panel is re-taken fresh when `innerHeight`
  or the inset changes (the keyboard), since a cap only ever shortens — and
  when PageBottomBar's inline `bottom` on it changes (a `bottom0` read under
  it is stale once it comes off). PageBottomBar's `capTo` never GROWS a panel
  whose top is already at/below its line, or the two scripts trade 23px every
  250ms. **Both scripts answer observed changes IMMEDIATELY** (MutationObserver
  microtask, `resize`, a ResizeObserver on held panels — delivered before
  paint), bounded to 4 runs a frame / 24 a second, so a popup's first painted
  frame is already settled; throttled answers painted every step in between.
  Panels get no box-shadow fill under the strip
  (a 0.8-black scrim made opaque showed as a black band through the veil).
  The inset also goes into `html`'s `scroll-padding-top` (anchors, focus),
  and the error screen is laid out up under the bar like the page.
  Verified on device: github, keddr (incl. menu mid-page), kontur, YouTube,
  Google SERP, apple.com, MDN anchor, error page, simulated dark filter,
  pinch 2.2x, element fullscreen, landscape and back, link overlay (no strip).
  Previews stay the page BOX; the strip is captured beside them as
  `Tab.thumbnailTop` (`<id>.top.webp`, split off the same exact capture,
  dropped by a software draw). **A card is the page BOX only** (box-shaped,
  no strip): under the strip is content scrolled up past a sticky header or
  the document's top padding, which raw on a card read as content over the
  header and a band of fill. `shrinkGeometry` scales `above` by
  `1 - shrink`, so the crop window grows the strip in from the box's top as
  the card zooms to full screen; the zoom stand-in draws `thumbnailTop` there
  (edge colour when none) under `drawStatusStrip`, whose head/cap are drawn
  at FULL above the box (only the zoom uncovers them) and whose veil eases
  from full at the screen edge to `amount` at the box top — so the raw strip
  is never seen and nothing changes colour when the zoom lands.
- **Sideways, a switcher card is SQUARE** — sized off the HEIGHT (0.62 of it,
  capped at 0.40 of the width), where upright it is 0.60 of the WIDTH at the
  page's own aspect ratio. The upright rule costs nothing (a tall page in a
  60%-wide card is still the tallest thing that fits); applied to a landscape
  screen it gives a letterbox strip with one band of page in it, two to a
  display with room for four. A card no longer has to be the page's shape:
  `applyShrinkTransform` scales the page by ONE factor on both axes — the
  LARGER of `card/page` width and height, so the card is COVERED rather than
  fitted (fitting would leave bare background along two of its edges) — and
  crops the surplus with a rounded window. That window is a `Shape`
  (`ShrinkCropShape`), not a draw-phase clip: a uniform rounded rect, even one
  smaller than the node, resolves to a plain `RenderNode` outline the platform
  clips to in hardware and antialiased, which a path clip over a live WebView
  every frame would be neither. It interpolates the CARD's two dimensions, so
  it equals the drawn page at fullscreen and the crop appears gradually, and it
  is CENTRED because `offsetX`/`offsetY` put the page's centre on the card's.
  Two separate x/y factors are what made the page visibly narrow on its way
  into a square card. Cropping is also what the card's own thumbnail does with
  the same picture (`ContentScale.Crop`), so the live page and its still
  stand-in show the same band of it throughout — that is the constraint to keep
  if either side is changed. `ZOOM_SCALE` (the row's zoom, `1/CARD_WIDTH_FRACTION`) becomes
  a MEASUREMENT there — `maxZoom = maxWidth / cardWidth`, capped at 2.4 — and
  is threaded into `applyRowZoomCompensation`, whose `settled` ramp must run
  against the same top of range. Width is the axis the zoom matches because the
  pivot card is hidden for the whole gesture (the live page stands in for it)
  and what the zoom actually places is its neighbours, spaced by width.
- **Tabs & switcher**: `selectTab` moves the tab to the END of the list, which
  is right for a switcher tap and wrong for walking the row by position — hence
  `stepToTab` (no reorder) with the move deferred and paid by
  `promoteSteppedTab` the moment MRU order is wanted. Eviction goes through
  `parkAndDrop`, never `destroy`, and `views` is touched on every `webViewFor`
  so its order is least-recently-USED. The handoff cover waits on
  `awaitPagePainted` (`postVisualStateCallback`, capped), preceded by two
  frames for the reparent — asking a not-yet-attached WebView for its next
  frame asks nothing. A preview is only applied when `pageExposed` at LANDING,
  not just at request time; `drawThumbnail` refuses a flat draw when the tab
  already has a picture; a capture for a closed tab is dropped.
  `capturePageNow` runs at `onPause` — by `onStop` the window has no surface.
  `pageExposed` is derived from `pageIsWhatsOnScreen` plus the surfaces that
  sit beside the page (find bar, password cards), never a second hand-written
  list. The tabs/+/menu buttons all ask for a copy on pointer DOWN
  (`onCoverPress`) — a finger takes tens of ms to lift, which is the frames the
  copy needs. A finger lifting off the PAGE also queues a capture (the only
  signal for canvas/WebGL/SPA content).
- **Private mode is a SPACE, not a per-tab flag.** `_tabs` holds both; the
  filter happens in `BrowserScreen` synchronously in composition (a
  `combine(...).stateIn` seeds with its initial value and one frame of "no
  tabs" throws up the new-tab sheet). Inside the ViewModel every "pick the next
  tab" goes through `spaceTabs()`. `_currentTabId` is written only via
  `setCurrentTab`, which keeps each space's own copy. Blur: `Modifier.blur` and
  `renderEffect` are silent no-ops below API 31, and a no-op here SHOWS the
  page — so below S the preview is dropped to `alpha(0f)` instead. A
  RenderEffect applies in the layer's own space BEFORE the scale, so the radius
  is divided by `scaleX`; the shrink radius is quantised to 6px steps because
  the downsample-level flicker is driven by the radius CHANGING.
- **Custom tab overlay** (`CustomTabActivity`, `CustomTabsProvider`,
  `ui/CustomTabScreen.kt`): the mechanism is task placement —
  `android:taskAffinity=""` and no new-task flag, so it stacks on the CALLER's
  back stack and `finish()` returns there. That is what Chrome's custom tab is;
  there is no API for floating over another app. The VIEW/BROWSABLE filter
  lives here; `MainActivity` keeps LAUNCHER + APP_BROWSER. An app picks us only
  because `CustomTabsService` is exported. The session is a second
  `BrowserViewModel(ephemeral = true)` that never writes (two writers = two
  browsers saving over each other) and restores SETTINGS only; `parkedFromDisk`
  is guarded or tab id 1 restores the real session's page. A bookmark is the
  one thing it keeps, via `BrowserStore.updateBookmarks`. Caller colours are
  ignored on purpose; caller menu items are honoured minus `OWN_ACTIONS`.
  The overlay follows the CURRENT tab, not the first (they differ exactly when
  a popup opens).
- **External links** (`core/ExternalLinks.kt`): `route` returns
  `Browser`/`Consumed`/`LoadInstead` — the third is why it isn't a boolean
  (`intent://`'s `browser_fallback_url` is a different URL). Schemes the
  browser cannot render (`mailto`, `tel`, `sms`, `geo`, `market`) always leave;
  an app scheme with nothing installed is swallowed, not error-paged. The https
  half is narrow: main frame + `hasGesture()` only. "Is there an app for this"
  CANNOT be asked of the package manager on API 30+: `<queries>` can name a
  scheme but not every host, so an app whose filter names `youtube.com` is
  invisible, and on API 31+ `MATCH_DEFAULT_ONLY` on a web intent returns only
  domain-APPROVED handlers — measured empty. So the launch itself is the
  question: implicit VIEW + `FLAG_ACTIVITY_REQUIRE_NON_BROWSER`, and
  `ActivityNotFoundException` means "keep it here" (no browser chooser can
  appear — the flag excludes browsers). The `queryIntentActivities` path,
  minus general browsers found by probing a host under the reserved `.invalid`
  TLD, survives only below API 30, where it launches with `setPackage`.
  Package visibility does NOT block starting an implicit intent, so the
  `intent://` and app-scheme paths need no equivalent. An `intent://`
  string is page-composed, so strip selector/component/flags and keep only a
  browsable VIEW. Needs the manifest's `<queries>` entry on API 30+.
- **Passwords**: two arrangements, never both. **External** (default) — fields
  go to the platform autofill service (`IMPORTANT_FOR_AUTOFILL_AUTO`),
  everything here stands down and the vault is HIDDEN, not emptied. **Yuku** —
  fields are withheld (`..._NO_EXCLUDE_DESCENDANTS`) so the keyboard cannot
  offer a login beside our own bar. The provider's name is read off
  `Settings.Secure.autofill_service` (no public API; `AutofillManager.isEnabled`
  is the fallback) and needs a `<queries>` entry for
  `android.service.autofill.AutofillService`. Launch
  `ACTION_REQUEST_SET_AUTOFILL_SERVICE` with NO data URI — a `package:` URI
  means "make THIS app the provider". Credential Manager was removed: it files
  logins under the calling APP, not the site, so they matched nothing.
  **The vault also holds ADDRESSES and CARDS** (`core/AutofillEntries.kt`,
  `core/FormFields.kt`, `ui/AutofillPanes.kt`), which is what closes the gap
  the "Yuku manages passwords" arrangement opened: the fields are withheld
  from the platform service, so a checkout used to get an offer from nobody.
  Same file, same key, same device lock — a home address and a card number are
  secrets in exactly the way a password is. Two switches on the pane, not one:
  a street the user has already handed to a courier is not a card number.
  **Nothing is CAPTURED from a page**, unlike a login: a card number scraped
  off a checkout is a copy this browser took without being asked. Both are
  typed once in Settings, which is the same amount of typing as the first
  checkout and none after it. **The security code is never stored** — it is
  the one field whose whole purpose is proving the card is in your hand.
  `FormFields` is `PasswordForms`' twin (document-start script + bridge that
  returns nothing; filling is Kotlin-only through `window.__afFill`). A field
  is classified by the site's own `autocomplete` token FIRST — and a site that
  named a token we don't handle (`cc-csc`, `one-time-code`) stops the walk
  rather than being guessed past, which is how a security code would otherwise
  end up with a card number in it. The name/id/label patterns are the fallback
  and their ORDER is load-bearing: "address" is inside "email address", "name"
  inside "name on card", "month" inside "expiry month". A `<select>` is filled
  by searching its options and left ALONE when nothing matches, never cleared.
  An address form is not reported on a document that has a visible password
  field — an email box above a password is a sign-in, and the password bar
  owns it. Verified off-device with `node` against a stub DOM: tokens,
  patterns, group crossing, the untouched CVC and the unmatched select.
  The vault is `filesDir/passwords/vault.bin`, AES-256-GCM under an
  AndroidKeyStore key (GCM so an edited file fails to authenticate; fresh IV
  per write), excluded from backup (`xml/backup_rules.xml`,
  `xml/data_extraction_rules.xml`) since the key isn't backed up. The pane is
  behind the device lock (`ui/DeviceLock.kt`, androidx.biometric — which forces
  `MainActivity` to be a `FragmentActivity`), asked for on the way IN; a device
  with no lock falls through. The key is NOT
  `setUserAuthenticationRequired` — the synchronous read in `init` must work.
- **Search engines**: `SearchEngine` is a data class (a custom engine must be
  as much of an engine as DuckDuckGo). Built-ins keep their old enum names as
  ids. `%s` / `{searchTerms}` marks the query; a URL with no placeholder gets
  one appended. Disabled engines are stored as the EXCLUSION so a new built-in
  is offered to everyone. The picker is a strip of the engines' own icons whose
  FIRST icon is the button; the five built-ins ship as assets
  (`ui/EngineIcons.kt`, `res/drawable-nodpi/engine_*.png`) trimmed to their ink
  and scaled by the square root of alpha MASS (not a bounding box), because
  chrome cannot afford a blank. The strip's ORDER is settled in the event that
  opens it, not in composition or an effect. Settings' pane is one column where
  a row's SECTION is its place in it; arrangement writes bypass the debounce.
- **Favicons**: fetched at 128px (`Favicons.ICON_PX`, s2 serves its nearest
  size) into a size-stamped cache dir, drawn `FilterQuality.High`. Every bitmap
  is measured once (`measureInk`): nothing drawn = fall back to a letter AND
  DELETE the file (a blank cache entry answers every later fetch forever); a
  mark on a transparent ground gets a chip coloured by the ink's own luminance.
  s2 serves bing.com as a WHITE magnifier — a fixed white chip made it
  invisible twice over.
- **TUI special theme** (`core/SpecialTheme.kt`, `ui/theme/Tui.kt`,
  `ui/theme/SpecialText.kt`): a whole-look override kept OUT of `AccentTheme` —
  the accent row draws its entries as coloured dots and "the UI becomes a
  terminal" is not a colour. Picked in Settings' "Special themes" section,
  which is a grid of big round targets four to a row (`SpecialThemeGrid`,
  glyphs mapped in `SpecialTheme.icon()` — Compose stays out of `core/`) and
  not the segmented `Picker` the rest of that screen uses: these are
  interfaces, not modes, and each is shown rather than described. No `Hint`
  under it for the same reason. **Default is a cell in that grid**, first of
  the four: it used to be reached only by tapping an accent, and that way out
  went when the accent stopped meaning "leave this look" (below).
  Three mechanisms, all hanging off `LocalTui` (a composition local, NOT a
  global, because Settings re-provides `BrowserTheme(special = None)` for its
  own subtree, and the ad blocker pane does the same — the screens that turn
  it off must show the ordinary look):
  `specialCorner(dp)` replaces every `RoundedCornerShape(` call site (the ui files
  import it instead) and returns a 0.dp corner, `SpecialCircle` does the same for
  `CircleShape`, and `SpecialCornerScale` is the multiplier for the four radii
  computed in the layout/draw phase (`cornerPx`/`cornerRadiusPx`, folded in
  where the density read is, since a shape can't be asked for from a
  `graphicsLayer` block). Text is lowercased by a `Text` wrapper in
  `ui/theme/SpecialText.kt` that every ui file imports in place of Material's —
  there is no hook between a `Text` call and its layout, and a `TextStyle`
  cannot change characters; text FIELDS are deliberately untouched (an
  address being typed is content). Square Material components come from
  `TuiShapes` (pass `OrdinaryShapes`, never `MaterialTheme.shapes`, for the
  non-TUI branch — Settings nests inside the TUI theme and would inherit it).
  **Type is Iosevka Term** (`res/font/iosevka_term_{medium,semibold,bold}.ttf`,
  OFL, unhinted, subset with `pyftsubset` to Latin/Greek/Cyrillic/punctuation/
  arrows/box drawing — ~256KB each against ~9MB whole). 0.5em cells (Droid
  Sans Mono is 0.6) at Roboto's x-height, round bowls. Set a step HEAVY:
  Normal is the Medium cut, Medium SemiBold, SemiBold/Bold Bold.
  `TUI_SIZE_PARITY` 1.05, tracking 0. IBM 3270 Condensed was tried first and
  dropped: as narrow, but thin, small (x-height 0.425) and angular.
  **Colours are a MONOCHROME MONITOR built from the accent**
  (`tuiScheme(seed, isDark)` in `ui/theme/Tui.kt`, then `tintedWith`). A
  green-screen had one phosphor and everything was that phosphor at some
  drive level, the unlit glass included (cool-retro-term's Apple ][ profile
  is #4DFF6B on #001100). The seed's hue picks the tube — green is P1, amber
  P3, Graphite P4 paper-white (a seed at ≤15% saturation stays grey
  throughout) — but on the DARK end the colour is carried by LIGHT, not by
  the palette: grounds are near-black with a breath of hue (L 3.5/2.2/1.2%,
  S 12%; page lightest, toolbar under it, floor under that — the order is
  this theme's grammar) and inks are NEAR-WHITE (L 95→34%, S ≤18%). An
  earlier ramp tinted the inks (L 87% at S 60%) and the grounds (S 30%), and
  the whole tube read muted: the hue was spent on the stroke and the halo
  had nothing to be more colourful than. White type with a saturated neon
  halo around it (see the bloom notes below) is what reads as vivid. The light end is hardcopy: paper
  with a faint tint of the ink, ribbon-dark type. The lock is #1E7A3A on
  paper and the dark scheme's primary on the tube. `TuiInks` carries the
  muted/faint inks per seed. It replaced fixed milk+orange / neutral grey+mint,
  which gave an amber pick grey glass and a mint lock. (Catppuccin and gruvbox
  were tried before that and dropped.) `splash_bg_tui` is still the old
  fixed values and does not track the per-accent grounds.
  **The CRT** (`ui/theme/TuiCrt.kt`): `Modifier.crt` draws scanlines (a dark
  band 0.42 of every 3dp; 3.5% alpha on paper, 24% on the tube), a
  phosphor-tinted raster lift in the lit rows (dark end only, always under
  content — on near-black the dark bands alone are invisible) and optionally
  an edge vignette (four linear fades, not a radial gradient, which on a
  tall phone only reaches the corners). 1px-wide tiles, nearest-neighbour
  for the grain's reason. **It is a BACKGROUND only** — never drawn over type
  or icons: `tuiCrtIf()` (toolbar, shared sheet, tab-list sheet,
  `Destination`) and `grainedBackground` both draw it before `drawContent`,
  with the vignette only on the empty canvases (`dotSpacing > 1`).
  Flicker/jitter/sync from cool-retro-term are deliberately left out.
  **Glow is a grainy BLOOM** (`tuiBloomIf()`, API 31+, dark end ×
  `LocalChromeDarkness`), built like crt-lottes-halation: the node's content
  is recorded into a `GraphicsLayer` and drawn through a RenderEffect chain —
  a colour matrix that REPLACES RGB with the phosphor (the accent) and makes
  alpha the pixel's luminance less a low floor (0.12: pale labels, hairlines
  and switch borders glow in the accent; near-black grounds do not), a 2dp
  core blur PLUS a 9dp skirt at 0.45, and `DST_IN` against a noise tile whose
  alpha runs 0.82–1 (`BLOOM_GRAIN`) — blended with `BlendMode.Plus` at 0.7.
  Additive is what keeps it unintrusive: it only adds light where light
  already is, so the ground never hazes over. The API < 31 text-shadow
  fallback uses the accent colour too.
  **Neon layering was tried and is TOO MUCH** — a 1dp white core plus accent
  layers at 3/8/18dp, each alpha-boosted ×3.0/2.2/1.4 after its blur. What
  had actually made the glow read as muted was the PALETTE (tinted inks and
  grounds), not the glow's strength: with near-white inks on near-black the
  plain core + skirt above is vivid enough. Keep the amount; fix colour in
  the ramp. (Since raised for text: `BLOOM_ALPHA_DARK` 1.0, skirt 0.6, light
  bleed 0.45, since brought back down to 0.22 — while the tab preview's outward halo came DOWN to 0.28 dark /
  0.14 light. Type wants more glow than a picture does.)
  **Colour lives on the controls** in the menu sheet, since the chrome is
  white on black: quick tiles get an accent outline (40% off, full on), a
  0.26 accent wash when on and an accent label when on; the address bar's
  row chevron `>` is drawn in the accent (the address bar buttons stay Ink —
  in the accent, copy and reload stood apart from back/forward and the
  address); tile LABELS stay `InkStrong` even when on (accent text on the
  accent wash blew out under the glow); `TuiSwitch` off is
  a 12% accent well with a 60% accent rule, thumb and `O` legend — unlit, not
  grey; on is a solid accent track whose thumb is WHITE at both ends
  (`lerp(onPrimary, InkStrong, darkness)` — `onPrimary` alone is the black
  ground on the tube).
  **No drop shadows under the TUI** on the menu address bar, the + sheet
  search bar or the find bar — a tube lights things, it casts no shadow.
  Those three fields take the quick tiles' outline instead (1dp accent at 40%).
  **Outlines are SOFT and glow**: `tuiSoftOutlineIf` (tiles, the three
  fields, `TuiSwitch`) and `drawSoftRule` (tab previews, the shrinking page)
  stroke through a 0.9dp `BlurMaskFilter` — a line on a tube is slightly out
  of focus. It goes AFTER `tuiBloomIf` in a chain so the rule is recorded and
  glows with the content; before it, it sat over the halo as a colder line.
  **The glow curve is steep** (`BLOOM_GAIN` 2.0, `BLOOM_FLOOR` 0.35, was
  1.6 / 0.12): at the old values a faint grey label (disabled tiles on an
  empty tab) got nearly white type's halo — a bright ring round dim text.
  Disabled tiles also bloom at 0.3. An ON tile's bloom is scaled by the
  accent's luminance (`0.3 / lum`, clamped 0.35–1): the halo is added over
  the accent wash, so yellow/green blew the label out to white while blue
  and red read fine.
  The empty-screen/switcher vignette is 0.07 light / 0.25 dark (was 0.14 / 0.5,
  read as a shadow round a blank screen).
  **A control with its own opaque fill needs its OWN `tuiBloomIf()`**, placed
  after that fill: the surface's halo is drawn under content, so a field or
  tile covers it and its text shows no glow at all. Applied to both address
  fields (menu sheet and + sheet) and the quick tiles.
  **Card bezel, progressive**: previews carry a 0.5dp outline in the ACCENT
  (`primary` at 0.85; 1dp in the text colour competed with the page) and an even fainter outward halo (0.16 dark /
  0.08 light). Both take an `amount: () -> Float` read in DRAW:
  `SwitcherShrink.amount` (a plain holder in `TabSwitcher.kt`, written by a
  `SideEffect`, = `shrinkOf(progress())`) for the cards — the glow sits
  BEFORE the card's `alpha(hideThumbnail)` so it stays while the live page
  stands in — and the drag offset for the card being thrown away (gone by
  half a screen up). The live page and its thumbnail stand-in draw the same
  outline inside their shrink layer (`tuiShrinkOutline` in
  `BrowserScreen.kt`, on the `shrinkGeometry` window, stroke ÷ live scale),
  0 at fullscreen and 1 on landing, so the handoff does not blink it.
  **The loading line glows** via the same `tuiGlowAroundIf`, on the fill
  `Box` AFTER its width `layout` (so the halo hugs what has loaded), at
  `strength = 4f, radius = 6.dp`. `tuiBloomIf` cannot do it: its layer is
  clipped to the node, and a 3dp node has no room for a halo inside itself.
  **The light end has its own halation: INK BLEED.** On a paper-white tube
  the ground is lit and type is the gap, so the inverse is drawn — alpha
  from 1 − luminance, 1dp core + 3.5dp skirt, accent-tinted, `Multiply` at
  0.22 (it can only darken; 0.45 over a 5dp skirt read as a smudge round
  every label). Both variants are masked back to the content's
  coverage (`DST_IN` against `createOffsetEffect(0,0)`, the untouched
  source), or an inverted empty pixel becomes solid colour.
  **The halo is drawn UNDER the sharp content** (`overContent = false`,
  the default). On top it tinted what it came from — accent swatches in
  Settings shifted hue. Underneath, opaque things cover their own halo and
  only the spill shows. The switcher's page previews (both `TabCard` and the
  dragged card) take `tuiScreenIf()` — NO raster (scanlines over a preview read as a damaged thumbnail), no
  vignette and no bloom inside (both read as an inward shadow) — plus
  `tuiGlowAroundIf()` BEFORE their `clip`: an accent halo outside the card
  via `BlurMaskFilter.Blur.OUTER` (12dp; 0.55 dark, 0.28 light), nothing
  drawn inside the bounds. Tab titles (`TabLabelRow`) take `tuiBloomIf()`.
  **Don't try to exclude pictures from the bloom by skipping them during
  the recording.** A `tuiUnshaded()` modifier did exactly that (a flag set
  around `layer.record`, draw skipped while set) and favicons, engine icons
  and accent swatches went INVISIBLE about half the time: a child with its
  own RenderNode (every LazyColumn item is placed with a layer) caches its
  display list, so the draw recorded while skipped is the one the REAL pass
  then reuses. Removed. Drawing the halo under the content is what keeps
  opaque pictures true; only the spill past their edge is tinted.
  **Aperture grille**: `GrilleTiles`, R/G/B columns 0.4dp each, `Multiply` at
  3.5% light / 5% dark, drawn with the scanlines. Light-end scanlines are
  2% (10%, then 4.5%, were both too distracting: a lit ground shows its
  gaps plainly), grille 1.5% light and the
  light vignette 14%.
  **Scanlines are soft and grained**: `ScanTiles` builds 128px tiles per pitch
  with a raised-cosine beam profile (sharpened ^1.6) and 35% per-pixel noise,
  cached per pitch rather than per draw.
  **The accent is `tuiAccent`**, passed to `tintedWith(accentOverride = …)`:
  the pure hue (HSL L 50, S 100) lifted only as far as 4.5:1 against
  `surface0` needs, L 40 downward on paper — the shared `darkAccent`'s L 62
  is a quarter white. `TuiGlyphIcon` draws with a native `Paint` and centres
  each character by its INK bounds (`getTextBounds`), not its line box. Deliberately SUBTLE and everywhere: toolbar, shared
  sheet, tab-list sheet, `Destination`, find bar, `ModalCard`, the empty
  screen's watermark. NOT on the tab switcher's root, whose cards hold page
  thumbnails (a bright page would bloom). The TUI's film grain under canvases
  is gone (`grainedBackground` draws raster only). Goes AFTER `tuiCrtIf()` in the chain or the raster
  lines are recorded and glow too. It covers vector icons as well as type.
  Below API 31 the fallback is `tuiGlow(color)`, a plain text `Shadow` from
  the `Text` wrapper and `TuiGlyphIcon`; from 31 up that returns null.
  **Accents are at full saturation** (`Color.vivid()` on the seed before
  `tintedWith`) — mostly for Auto, whose wallpaper primary is muted; a seed
  at ≤15% saturation stays grey.
  The toolbar's three buttons become the words "tab"/"new"/"etc"; menu rows
  and quick-action tiles drop their icons (and the row drops the gap with
  `leadingGap = false`), the chevron becomes ">". Material's `Switch` takes
  its corners from its own tokens rather than from `MaterialTheme.shapes`, so
  `MenuSwitch` hands over to a hand-drawn square `TuiSwitch` instead. The two
  quiet inks (`inkMuted`/`inkFaint`) are overridden away from
  `outline`/`outlineVariant` — those two are right for hairlines and too
  faint for monospace text at label sizes.
  **The loading line is ONE UNBROKEN RUN whose length steps.** A terminal's
  progress bar is `####    ` written into a text grid, where a gap between
  the marks would be a space character — so unlike Nothing's modules and 98's
  blocks (which are drawn with track showing between them), the TUI's bar has
  no gaps at all and only its WIDTH is quantised, advancing a 9dp cell at a
  time through the same `quantiseToCells`. Three looks, three cell shapes,
  one mechanism.
  **The icons are two halves, and neither is a set drawn for the occasion**
  (`ui/theme/TuiIcons.kt` for the characters, `ui/theme/SharpIcons.kt` for
  the vectors, through the same `ui/theme/SpecialIcon.kt` wrapper
  Nothing and 98 go through). **Half one is the keys**: where the thing an
  icon means is a character a keyboard actually has, the theme draws that
  character in the interface's own monospace face — `<` `>` back/forward,
  `^` `v` the find bar's steps, `x` close, `+` add, `/` search (the search
  key in `less`/`vim`/`man`), `:` the menu, `\` and `/` the two diagonal
  arrows, `=` the drag handle. It IS the key, at the weight of the words
  beside it, which is what a terminal has instead of an icon. The list is
  short on purpose: an earlier pass ran it through the whole interface with
  single letters for the nouns (`b` bookmark, `h` history) and a letter
  standing in for a picture reads as a keyboard legend, not a control — a
  character earns its place only where it is the SYMBOL for the thing rather
  than the initial of its name. **Half two is Material Sharp**: everything
  else is the same glyph the app already draws, in Material's square-cornered
  cut. The theme's grammar is that every radius is zero (`specialCorner`) and
  Sharp is that grammar applied to the icons by the people who drew them —
  nothing vendored, nothing to drift out of step, and a mapped icon beside an
  unmapped one still reads as one set. `TuiGlyphIcon` sizes a character off
  its box (`BoxWithConstraints`, defaulting to Material's 24dp when an axis
  is unbounded) at 0.86 of it and asks for BOLD: an em box is not its ink,
  and one character has a fraction of what a silhouette puts on the page.
  A pixel set was tried between the two (Pixelarticons, borrowed from the 98
  theme, which has since dropped them too, for back/forward/reload/share/globe) and dropped: a bitmap glyph
  belongs to a machine with visible pixels where a TUI is a face on a grid,
  and mapping only some of them left a pixel chevron sitting beside a
  Material padlock.
  **The theme picker's own icons are pinned to Material** (`MaterialIcon` at
  that one call site in `SettingsSheet.kt`, not the theme-aware `Icon`): the
  cells are a row of CHOICES, and a choice has to be drawn like the ones
  beside it or the picker shows the active look instead of the looks on
  offer. Only the TUI could ever reach it — Nothing and 98 turn themselves
  off for Settings' subtree — which is the asymmetry that made it worth
  pinning.
- **Nothing special theme** (`ui/theme/Nothing.kt`): the second whole-look
  override, hanging off `LocalNothing` exactly as the TUI hangs off
  `LocalTui`, and turned off for the same subtrees (Settings, the ad blocker
  pane). Monochrome — warm off-white one way, OLED black the other,
  hand-mapped like Catppuccin rather than seeded, since a tonal scheme from
  the accent would put a cast in all sixteen greys. **The greys are at
  exactly 0% saturation** and that is load-bearing, not incidental: they used
  to be warm (page at hue 45, up to 7% saturation at the outline), which
  spread a beige cast over every surface, hairline and muted ink — by area
  the largest hue on screen, in a look whose premise is that colour is an
  interrupt. Neutralised by LUMINANCE (each value is the grey of the same
  relative luminance as the colour it replaced), so every contrast ratio is
  unchanged and only the chroma is gone — which is why they are not round
  numbers. The accent carries the warmth alone now, which is what makes it
  read as an interrupt: one warm mark on a neutral ground is an event; the
  same mark on a ground already leaning that way was just the warmest thing
  in a warm room. The skill's light ramp is 0% throughout for this reason.
  That is still true of the CANVAS, but no longer of the whole palette: the
  base `Dot` values are 0%, and at theme build time every ELEMENT role is
  carried onto the user's accent hue (see "The ONLY colour difference" below).
  The status green and `error` keep their own hues.
  **The canvas is the ORDINARY theme's canvas.** The greys used to start a
  few points down (paper #F3F3F3) and the dark end was OLED black with its
  raised tones at #0C0C0C/#161616, which read as a DIMMER version of the app
  rather than a different one, and put the sheet four values away from the
  page behind it — both simply black. The ramp is now neutral tone
  98/96/92/90 light, i.e. what `tonalColorScheme` builds for every other
  look. The DARK end is as near black as it can be without being black —
  ground #030303 — and the ramp above it is COMPRESSED: sheets/toolbar
  #0D0D0D, sunk surfaces #151515, `surfaceBright` #1E1E1E, the switcher's own
  ground #050505. Two separate facts hold that shape. The ground is not a
  true zero because a black pixel on OLED is a pixel switched OFF and lags
  coming back on (the smear those panels are known for), and white type on
  zero blooms; a value in the low single digits reads as black and never
  switches the pixel off. It is 3 rather than the 5 usually named because
  the two artefacts of that band behave differently: the smear is
  all-or-nothing (any drive clears it) while the low-drive NON-UNIFORMITY
  the same band exposes — the mura that reads as brighter corners on a dark
  screen in a dark room — scales with the drive, so 3 keeps the pixel lit at
  about half of it. The render is flat to well under an 8-bit level either
  way (measured off a framebuffer capture), so anything visible in that
  field is the panel. And the ramp is short because a step measured against black is
  read as a COLOUR, not as a height — the raised tones were #1A1A1A/#262626/
  #333333 over a #080808 ground, then #141414/#1F1F1F, and at both a sheet
  covering most of the screen read as a grey screen replacing the page
  rather than a surface over it. What separates a sheet from the page
  instead is the SCRIM (true black, `Dot.black` — it takes the page down
  rather than lifting the sheet) and the hairline round it, neither of which
  costs any grey. The hairlines (`wireSoft`/`wire`, #252525/#3A3A3A)
  therefore did NOT come all the way down with the grounds: with the
  surfaces a few values apart, the line is the separation.
  The SWITCHER's ground is the one that has to stay near the floor whatever
  else moves: a dark page's own thumbnail is black or near it, and a ground
  any higher blurs the edge between the canvas and the cards standing on it
  (at #1A1A1A they ran together into one dark field with corners in it).
  **The ordinary dark scheme now lands on the same values**
  (`tonalColorScheme`, dark branch): ground tone 1, `surfaceContainerLow` 5,
  `surfaceContainerHigh` 8, `surfaceVariant` 12 — ~#030303/#0D0D0D/#151515
  before the seed's hue goes in. They are still `tone(seed, …)`, so a picked
  accent tints those greys exactly as before; there is only less light in
  them to tint, and the accent roles themselves are untouched. It ran 10/17/25
  (against Material's 10/13/22, opened up because three tone points is a step
  you can measure and not one you can see); at this ground the argument
  reverses — a step measured against near-black reads as a colour rather than
  a height — so the steps are four points and the scrim plus the hairline
  carry the separation. The same levels are written into the rest of the dark
  surface family (`surfaceDim`/`Bright`, `surfaceContainerLowest`/`Container`/
  `Highest`), which used to fall through to Material's baseline purple-grey
  ramp. **`Auto` (`AccentTheme.Dynamic`) gets them too**, via
  `ColorScheme.darkGrounds()` beside `boldDarkAccents()`: its scheme is the
  whole wallpaper palette rather than a seed, so Material You's own tone
  6/10/12 grounds made the default pick the one that left the canvas ten
  values brighter than every swatch. Each ground is RE-TONED, not replaced —
  `tone()` keeps the hue and saturation Android derived and moves only the
  lightness — so they are still the phone's greys with the light taken out.
  Inks, outlines and `inverseSurface` stay Android's.
  **The splash ground is a RESOURCE and does not move with the palette** —
  `@color/splash_bg_nothing` in `res/values/colors.xml`, read by the system
  before a line of app code runs, with nothing linking the two. Neutralising
  the palette left it behind at the old warm #F4F3F0, i.e. a launch screen in
  a colour the app it opens into no longer used. It tracks `Dot.paper` BY
  HAND; change one, change the other. (values-night's is #030303, tracking the dark
  ground.) Note also that the FALLBACK splash — the one a process
  relaunched into an existing task record gets, see the splash notes below —
  is `Theme.Browser`'s `@color/splash_bg` — plain #FFFFFF / #000000, not the
  ordinary palette's warm near-white — and no app state can reach that path,
  so under this theme some launches still open on a ground that isn't
  Nothing's. It is within a couple of values of it at both ends, and it is
  deliberately a different splash rather than a wrong one; see the splash
  notes below. One warm yellow (#E3A81C,
  #F5C64A on black) is spent on `primary` and nowhere else — in this language
  colour is an interrupt, not a step in the hierarchy — and because it is a
  LIGHT accent, `onPrimary` is the ink at BOTH ends — except a Switch's checked
  thumb, which Material draws in `onPrimary` and which came out as a black
  puck on a bright track, so `MenuSwitch` gives it the SURFACE colour there
  instead. Red survives only as `error`.
  **The ONLY colour difference from the default look is the CANVAS.** Page,
  sheets, toolbar and the switcher/empty ground (`background`, `surface`,
  `surfaceContainerLow`/`Lowest`/`Container`, `surfaceDim`, `emptyBg`) stay
  pure neutral; every ELEMENT on them — fields, tiles, hairlines, inks and so
  icons, the containers, `inkMuted`/`inkFaint`, the empty watermark — takes
  the accent's hue exactly as `tonalColorScheme` tints the default look's
  (`nothingElementsTinted` / `nothingTint`: seed hue, `tonalColorScheme`'s
  saturation scale per role, the grey's OWN lightness, so contrast is
  unchanged; a neutral seed stays grey). `surfaceTint` still points back at
  the surface (Material mixes it into every raised sheet, which would tint
  the canvas). `accentWash` no longer hands back ink under this theme — a
  wash under a tile or chip is an element, not the canvas.
  **The reference is the `nothing-design` skill** (github.com/dominikmartn/
  nothing-design-skill) — its `SKILL.md` plus `references/tokens.md`,
  `components.md`, `platform-mapping.md`. Type scale, iconography rules and
  the dot-matrix motif here are its tables, not invention; where this app
  departs from it, the departure is called out below.
  Three faces, split by SIZE: **Doto** (dot matrix, the OFL stand-in for
  Nothing's proprietary NDot 57, ROND axis at 100 so the dots are bored holes
  rather than square pixels), **Space Grotesk** and **Space Mono**. The last
  two are the skill's own picks and the reason is provenance: they are
  Colophon Foundry's, the same house that drew Nothing's real typefaces, so
  the sans and its monospace share the actual thing's DNA. (They replaced
  Geist, which was a reasonable guess at the same brief and is now deleted
  from `res/font`.)
  **Doto is display-only, 36sp and up** — displayLarge/Medium/Small and
  nothing else. That is the skill's floor and it is the answer to the dot
  face "looking too small" everywhere it appeared: it was setting 14sp list
  titles, which is a size it cannot carry. A dot-matrix glyph is mostly the
  paper between its holes, so it puts about half the ink of a solid face on
  the page at the same point size; above 36sp you read letterforms with a
  texture, below it you read texture with letterforms somewhere in it, and no
  amount of extra size or weight buys the difference. Every heading and title
  below the floor is Space Grotesk. **Doto's tracking is NEGATIVE**
  (-0.02em), reversing the old positive tracking that was there to stop dot
  columns running together — true at 14sp, false at 48sp, where the gap is
  already a whole dot wide and tracking out reads as spaced-out lettering.
  Weights are on the skill's budget, two per family: Doto 600, sans 400 body
  / 500 above it, mono 400. Labels are 11–13sp mono caps tracked 0.06–0.08em
  — the skill's "instrument panel" labels, where the tracking is most of the
  effect. **The mono face IS the label face**,
  which is why `SpecialText` cases text up by asking which family it is about
  to be drawn in rather than by keeping a list of which call sites are labels
  (TUI needs no such test — it cases the whole interface down). Corners are
  CAPPED at 16dp, not squared (`specialCorner`), and percentage corners are
  left alone: a percentage corner is how this app writes "a pill", and pills
  are Nothing's own button shape. The toolbar's new-tab button is drawn as a
  yellow 78x44dp oval with a hand-drawn wide plus (`NothingNewTabGlyph`) —
  wider than the 48dp slot an IconButton gives a glyph and stopping 6dp short
  of the 56dp bar at each end, since the strip of bar left showing above and
  below is what makes it read as a key IN the bar rather than a hole cut out
  of it. The BUTTON is grown to match (`requiredSize` — `size` would silently
  no-op against IconButton's own 40dp), so the touch target and the ripple are
  never smaller than the thing drawn in them. The plus takes its horizontal
  arm off the oval's width and its vertical arm off THAT arm, not off the
  height: scaled from both dimensions it comes out as square as the oval, and
  being wide is the character of the glyph. Quick-action tiles ask for the body
  face by name, since a tile's label is a NAME rather than a status and two
  capitalised words in the tracked mono face neither fit nor want the caps.
  Three further marks of the language, all keyed off `LocalNothing`: the app's
  own flat canvases take a **dot field instead of the film grain**
  (`Modifier.dotField` in `Grain.kt`, which `grainedBackground` — now
  `@Composable` — routes to) — a 13dp-pitch matrix of bored holes at 6.5% ink,
  because these surfaces are printed rather than photographed. Unlike the
  grain it is pitched in dp (a one-pixel grid is invisible at 500dpi) and
  sampled BILINEARLY, the opposite of the grain's nearest-neighbour and for
  the same reason: nearest-neighbour keeps unstructured noise honest under a
  scale, but turns a regular grid into beat patterns crawling across every
  card in the switcher. The tile is rebuilt in `drawWithCache` rather than
  once per process because it is a different bitmap per density. The EMPTY
  canvases — the empty screen and the switcher's own ground, which are one
  ground — take a WIDER grid (`EMPTY_DOT_SPACING`, 1.8x the pitch, same hole
  and same ink, through `grainedBackground(dotSpacing = …)`): everywhere else
  the field is read past something and the grid is what says the surface is
  printed, but on a screen with one kaomoji on it the field IS what is on
  screen, and a whole empty display of it at the ordinary pitch reads as a
  tone rather than as countable holes. Both surfaces take it or a switcher
  emptying out would change texture as the last card leaves it. The
  **loading line is a RULER** (`RulerLoading` in `BottomToolbar.kt`) — 1dp
  ticks at a 4dp pitch, 4dp long, every tenth 8dp, like a caliper's scale: an
  instrument reading out a distance. 8dp of tick on the toolbar's top edge
  ran into the + oval, so it lives on a 12dp BAND of bar ground ABOVE the
  toolbar that rises out of its top edge while a load runs and folds back
  after (reveal = the line's fade, read in draw; the band covers the bar's
  divider and carries the hairline up with it). The band is placed OUTSIDE
  the Surface (which clips) with a `layout` reporting ZERO height — the
  page's bottom inset is read off the toolbar's measured size, so a strip in
  the Column made the bar permanently taller and animating one would resize
  the page per frame. Ticks hang from the band's top line, edge to edge, and
  are laid out FROM THE CENTRE: a major tick at the exact middle and every
  tenth either side, so the scale mirrors whatever the width. Every MAJOR
  tick is the accent (35% alpha until reached, and skipped by the sweep).
  Filled minor ticks are INK
  and only the newest is the ACCENT (the reading head — colour spent on the
  one mark that just moved); the stall sweep lerps lit ticks toward the accent
  rather than white (white vanishes on dark-theme ink). It replaced a row of
  3dp dots at a 6dp pitch; what follows about lighting whole cells one by one
  holds for the ticks (they count `fraction * count`, whole ticks only). The dots
  were there the WHOLE time — the track is the same row drawn
  in the hairline colour, one function for both — so the bar is a matrix at
  either end of the load and only a dot's COLOUR says whether its part has
  happened. They light ONE BY ONE: the fill's width is snapped down to the
  last cell it has completely covered (`quantiseToCells`), so it does not
  move at all while the load crosses a cell and then the whole cell is there
  — a part-lit cell would be the same continuous readout with a comb over it,
  and a clipped circle is not a dot (`LoadingCells.dot` drops it rather than
  drawing an egg). Only the finish takes the raw width, because a bar that
  stops a cell short of the edge on a load that completed is reporting
  something untrue. WebView reports progress in jumps and a smooth ramp is a
  story about numbers that arrived as steps. `StallSweep` is drawn as dots
  here too — the gradient sampled at each dot's centre — since a translucent
  band laid over the matrix is a solid rule crossing it. An earlier pass had
  20dp rounded capsules on a 3–4dp line; the dots are the same mechanism with
  the cell shrunk to the bar's own height. Two capsule shapes were what made
  it read as a dashed rule rather than as the theme's grid. And the **quick-action tiles are outlined modules**, carrying
  state on the outline: a hairline off, the accent itself on (plus the
  accent wash when on).
  **The theme has its own icon set** (`ui/theme/NothingIcons.kt`), swapped in
  by an `Icon` wrapper (`ui/theme/SpecialIcon.kt`) that the ui files import
  in place of Material's — the same mechanism as `SpecialText`'s `Text`, and
  for the same reason: there is no hook between an `Icon` call and what it
  draws, so the alternative is a `LocalNothing` branch at forty call sites.
  Lookup is a `Map<ImageVector, ImageVector>` keyed by the vector itself
  (`ImageVector` has a real `equals`, so this survives whatever Material does
  about caching); anything unmapped falls through to Material.
  Rules, off the skill's §5: monoline on a 24-unit grid, no fill, ROUND caps
  and joins, a 20-unit live area, five or six strokes maximum —
  where Material draws a silhouette this draws the diagram (a bin is a lid
  and a U, settings is two rails and two handles, share is three nodes and
  two lines). Round caps REVERSED the new-tab plus's butt caps: the look is
  machined, but a bar with one butt-capped mark beside round-capped ones is a
  bar with two icon sets in it. **Filled and outlined variants map to the
  SAME glyph** (the skill forbids filled icons), which costs the menu tiles
  their filled/outlined on-off signal — that is now carried entirely by the
  tile's accent outline and the glyph's tint, so the outlined-tile treatment
  is load-bearing rather than decorative. The set is deliberately partial and
  covers whole SURFACES (toolbar, menu sheet, find bar, switcher, link
  overlay, list screens) rather than a count of icons: a row mixing two icon
  languages is worse than a row in either. Settings and the ad blocker pane
  turn the theme off for their subtrees, so they keep Material's set. A dot
  is drawn as a 0.01-long round-capped stroke, NOT a tiny circle — a stroked
  circle at that radius comes out as a ring. Glyphs were checked by
  transpiling the Kotlin paths to an SVG contact sheet and rasterising it;
  do that again rather than shipping hand-written path data unseen.
  **`STROKE` is 2.25, not the skill's 1.5**, and the reason is the company
  the glyphs keep: they share rows with Material's own icons wherever the set
  falls through, and a Material glyph is a SILHOUETTE whose ink is two or
  three units wide everywhere a monoline one is one and a half. A wireframe
  set has to be drawn above its nominal weight to hold the same colour on the
  page as the solid one it replaces. A few interiors were opened up to suit
  (the eye's pupil, the share graph's nodes, the sun in the picture frame,
  and the open corner of `OpenInNew`, whose two ends were within a unit of
  touching at this weight).
  **`BarButton`'s per-call-site `iconSize` is ignored under this theme.**
  Those numbers correct for how much of its 24-unit box each MATERIAL glyph
  happens to fill (copy 22 units, a chevron 12) — applying them to a set
  already drawn to one live area is a second correction on something square,
  and it is what made reload tower over copy in the menu's address bar. One
  size, 24.dp, for the whole bar.
  **Known departures from the skill**: its accent is red `#D71921`, this
  theme's is the warm yellow above (a deliberate earlier call — the skill's
  "one accent, an interrupt" logic is intact, only the hue differs, and the
  skill's red would collide with `error`). It also bans shadows, blur and
  spring easing, none of which this app's Nothing subtree currently honours
  — the private-mode blur and the shared Material elevation are app-wide
  mechanisms, not theme-local ones.
- **98 special theme** (`ui/theme/Ninety8.kt`, `ui/theme/Ninety8Icons.kt`):
  the third whole-look override, hanging off `LocalNinety8` exactly as the
  other two hang off their locals. Windows 98's own system colours, hand-
  mapped like Catppuccin and Nothing rather than seeded — these are sixteen
  fixed values an operating system was drawn from, and a tonal scheme from
  the navy would replace all sixteen with shades of navy. `#C0C0C0` face on
  every surface, `#FFFFFF` window white on `surfaceContainerHigh` (so a
  FIELD is a hole in the face with paper behind it, which is what its sunken
  bevel then says), navy `#000080` on `primary` and nowhere else,
  `surfaceTint` pointed back at the surface for Nothing's reason. The
  DESKTOP teal `#008080` is the one colour Material has no role for, so
  `BrowserTheme` special-cases `emptyBg`/`emptyGlyph` for it — the switcher's
  ground and the empty screen's, which is exact: a card is a window and a
  window stands on a desktop. Windows 98 has no dark end, so the dark one is
  derived rather than copied (graphite face, the desktop with the light
  taken out, the caption GRADIENT's brighter blue since navy on graphite is
  not a selection colour); the bevel is what makes that derivable at all —
  a lit edge and a cast edge still read as lit and cast on a dark face.
  **Depth is drawn, not cast.** `Modifier.bevel98` is the whole language: two
  bands per edge, white and near-black outside, `#DFDFDF` and `#808080`
  inside, swapped for a sunken control — one function for both, because they
  are one effect and its negative. Drawn with `drawWithContent` AFTER the
  content (a fill or a page preview painted to the same bounds would cover a
  border under it), butted rather than mitred at the corners (that asymmetry
  is what a bevel drawn as four rects looks like, and it is part of the
  look), and a band is 1.5dp rather than a hairline — the original is one
  physical pixel because in 1998 a pixel was visible, and at 400dpi one is
  not. Call sites use `bevel98If(style)`, which is a no-op in every other
  theme, for the reason `specialCorner` owns the corner branch. Applied at:
  the toolbar (a thin raised band along its top, REPLACING the hairline
  divider — a Windows toolbar is raised off what is under it, not ruled off
  from it) and its three buttons, the sheet surface, both omniboxes and the
  list screens' search field (sunken, and their Material drop shadows are
  dropped rather than squared — a control cannot be sunk into a surface and
  floating above it at once), the find bar (raised panel, and the one place
  that keeps a shadow: it floats over a live page, so it gets the period's
  own `hardShadow98`, a solid black rect offset 4dp with no blur), and the
  switcher's cards. `bevel98If` takes an `inset`, which is how the toolbar's
  three buttons are DRAWN 5dp inside their 48dp targets — a 48dp button in a
  56dp bar leaves 4dp of bar above it, and its lit top edge then reads as one
  thick line with the bar's own; the target never moves.
  The menu's quick tiles carry their on/off state on the
  bevel's DIRECTION — raised when off, pressed IN when on — which is what a
  Windows toolbar toggle does and the only state signal that costs no
  colour. `MenuSwitch` hands over to `Ninety8Switch`, a CHECKBOX: there was
  no switch to redraw, a sliding control in that system is a scrollbar, and
  a scrollbar means position. The loading bar is a real progress
  CONTROL, not a rule on the toolbar: a 10dp sunken well in the face colour
  with 6dp navy blocks and 2dp of well between them stepping across it, the
  blocks inset by the bevel's two bands so they never cross its edge. Same
  `segmentedFill`/`quantiseToCells` as Nothing's modules and whole blocks for
  the same reason (progress arrives in jumps), but squared, chunkier and
  bordered — in that system a control is a thing with an edge. `grainedBackground` gives 98 a FLAT fill: its
  surfaces are neither photographed nor printed but filled with one indexed
  colour, and a texture over them would be dither.
  **Type is Arimo** (`res/font/arimo.ttf`, SIL OFL, google/fonts), the
  stand-in for MS Sans Serif. MS Sans Serif is a neo-grotesque — Helvetica's
  skeleton hand-fitted to a pixel grid — and when Microsoft needed it as
  outlines they drew Microsoft Sans Serif, metrically compatible with Arial.
  So the stand-in is an Arial-metric grotesque, which is what Arimo is (it is
  Liberation Sans at Arial's widths).
  **Three pixel faces were tried first and the whole idea was wrong**:
  Pixelify Sans, then Jersey 10, then Tiny5, all on the reasoning that a
  bitmap face wants a stand-in made of visible pixels. The pixels were the
  CONSTRAINT MS Sans Serif was drawn under (640x480, where a pixel was a
  visible thing), not the point of it; at 400dpi a face built out of
  deliberate stairs is not the typeface rendered faithfully but a different
  typeface quoting the hardware, and it reads as an arcade cabinet — the one
  thing this interface was trying not to be. The pixels the theme KEEPS are
  the ones that carried meaning: the bevels, and the 16px icon set. Don't
  re-try a pixel face here.
  Arimo also fixes what the last swap was for: 3010 glyphs including the
  full Cyrillic block, where Jersey 10 had 332 and no Cyrillic at all, so a
  Russian bookmark title, tab label or heading came up as tofu (every Jersey
  cut is Latin-only, so there was nothing to swap within the family).
  ONE WEIGHT, and every role in the type scale asks for `Normal` — now by
  choice rather than by constraint: Windows 98 set its menus, rows, labels
  and tooltips in one weight of one face, and what a heading has instead of
  boldness is size and the bevel around what it heads.
  **`PIXEL_SIZE_PARITY` is GONE, deliberately.** It multiplied Material's
  ladder to buy back the optical size a pixel face lost (1.21 under Jersey
  10, 1.04 under Tiny5, both measured as Roboto's x-height over the face's).
  Arimo's x-height is 52.8 per 100 against Roboto's 52 and it sets
  "Bookmarks" in 500 units against Roboto's 499 — same optical size, same
  width, factor 1.0 — so the mechanism went rather than staying on as a
  multiplication by one. That parity is also what keeps the face safe in
  layouts measured around ordinary type, the menu's quick-tile labels (the
  tightest text in the app) included. Tracking is Material's again too — it
  was forced to zero only because a pixel face's sidebearings are whole
  pixels of its own grid. No case change (`SpecialText` gets `AsIs` —
  Windows wrote its menus in sentence case).
  **The icon set is Open Iconic** (useiconic.com/open, Iconic/Waybury, MIT +
  SIL OFL), vendored into `ui/theme/Ninety8Icons.kt` as the `d` strings it
  ships, parsed once by `PathParser` into `ImageVector`s on an 8x8 viewport.
  Squared corners are NOT what makes a glyph look like 1998 — that is why
  Material Sharp (which this used briefly, and which the TUI still uses via
  `SharpIconOverrides`) reads modern-with-square-corners. What does it is the
  DRAWING: a period toolbar icon was cut on a 16-pixel grid, so its strokes
  are thick and even, its terminals blunt, its metaphors literal objects — a
  filing folder, a globe with continents, a monitor on a stand, a knob with a
  pointer. Open Iconic is drawn on an EIGHT-unit grid for use from 8px up,
  which lands in the same place by the same route, and it is a VECTOR set, so
  the pixels are gone and only the proportions they forced are kept — the
  same trade the face makes.
  **Coverage is complete on purpose**: every key in the table has a glyph and
  nothing falls through to Material, because a row mixing an eight-unit glyph
  with a Material one is a row with two icon sets in it. Where Open Iconic
  has no equivalent the nearest OBJECT stands in, not the nearest
  abstraction: a cookie banner is a dialog (`comment-square`), the keyboard
  setting is the machine that has one (`laptop`), a tap is `target`, tuning
  is a `dial`. The period drew things that way too — it had no vocabulary of
  gestures and pictured the hardware instead. Vendored as `d` strings rather
  than hand-written builders because these are someone else's drawings and
  the point is that they arrive unedited; a `d` string is not reviewable by
  eye, so the check is the Nothing set's — rasterise the lot to a contact
  sheet and look at it.
  **Two pixel sets preceded this and both are dead ends**: Pixelarticons
  (1154 lines of vendored ASCII art, deleted with the pixel face — 16x16
  pixel art and a bitmap font were ONE decision, and keeping the icons
  pixelated under an outline grotesque left a period toolbar under interface
  text from another machine), and before that two hand-drawn attempts
  (strokes on a 16-unit grid antialias into a modern outline set drawn small;
  hand-placed pixels fixed that and were still thirty-two separate guesses
  about what a bin looks like). Don't revive any of them.
  `BarButton`'s per-call-site `iconSize` is ignored under this theme as it is
  under Nothing's, at 24dp: the set is drawn to one live area already, and
  those numbers correct for how much of its box each MATERIAL glyph fills.
  **Splash**: ground `@color/splash_bg_98` (the desktop — before there is a
  window there is a desktop, which is the state a splash is in), ink window
  white; both track `Silver.desktop`/`Silver.hilight` BY HAND, same wire and
  same hazard as Nothing's, and the fallback-splash caveat above applies
  here too.

- **Aero special theme** (`ui/theme/Aero.kt`, `ui/theme/AeroIcons.kt`): the
  fourth whole-look override, hanging off `LocalAero` exactly as the other
  three hang off their locals, and turned off for the same subtrees
  (Settings, the ad blocker pane). Frutiger Aero by way of Windows 7, Vista
  and Aqua.
  **Its surfaces are TRANSLUCENT, which is what makes it different in KIND
  from the other three** rather than in palette: they recolour opaque
  chrome, this one lets the page through it. That is free here rather than
  invented — the toolbar is already drawn over the live WebView's bottom
  strip and the sheets are already drawn over the page, so every surface
  this theme frosts has a real backdrop. The alphas are part of the palette
  and there is no tonal generator that produces one, which is the reason
  this scheme is hand-mapped on top of the reasons the other three are.
  `background` stays OPAQUE and must: it is what a tab paints before the
  renderer has a frame, and a translucent one shows the window's black
  behind a page that is merely still loading.
  **The glass is TINTED, and that is the whole design.** Windows 7 shipped a
  colour picker for its chrome and called it colorization; colour IN the
  material is the one thing separating this from the near-clear glass
  interfaces have gone back to. Clear glass fails in the way that matters —
  legibility of the type on it becomes a property of somebody else's web
  page — so the material carries its own icy blue plus the accent at
  `AERO_TINT` (0.10), and the surfaces sit at 0.86–0.94 rather than the
  0.4–0.6 a fully clear look would take. Hence also that the accent is the
  colorization SLIDER: the pick goes through `tintedWith(singleVoice =
  false)` like TUI's and Nothing's, and picking Pink and picking Teal are
  visibly two different browsers. The material follows too:
  `aeroRehued(seed)` moves every blue-family role (surfaces, fields, rules,
  inks, page) onto the seed's hue before `tintedWith`, keeping lightness and
  alpha; a pale seed thins saturation, so Graphite is grey glass. The tab LIST
  switcher is a floating bubble under Aero (`AERO_BUBBLE_GAP`, no overhang,
  four round corners) — its overhang used to show through the glass toolbar.
  It is composed OUTSIDE the Scaffold under Aero (`aeroListOverlay` in
  `BrowserScreen`, with `bottomInset` = the toolbar) so its scrim reaches the
  navigation bar, and so `aeroPageGlass` can frost the page under it via
  `ListPaneBounds` without frosting the list itself. Its rows are translucent
  droplets (`aeroDroplet(glare = true)`). The splash ground is per accent
  (`splash_bg_aero_<accent>`, `aeroAccentSky` values baked by hand; Dynamic
  uses `system_accent1_50`/`_900`). **Nested pops**: the omnibox bars take
  `aeroPopIf(yieldToChildren = true)` and their buttons `aeroPopIf(claim =
  true)` — the child records the pointer in `AeroPopClaim` during Initial,
  the bar checks it on the Final pass, so a button tap pops only the button.
  The list sheet slides inside a clip ending at the toolbar's top, so it
  rises from under the bar. `ListPaneBounds.rect` is SNAPSHOT state: as a
  plain var the page layer read no state on its first run and never re-ran,
  so the blur silently never appeared. The find bar is a third shader
  region (`findInRoot`) with the sheets' translucent fill. The full-screen
  grab (`thumbnailFull`) is persisted as `<id>.full.webp` beside the preview
  and restored with it; `prune` keys on `substringBefore('.')` for that.
  The Aero loading tube tapers over `LOAD_AERO_TAPER` of the rim at each end.
  **Depth is REFRACTED, not cast** — the counterpart to 98's drawn bevel,
  and the whole of `Modifier.aeroGlass`. Three marks, one light source
  directly above: a **specular gloss** over the top 46% ending in a HARD
  terminator (the signature of the era — a gloss that fades is a gradient, a
  gloss that stops is a reflection); a **lens band** inside the rim,
  brighter at top than bottom, which is the thickness of the glass seen
  edge-on; and a **bounce** along the inside of the bottom edge, light that
  went through and came back off what is under it. A sunken `Glassy.Field`
  is the same three rearranged (cavity shadow at the TOP, bounce at the
  bottom), one function for both, exactly as `bevel98` draws a control and
  its negative.
  **The refraction is DRAWN, and it has to be.** The backdrop at these
  surfaces is a WebView, and Chromium's output is not readable by a Compose
  draw pass at any price (see `View.draw(Canvas)` above) — so there is no
  real backdrop blur to be had, and `Modifier.blur` is a no-op below API 31
  besides. The rim is what a real blur would have produced at the one place
  a viewer reads thickness off; the translucency does the rest honestly.
  **The fill is not drawn by the modifier.** Every call site already has its
  own `background(...)`/`Surface(color = ...)` in the palette's translucent
  surface colour, and a draw modifier runs in CHAIN ORDER — so the marks
  land on the glass and still under the node's content, which is why the
  gloss can be this strong without washing out a row of text. The rim alone
  is drawn after the content (`drawWithContent`), for the reason `bevel98`
  draws its bands there.
  Call sites go through `aeroGlassIf(corner, style)`, a no-op in every other
  theme, exactly as `bevel98If` owns the bevel branch. **The corner is passed
  in** because a draw modifier cannot ask its node what shape it was clipped
  to, and a square rim on a round surface is the one error here visible from
  across the room.
  **Corners GAIN rather than being capped or squared** (`specialCorner`,
  gain 1.35 with a 10dp floor): Aqua's control is a lozenge and Aero's is a
  blown wet object, and a 4dp radius reads as cut sheet. The floor matters
  more than the gain — it is what lifts the app's small radii into the range
  where a highlight can run round a corner instead of stopping at it.
  `SpecialCornerScale` carries the gain but cannot carry the floor, which
  costs nothing: every draw-phase radius is a card's (16dp+), well clear of
  it.
  The **switcher's ground is the SKY** (`AeroSkyLight`), the same call 98
  makes for its desktop and for the same reason — a pane of glass has to be
  held up against something, and the ordinary answer (a shade off the
  toolbar) is glass resting on glass. `grainedBackground` gives this theme a
  plain vertical **sky gradient** (`aeroSky`) instead of the grain: its
  canvases are a view of something with depth, not a surface, and it is the
  cheapest of the three textures — one rect, no shader, no per-density
  bitmap.
  The **loading bar POURS and then glazes**, which is the one place this
  theme agrees with the ordinary look where the other three break the run
  up. That is not a lapse: Vista and 7 drew a continuous glossy lozenge in a
  sunken well precisely because the material was liquid — a SEGMENTED bar is
  XP's, one generation earlier. It is also the only look where cells would
  cost something real, since chopping a specular run into blocks leaves a
  row of little highlights rather than one reflection. 6dp tall, because at
  2dp the gloss and its terminator land inside one pixel of each other.
  **Type is Open Sans** (`res/font/open_sans.ttf`, SIL OFL, google/fonts),
  standing in for Segoe UI, and the provenance is the point exactly as
  Arimo's is for MS Sans Serif: **Steve Matteson drew both Segoe UI and Open
  Sans**, the second an open commission a few years after the first, off the
  same humanist skeleton. Not a lookalike picked by eye — the same
  designer's other cut of the same idea. (Frutiger itself is Linotype's and
  equally not ours; its open descendants would have been a second-hand route
  somewhere Open Sans reaches directly.) Two weights, 400/600 — not 700: a
  bold heading on glass reads as a heavier pane rather than a louder line.
  No size correction, and none needed: x-height 54.5 per 100 against
  Roboto's 52. Sentence case (`SpecialText` gets `AsIs`) — Windows 7 and OS
  X both wrote that way, and what carries a label here is the lozenge and
  the gloss around it.
  **Icons are Material's own ROUNDED cut** (`AeroIconOverrides`), the far
  end of the range the TUI takes Sharp from, and right for the same reason:
  it is this theme's corner rule applied to the icons by the people who drew
  them. The table is deliberately PARTIAL and that is safe here where it
  would not be for Nothing or 98 — an unmapped glyph falls through to the
  same family one cut away, not to a different draughtsman. **No hand-drawn
  set was attempted**: the icon of this era was a 128px full-colour glossy
  object in perspective, and there is no monoline approximation of one; the
  gloss lives on the chrome where it can be drawn honestly. Unlike Nothing
  and 98, `BarButton`'s per-call-site `iconSize` is KEPT — those numbers
  correct for how much of its box each Material glyph fills, and a Rounded
  glyph fills exactly what its Filled twin does. Same drawings.
  **Splash**: ground `@color/splash_bg_aero` (the sky, tracking `Glass.sky`
  / `Glass.skyDark` BY HAND — same wire and same hazard as Nothing's and
  98's), ink the ACCENT's, so it takes the (look, accent) cross product of
  order arrays like TUI and Nothing and unlike 98. The fallback-splash
  caveat applies here too.

- **Translucent sheets** (Default and Nothing only, API 33; `LocalFrosted`,
  `LocalFrostOpacity`, `ui/theme/AeroPageGlass.kt`): the page is blurred
  under the sheet / toolbar / find bar by `frostedSheetGlass`, and the fill
  on top is translucent. Same rules in both looks. The slider (0 clear .. 1
  solid) has FIVE fixed steps (0/.25/.5/.75/1; `OpacityRow` snaps, and an old
  continuous value is shown on its nearest step) and moves EVERYTHING, not
  just the canvas: `frostCanvasAlpha` 2%→98.5%
  (eased ^1.3), `frostElementAlpha` 10%→100% for fields/rows/off tiles
  (always above the canvas),
  `frostBlurDp` 10→56dp, and `FrostMaterial.over` — saturation lift
  1.55→1.15, luminosity pull toward the sheet's ground 0.35→0.95, rim ×1.5→0.7
  (at the clear end the page keeps its own light and the rim says "glass").
  Grounds of controls on a frosted surface go through `frostedIf(...)` or they
  are opaque slabs (the shared `Picker`'s unselected segments and
  `frostedSliderColors()`'s inactive track). **STATE colours never do**: switch
  tracks (both), the picked segment, a slider's active track and an ON quick
  tile (wash composited over a solid `FieldBg`) stay solid — thinned over
  frost they washed out to the page's colour. There is deliberately no
  accent alpha any more. **Nothing's loading ruler band** sits
  ABOVE the toolbar, outside the blurred region, so it publishes its reveal
  through `RulerBand` (snapshot state holding a lambda) and the toolbar's
  frost rect grows up by `RulerBand.height * reveal`. **Nothing's new-tab
  oval is always opaque.**
  **The toolbar does NOT slide away under a + / menu sheet** (it did, over
  `SHEET_BAR_OUT_MS`, and was seen going through a translucent sheet): it
  stays laid out and in place, and only the part NOT under the sheet is drawn:
  its layer is clipped at the sheet's top edge every frame (`size.height -
  sheetHeightAnim - translationY`, open upward for the ruler band), and at
  `sheetHeightAnim >= bar height` it is alpha 0 with its frost region
  dropped. `sheetBarSlide` is for full-screen destinations
  only. **Tab list frost**: `ListPaneBounds.rect` is carried `SHEET_CORNER`
  on under the toolbar line (the frost shader rounds all four corners; the
  sheet itself is square there), and the list's clip corner is capped at
  `NOTHING_MAX_CORNER` under Nothing to match `frostSheetCorner`.
- **Default-look grounds carry the swatch visibly** (`GROUND_SAT_LIGHT` 0.4 /
  `GROUND_SAT_DARK` 0.3 in `tonalColorScheme`, every surface role): at the
  old 0.12–0.2 a fixed swatch left the canvas (Settings most visibly) plain
  white where Auto's wallpaper neutrals are tinted. Nothing's element tints
  use the same container scale (0.35).
- **Accents tint the TUI, Nothing and Aero looks; 98 they replace.** The fixed
  swatches (`AccentTheme.color()`) are Material's A-series, at or near full
  saturation — the old 600 ramp was the muted set a component library picks
  to sit under someone else's brand, and only hue+saturation survive
  `tonalColorScheme` anyway, so a bolder seed is the only thing that changes
  how much colour reaches the screen. Under TUI/Nothing/Aero the pick does
  NOT seed a scheme: `ColorScheme.tintedWith` writes the accent roles only
  (primary/onPrimary/inversePrimary, plus secondary+tertiary for the TUI,
  whose palette has one voice), at tone 42 light / `darkAccent` dark, with
  `onPrimary` whichever of the theme's OWN ground and ink measures better —
  seeding would regenerate the sixteen greys from the accent's hue, which is
  what both palettes spend their whole colour budget avoiding. Every swatch
  reaches them, Graphite included: a grey accent is a terminal with no
  colour and is Nothing's own premise, not an abstention. Dynamic hands over
  the wallpaper's `primary` per end; below API 31 it falls through to the
  fixed pre-S seed like the ordinary look does. **98 is untinted** — its
  sixteen values ARE the look — so an accent tapped while it is on drops the
  special theme to `Default` instead (`setAccentTheme`), which is the only
  place that reset survives.
- **The dark accent is NOT Material's tone 80** (`darkAccent` in
  `ui/theme/Theme.kt`). Material puts a dark `primary` there because its own
  space is chroma-limited; `tone()` here is plain HSL, where lightness 80 is
  80% of the way to white whatever the saturation says — so the same table
  turned every accent pastel at night, and a red picked in daylight came up
  pink. The dark accent sits at tone 62 with a saturation FLOOR of 0.62
  (skipped when the seed carries no hue, so Graphite and a grey wallpaper
  stay grey), lifted two tones at a time only until it clears 4.6:1 against
  `DarkestGround` — blue and violet need one notch, nothing else does, and a
  flat higher tone for everyone would cost the rest their boldness. What
  sits ON it is measured, not tabulated (`accentInk`). The WALLPAPER's dark
  scheme gets the same correction (`boldDarkAccents`, primary/tertiary
  only): a system colour going pale at night is the same complaint whatever
  its source, and every surface, ink and outline stays Android's.
- **The default accent is `Dynamic`**, not Graphite — set in both
  `BrowserStore.Settings` (field default AND the parse fallback) and
  `BrowserViewModel._accentTheme`'s seed, which have to agree or the frames
  before the store is read are a different theme from the ones after.
- **The menu sheet's rest height is MEASURED, not chosen.** Every other sheet
  rests at `SHEET_REST_FRACTION` (2/3); the menu rests there too unless its
  contents do not fit, in which case it rests at exactly the height they need
  — `menuContentHeightPx + menuChromePx`, coerced into
  `restFloorPx..sheetExpandedHeightPx`. The content height comes from
  `MenuSheet`'s `onContentHeight`, reported from a `Modifier.layout` placed
  INSIDE `verticalScroll` (which measures its child unbounded, so that
  placeable's height is the contents' own, not the box's). The chrome is the
  24dp drag handle plus the navigation-bar inset; `SHEET_CORNER` is overhang
  the Column pads back, so it costs nothing. It replaced a hand-picked
  `MENU_SHEET_REST_FRACTION = 0.74f`, which was a guess in both directions:
  ~39dp of dead sheet under the last row on a 411x911dp screen (a sheet made
  taller to show nothing — the thing the fraction was raised to avoid), and a
  return to scrolling on any screen whose rows came out taller. The
  `verticalScroll` stays as the safety net.
  Read off `sheet ?: lastSheet`, exactly as `sheetExpandable` is, so the
  height does not jump back mid-close when `sheet` has already gone null.
  **App settings is an ordinary row**: the 16dp gap and `HorizontalDivider`
  that used to set it apart (it is the only row leaving the browser for the
  app around it) are gone — the rhythm of a list is broken by any gap in it,
  and the distinction was not one anyone was looking for.
  Two things were tried and reverted, both worth not re-trying: shortening
  the quick tiles to `aspectRatio(1.25f)` bought 18dp but pulled the tile row
  up under the address bar, and the gap between those two is what separates
  "where you are" from "what you can do to it" — the tiles are SQUARE. And
  dropping the sheet's own 12dp bottom padding left the last row flush.
- **Sideways, a sheet is HALF the screen wide** (`sheetWidth()` in
  `ui/Modifiers.kt`, applied to all three: `BrowserScreen`'s shared surface —
  + / menu / site settings / reader settings — and `TabListSwitcher`'s own).
  A bottom sheet is sized to the reach of a thumb on the edge it is pinned to,
  and a phone turned sideways has the same thumb and twice the width, so full
  bleed there is a strip of rows with a screen of empty surface beside each
  label. Floored at 360dp (itself capped at the screen) so half a small
  landscape screen never wraps a row. The parents are already `BottomCenter`,
  so nothing is offset to re-centre it, and the two bottom corners are still
  off the bottom edge, so narrowing only exposes the top two — which is what
  the shape is for. Hence also `cornerFlattens` in `TabListSwitcher`: the
  radius fills in as the sheet meets the top of the SCREEN, which only happens
  when it is full-bleed; sideways there is page either side at every height and
  the corners stay round.
- **The tabs button opens the + sheet when there are NO tabs.** Deliberate,
  and older than any of this: `openSwitcher()` refuses an empty space (no
  cards, no page to shrink into them), so the button used to press, buzz and
  do nothing. The empty screen has exactly one thing to offer and closing the
  last tab already opens it. With tabs open the button opens the switcher as
  always. See `onTabs` in `BrowserScreen`.
- **Zoom** is `textZoom` (WebView has no page-zoom API; `setInitialScale` is
  undone by the first pinch or any viewport meta), one global setting, ladder
  50…200 with widening gaps. The readout IS the reset button.
- **Downloads are confirmed, then flown, then followed.** `setDownloadListener`
  raises the request (held on the ViewModel, so rotation keeps the question);
  `blob:` skips the card (it fails whatever the answer). It is the one
  `ModalCard` whose SCRIM TAP declines (`onScrimTap`) — nothing in the
  renderer waits on it — and the one that animates OUT (`leaving`/`onLeft`):
  the answer is consumed at once and `leavingDownload` keeps the card composed
  for its fade. Its header is `DownloadBadge`; on a yes the badge's window
  centre goes to `BrowserScreen` via `onDownloadConfirmed`, the card hides its
  badge, and `DownloadFlightOverlay` (`ui/DownloadFlight.kt`, composed AFTER
  `WebPlatform` so it draws over the fading card) flies the same badge from
  there: a quadratic Bézier walked LINEARLY (projectile motion) into the menu
  button, through LIVE `LayoutCoordinates` read in `graphicsLayer`
  (`PlacedNode`) so it lands on a bar still sliding in. **Read the animated
  value FIRST in that layer block**: on its first run the coordinates don't
  exist yet, and an early return before reading `t` left a block that read no
  state, never re-ran, and held the badge at alpha 0 — the flight was
  invisible. The overlay (no toolbar) gets the old toast instead. The
  Downloads screen lists `activeDownloads` above the files (bar, bytes, cancel
  = `DownloadManager.remove`, which deletes the partial file), collected
  inside the `Destination` lambda so polls don't recompose `BrowserScreen`. Progress: DownloadManager broadcasts
  only the END, so `trackDownload` polls `DownloadProgress` every 250ms while
  anything is tracked, and `init` re-tracks in-flight rows after a process
  death (the provider scopes queries to our own rows). `fraction` is null
  when any total is unknown (the ring spins instead); a success holds a full
  ring 500ms. The fraction reaches `BottomToolbar` as a LAMBDA read in draw /
  `snapshotFlow` — `BrowserScreen` composes only the `downloading` boolean.
  TUI shows the percentage as the button's word.
- **Clear browsing data** clears cookies, cache, `WebStorage` and history —
  four switches, one button. Deliberately NOT swept: open tabs and their back
  stacks, and the password vault (which has its own door).
- **Visit tallies** (`core/VisitTally.kt`) live in their own table keyed by
  `visitKey(url)` (fragment and trailing slash dropped, scheme+host lowercased,
  **query kept**). **A LOAD is not a visit.** Counting hangs off
  `doUpdateVisitedHistory` (`noteNavigation`), not `onPageFinished`, which
  fired for reloads, back/forward, restores and failed pages alike: a reload
  is skipped by its flag, a back/forward step by comparing the back/forward
  list with the view's last snapshot (same size, landed on an entry that was
  there), a replaced entry retargets the pending visit, and a view's first
  commit counts only for `freshTabs` (a new tab on an address, a popup) —
  anything else is a tab coming back. Search engines' result pages never
  count (`SearchEngine.isResultsPage`). A visit then only PENDS until its page
  has been on screen `VISIT_DWELL_MS` (`syncVisitClock`: current tab, page
  showing, Activity resumed — time accumulates across interruptions), and a
  repeat within 30 min is the same visit. Ranking is a decayed score (14-day
  half-life) and "Most visited" admits only pages seen on `MIN_VISIT_DAYS`
  separate days of the last 30 (a day bitmask per tally) — shorter than 20,
  or empty (the sheet then shows Recently visited), rather than padded.
  Pruned by lowest CURRENT score: a lifetime count locks new pages out once
  the table is full, and recency drops the daily sites the list is for.
  A typed query shows at most 3 matches from `omniboxHistory` (every tally
  plus uncounted recent history), prefix matches first, with the engine's
  suggestions under them.
  **The sheet's "Recently visited" is `recentlyVisited`, NOT `history`**: the
  tallies ordered by `lastVisitAt` (which a repeat inside 30 min still
  refreshes, unlike `scoredAt`), so it holds only visits that passed the rules
  above. `history` stays the raw every-page-that-loaded record, and the
  History screen still reads it.
- **Launcher shortcuts**: the intent must be EXPLICIT, and there is no way to
  write a variable package name into a resource — `${applicationId}` is
  substituted into AndroidManifest.xml and nothing else, and a string RESOURCE
  isn't resolved either (`ShortcutParser` reads raw text). So
  `app/src/main/shortcuts-template.xml` lives OUTSIDE `res/` and
  `GenerateShortcutsTask` writes it per variant. Check with `dumpsys shortcut`.
- **Splash & icons — there are TWO splashes**, because there are two kinds of
  launch and only one of them can be chosen. Both draw baked PNGs (a
  VectorDrawable can't render text; these kaomoji span many scripts, so they're
  rendered on macOS via CoreText by `tools/render_splash_cats.swift`). The
  splash scales the icon into a SQUARE slot masked to a circle two thirds
  across, so frames must be square, share one point size, and be centred by
  their INK (a line box carries full ascent/descent).
  **The COLD-START splash** is the app's: twenty cats cycling at 280ms in the
  accent's own ink on the app's own ground. Colour comes from a theme attribute
  (`?attr/splashInk`) because no app state is readable at splash time; the
  accent is baked into WHICH theme via
  `getSplashScreen().setSplashScreenTheme(id)`, which registers for the NEXT
  launch. **A special theme moves the GROUND only, never the ink**: TUI and
  Nothing are TINTED by the accent (`tintedWith`), so the cat is the swatch's
  colour there and only the paper under it is the look's — which is why the
  order arrays are a (look, accent) cross product
  (`splash_orders_tui_blue`, …) and why there is no `splash_ink_tui` /
  `splash_ink_nothing` any more. 98 is the exception at both ends — it
  REPLACES the accent, so it keeps one splash, ground and ink both.
  **The frame ORDER rides the same wire**, for the same reason — a drawable
  can't be picked at splash time either. So the twenty faces exist as twenty
  shuffled `<animation-list>`s (order *k* opens on face *k*, so the first
  frame, which is all a fast launch shows, is a different cat each time)
  crossed with the twelve inks into 240 themes; `MainActivity` draws one at
  random out of `R.array.splash_orders_*`.
  **The FALLBACK splash is a different splash on purpose**, not a degraded
  copy: one still `ฅ^•ﻌ•^ฅ` (`splash_cat_fallback`, the twenty-first face,
  fitted on its own and named by no order) in the app's muted grey on plain
  white or black. It is a one-frame `<animation-list>` rather than a
  `<bitmap>` only for tidiness against the frames beside it — neither form
  draws at all without the behaviour flag below. **That registration is only read when the ActivityRecord is
  CREATED** — the platform resolves it in `showStartingWindow` only on a real
  `startActivity`. A process killed with its task still in recents (memory
  pressure, app standby, hibernation) is relaunched INTO the existing record,
  and that launch's splash comes from the manifest's `Theme.Browser` alone:
  measured on device, ground `@color/splash_bg`, where a force-stopped
  (record-destroyed) launch of the same install got `splash_bg_nothing` and
  the registered order.
  **And that launch draws NO ICON unless the theme asks for one.** The
  platform does not treat it as a cold start, so its default splash style is
  EMPTY: the ground is drawn and the icon is dropped, whatever the theme
  names. Verified on device through both a recents tap and an explicit start —
  a bare white screen, right colour, no cat, with a plain `<bitmap>` and with
  a one-frame `<animation-list>` alike, so it is not the drawable.
  `android:windowSplashScreenBehavior=icon_preferred` (API 33+, hence
  `values-v33/themes.xml`, where `Theme.Browser` is restated in full because
  styles don't merge across qualifiers) is the request that brings it back,
  and it is the only lever the app has: the other one,
  `ActivityOptions.setSplashScreenStyle`, belongs to whoever STARTS the
  activity — the launcher or the recents UI. Below API 33 that path still gets
  the ground alone. Nothing app-side reaches that
  path — the value lives in PackageManager's per-user state and is write-only
  from here — so everything about that splash is frozen at build time, which
  is why it says so rather than pretending: no animation (hence no
  `windowSplashScreenAnimationDuration` for the platform to wait out), no
  minimum hold, and a plain ground that belongs to no look, since a near-white
  that is nearly Nothing's or a teal that is 98's would be wrong for everyone
  else. Light/dark is the one thing that still varies, because the system
  resolves it from the current configuration rather than from anything the app
  remembered. The system accent lives in `@color/splash_ink_dynamic`, reached
  through `SplashTheme.Dynamic` like every other accent, so a fallback splash
  can't come up in a wallpaper colour a swatch user never picked. Pre-31 has no
  registration mechanism at all, so every launch there is the fallback look;
  `drawable/splash_window.xml` is a static window background.
  **The two GROUNDS are split by `Theme.Browser.Splash`**, an intermediate
  every `SplashTheme.*` parents onto: `Theme.Browser` itself keeps
  `@color/splash_bg` (#FFFFFF / #000000, the fallback's) and
  `Theme.Browser.Splash` overrides it with `@color/splash_bg_app` (#FAF9F5 /
  #040403, the app's own canvas — the dark one tracks the ordinary dark
  scheme's tone-1 ground by hand, as `splash_bg_tui`/`_nothing` track theirs). It has to be an intermediate rather than a
  value on `Theme.Browser`, because every splash theme inherits from
  `Theme.Browser` and would otherwise inherit the fallback's ground with it.
  **Which splash is up cannot be asked, only inferred** —
  `keepSplashUpWhileRestoring(savedInstanceState)` reads a non-null bundle as
  "the ActivityRecord outlived the process", which is the same condition that
  decides the splash, reached from the other side (the record is what holds
  the bundle). That is what gates `SPLASH_MIN_MS`: the cold-start splash is
  held to 1s from `Process.getStartUptimeMillis()` even when the session is
  back sooner, because an animation that appears and leaves inside one and a
  half frames reads as a flash; the fallback splash has nothing to cycle and
  releases as soon as the session is back. Getting the inference wrong costs a
  second either way, never a wrong picture. Verified on device by raising the
  floor to 4s: `am start -W` reported 4237/4205ms for a force-stopped launch
  and 1829/1767ms for one after `am kill` with the task still in recents. At
  the real 1s the DEBUG build cannot show the difference — restore alone takes
  ~1.8s there, so the floor never binds; it is the release build's ~860ms cold
  start the floor exists for.
  Faces are chosen for ONE WIDTH: a single point size fits every frame and is
  set by whichever is tightest against the safe circle, so a narrow face
  doesn't shrink itself, it shrinks all twenty. (The fallback face is outside
  that constraint — nothing appears beside it or after it — so it gets its own
  fit against the same circle.) They're also chosen for a spread of MOUTHS
  (fifteen distinct, at most three alike) — the mouth is what reads as the
  expression, so repeating it makes one cat twice, where repeated eyes still
  read as two. `tools/render_splash_cats.swift` generates the lot — PNGs, frame
  wrappers, order lists, `values-v31/splash_orders.xml` — from its `faces`
  array and `fallbackFace`, so the set is edited there and nowhere else; it is
  byte-for-byte reproducible, so re-running it on an unchanged set rewrites
  nothing. `MainActivity.keepSplashUpWhileRestoring` is a hand-rolled
  `OnPreDrawListener` veto (capped at `SPLASH_MAX_MS` = 3s, skipped when
  `startupComplete` is already true) and releasing needs an explicit
  `content.invalidate()`. The veto's own job is not to keep a cat up but to
  avoid drawing a browser with no tabs in it; the minimum hold is the separate
  thing layered on top. The adaptive launcher icon's safe zone is a CIRCLE,
  fitted to 82% of it; `android:gravity="fill"` on the bitmap.
- **Haptics** (`ui/Haptics.kt`) go through `View.performHapticFeedback` with
  platform constants, not Compose's `LocalHapticFeedback` (which knows only
  LongPress/TextHandleMove here). Nothing passes
  `FLAG_IGNORE_GLOBAL_SETTING`. Ordinary navigation is SILENT; what buzzes is
  state changes and gestures. Don't fire one when the long-press context menu
  opens — `View.performLongClick` already did.
- **Keyboard**: the window is `adjustNothing`, so below API 30 the IME reports
  nothing and bottom-anchored bars stay where the toolbar left them. Read
  `WindowInsets.imeAnimationTarget`, never `WindowInsets.ime` — the live inset
  climbs per frame and costs one reflow each. The WebView is sized to end at
  the keyboard's top (Chromium does NOT resize a WebView's viewport for the
  OSK — the app is responsible), so `pageOverflow` may go negative and the JS
  inset goes to 0 while the keyboard is up.
- **`scrollToItem` suspends until the list is laid out** — in the private space
  there is no list, so anything queued after it is unreachable. Ask for the
  keyboard first.
- **`Base64.DEFAULT or Base64.URL_SAFE` is not "accept both"** — URL_SAFE swaps
  the alphabet. Try standard first, url-safe as a fallback.
- **`DownloadManager.ACTION_VIEW_DOWNLOADS`** is how to open the file manager;
  ACTION_VIEW on a documents-provider folder URI needs `MANAGE_DOCUMENTS` and
  throws SecurityException.
- **A linked image reports `SRC_IMAGE_ANCHOR_TYPE`** and gets link actions
  only; `hitTestResult.extra` there is the image src, never the href — use
  `requestFocusNodeHref`. Raw finger coordinates exist only in a no-op
  `setOnTouchListener`.
