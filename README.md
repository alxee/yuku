# Yuku — project draft

A minimal Android browser: WebView engine, custom Compose UI, three-button bottom
toolbar.

## Open it

1. Android Studio → **Open** → this folder.
2. The wrapper jar is binary and isn't included. `./gradlew` from the terminal will
   fail; Android Studio syncs with its own Gradle and regenerates it. If you want the
   terminal to work, run **Gradle → wrapper** from Studio's Gradle panel afterwards.
3. **You'll be prompted to download SDK Platform 35.** This machine has android-37.0
   installed; the build pins `compileSdk = 35` to match AGP 8.7.3. Accept the
   download, or bump AGP and `compileSdk` together if you'd rather use what's there —
   don't raise `compileSdk` alone, AGP 8.7.3 doesn't know API 37.
4. Android Studio may offer an AGP upgrade. Accepting is fine.
5. Create an AVD: **API 35, arm64-v8a, Google APIs**. On Apple Silicon this runs native.

There's no JDK on your PATH — only the one bundled inside Android Studio. That's fine
for building in the IDE, but if you want `./gradlew` to work from a terminal:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Change `applicationId` and `namespace` in `app/build.gradle.kts` from
`com.yuku.browser` before you ship anything.

## What works

- One WebView per tab, held by the ViewModel so recomposition can't destroy it
- Session restore: tabs come back as the page they were — navigation history and
  scroll offset included — under a still image of that page, held until the live
  one has painted, so a relaunch doesn't flash blank
- Bottom toolbar: tab count, new tab, menu chevron (rotates when the sheet opens)
- New tab sheet: URL/search field, private-mode switch, recent history
- Menu sheet: address bar (back / forward / registrable domain / copy / reload),
  four tiles, list rows, blocked-count footer
- Tab switcher: snapping horizontal carousel, swipe up to dismiss, long-press to
  gather every tab into one pile (swipe that up to clear them all), close all
- Private mode as a separate tab space, not a per-tab flag: its own tab list,
  and while it's open the whole app turns dark violet — light mode included,
  everything but Settings — with `/ᐠ_ ꞈ _ᐟ\` alone behind the tab switcher and its
  previews blurred past reading — progressively, so a page goes out of focus as
  it shrinks into its card and comes back as it's drawn out. The turn into the
  space is animated both ways too. Settings > Behavior chooses whether leaving
  keeps those tabs or closes them
- Empty state with the faint 404, which the search sheet opens over
- Hostname ad blocking, including the service-worker path — HaGeZi Pro for ads,
  AdGuard's CNAME-cloaked tracker list, both updated weekly in the background.
  Settings > Ad blocker holds the switches, a rule count, how many requests were
  blocked on the page you came from, update now, and your own lists — a feed
  URL, a file imported from the device, or rules typed straight in
- Per-site settings, as the menu row the ad blocker's used to be: ads,
  trackers and cookie banners turned off for this one site when the page needs
  them, plus a dark override and a text size that are this site's rather than
  the browser's. A site with nothing set is treated like every other, and the
  three filter switches say so when the feature is off everywhere. Nothing a
  private tab sets is written down
- Reader mode (the menu's Reader row in the browser and in the link overlay,
  offered only on a page that has an article in it): the page reduced to a headline, a byline, the prose and the
  pictures, over the top of the live document rather than instead of it, so
  turning it off is instant. The extraction runs from every page's first paint
  and keeps the longest result it has ever seen — which is what makes it work on
  a metered page, where the server sends the whole article and a script takes it
  back a second later — and reads schema.org `articleBody` and hydration blobs
  alongside the DOM
- Cookie-banner hiding, as cosmetic filtering rather than blocking: EasyList's
  cookie list injected as a stylesheet at document start, site-specific rules
  looked up by host, and the scroll lock a modal banner leaves behind undone.
  Nothing is accepted or refused on your behalf
- Page dark mode (System / Off / On, under Settings > Behavior, plus the menu's Dark
  tile), separate from the app's own theme. Sites see `prefers-color-scheme: dark` and
  use their own dark theme where they have one; the rest get an injected
  hue-preserving inversion that lands on a soft #181818/#E7E7E7 rather than pure
  black, leaves images/video/canvas at their original colors, and stands down on
  anything already dark
- Pull to refresh (toggleable, Settings > Behavior > Gestures), which stands down
  for sideways swipes and for drags starting inside a scrolled-down element on the
  page
- Saved passwords: logins captured from a submitted form and offered for saving,
  filled back from a chip when the cursor lands in a sign-in form, and kept in an
  AES-GCM vault under an AndroidKeyStore key (never in the settings blob, never
  in the private space, excluded from backup). Settings > Passwords is a Save
  passwords switch and a choice of who manages them — this browser, or whichever
  password manager the device's autofill service actually is — never both at
  once, with the vault itself one step further in behind the device lock
- Address and card autofill, offered above the keyboard when the cursor lands
  in a delivery or payment form, out of the same encrypted vault the logins
  are in and behind the same lock. Both are typed in once under Settings >
  Passwords & autofill rather than taken off a page, each has its own switch,
  and the card's security code is never stored — the one number on a card
  whose whole job is proving you are holding it
- Desktop mode via UA override
- Page text size, as one setting for every tab under Settings > Appearance and
  as a per-site override in the menu's Site settings sheet — slide it back to
  the app's own percentage and the site follows the app again. Kept across
  restarts
- Downloads: a download link saves through the system's download manager, with
  the page's cookies, `Referer` and user-agent attached — a private tab's from
  its own profile's jar — into the device's own `Downloads` folder, which is
  where the Downloads screen reads from. `data:` URLs are written directly.
  Every download is confirmed first (name, size, host; tap outside to
  decline); a confirmed one's icon drops off the card into the menu button,
  which turns its chevron down with a progress ring round it while anything
  is in flight. The Downloads screen shows each one in progress with a bar
  and a cancel button
- File uploads: `<input type="file">` opens the picker the form asked for,
  accept types and multiple-selection included
- Fullscreen video, with the system bars away and the screen kept awake
- The dialogs a script can raise — `alert`, `confirm`, `prompt` and the
  leave-this-page question — as a card over the page, one at a time, and a
  certificate that doesn't check out as a warning that can be read and
  overridden per host rather than a load that silently fails
- Sites behind a password — the `401` in front of a router's admin page, a
  staging box, an old intranet — as a sign-in card rather than a page that
  fails with nothing said. The login is reused for the rest of that host's
  requests so one protected page doesn't ask twenty times, kept in memory for
  the session only (never on disk, never in WebView's own plaintext store),
  and dropped the moment the server rejects it
- Reloading a page that was the result of a form — search results, a submitted
  order — asks before sending it again, instead of quietly doing nothing
- A renderer that dies takes its tab down and no more. Android's default is to
  kill the whole app process, losing every other tab's live page; here the
  dead view is cleared away, the page comes back on its own, and it says
  whether it crashed or was reclaimed for memory
- History and bookmarks both search, by title, host or address, with the field
  pinned above the list rather than scrolling away with it
- Site permissions: a page asking for the camera, the microphone or the user's
  location gets a card over it — Block / Allow this time / Allow — and the
  answer is kept per origin, where Settings > Privacy > Site permissions can
  take it back. Each of the three also has a global Ask / Allow / Block, which
  a site's own answer overrides. An allow is two permissions, not one: the
  site's, and then Android's own for this app, asked in that order and only
  when it is actually needed. A private tab's answers never reach disk
- Site notifications: WebView has no `Notification` API, so one is supplied by
  an injected script and drawn as Android notifications. The fourth site
  permission, and the one that is OFF by default — Block until the rule in
  Site permissions is set to Ask. Asked only after a tap, never in private
  tabs; no push, so a site notifies only while it is open in a tab. Tapping
  one opens its tab
- Clear browsing data (Settings > Privacy): history, cookies, site data and
  cached files, each its own switch. Open tabs and saved passwords are left
  alone — the second has its own list behind the device lock
- Links from other apps open as an overlay, not as the whole browser: close, a
  working address bar (the browser's own omnibox — tap it to type a different
  page), and an overflow menu with reload/share/copy across the top and open in
  Yuku, bookmark, desktop site and find on page below. It fades up over the app
  the link was tapped in and drops straight back into it when closed. It's a
  real Custom Tabs provider, so apps using `CustomTabsIntent` pick us for it —
  and an app with its own in-app browser is left alone, since it never asks
- Handles `VIEW` intents; can be set as the system default browser

## What's still missing

| Area | State |
|---|---|
| Undo | Closing a tab is immediate; no Snackbar, and no recently-closed list |
| Bookmark folders | `BookmarkEntry` is flat: url, title, host. No folders, renaming or reordering |
| Print / save as PDF | No `createPrintDocumentAdapter` — no way to get a ticket or a receipt off a page |
| Add to home screen | No `pinShortcut`. The launcher shortcuts in `MainActivity` are static app shortcuts, a different thing |
| Translate | No translation of any kind |
| Homepage | No startup-page setting; launch behaviour is only "open the new tab sheet" |
| Export / import | Bookmarks, history and the vault live in `filesDir` and leave with the device |

## Things that will bite you

- **`INTERNET` permission** is in the manifest. Remove it and you get a blank white
  page with no error.
- **Dark mode needs the app theme too.** `setAlgorithmicDarkeningAllowed` only takes
  effect when the theme reports `android:isLightTheme="false"`. Flipping the toggle
  alone does nothing until `themes.xml` follows.
- **Swipe-to-dismiss gets clipped** the moment you move the switcher into a nested
  container — set `clipChildren="false"` up the whole hierarchy.
- **Touch targets vs glyph size.** Address-bar icons render at 16dp inside 32dp
  buttons. Below 48dp fails accessibility review; expand with padding, don't grow
  the glyph.
- **`shouldInterceptRequest` runs on a background thread** for every subresource.
  Keep `AdBlocker.shouldBlock` allocation-free.

## Blocklist

Real lists, downloaded and merged at runtime — see `core/BlocklistFeeds.kt` and
`core/BlocklistStore.kt`, and the Ad blocker screen for what the user can change.
`app/src/main/assets/blocklist.txt` is only the seed used before the first update.
To go beyond hostname blocking, swap `AdBlocker`'s internals for Brave's `adblock`
crate over JNI — the call site doesn't change.

## Layout

```
app/src/main/java/com/yuku/browser/
  MainActivity.kt          entry point, intent handling, WebView debugging
  CustomTabActivity.kt     the overlay: one page, in the calling app's task
  CustomTabsProvider.kt    the service that makes apps pick us for it
  core/
    Tab.kt                 tab state (no WebView reference — deliberate)
    FileDownloads.kt       the far side of setDownloadListener
    DownloadProgress.kt    polls DownloadManager for the toolbar's progress ring
    SitePermissions.kt     what a page may ask the device for, and per-site answers
    WebNotifications.kt    window.Notification polyfill, bridged to Android notifications
    BrowserViewModel.kt    WebView lifecycle, tab list, navigation, toggles
    AdBlocker.kt           hostname matching, per-page counter
    PasswordStore.kt       the vault: AES-GCM under an AndroidKeyStore key
    PasswordForms.kt       capture/fill/focus in the page, over one JS bridge
    ReaderMode.kt          article extraction from first paint, and the reader overlay
    SiteSettings.kt        what one site gets that the rest of the web does not
    AutofillEntries.kt     saved addresses and cards, in the password vault
    FormFields.kt          finding and filling a checkout form, over one JS bridge
    UrlUtils.kt            omnibox parsing, registrable domain via OkHttp's PSL
    CustomTabRequest.kt    what the calling app asked for, and events back to it
  ui/
    BrowserScreen.kt       Scaffold, sheets, back handling, WebView host
    WebPlatform.kt         downloads, uploads, fullscreen, JS dialogs, certificates,
                           HTTP auth, form resubmission, permissions, crashes
    BottomToolbar.kt       three buttons, chevron rotation
    CustomTabScreen.kt     the overlay's header, menu and page
    NewTabSheet.kt         search field, private toggle, recents
    PrivateVisuals.kt      the private space's backdrop and preview blur
    MenuSheet.kt           address bar, tiles, list, footer
    PasswordsScreen.kt     Settings > Passwords & autofill
    AutofillPanes.kt       its addresses and payment methods, and their editors
    SiteSettingsSheet.kt   the menu's per-site sheet
    AdBlockPane.kt         Settings > Ad blocker
    PasswordPrompts.kt     the save card and the fill chips, above the toolbar
    TabSwitcher.kt         carousel, drag-to-dismiss, close all
    EmptyState.kt          the 404
    Modifiers.kt           fadeEdges — DstIn gradient instead of ellipsis
    theme/Theme.kt         colors
    theme/Special.kt       the special themes' shared shape rules
    theme/Tui.kt           the TUI theme's palette and type
    theme/Nothing.kt       the Nothing theme's palette, type and dot font
    theme/NothingIcons.kt  its monoline glyph set
    theme/Ninety8.kt       the 98 theme's system colours, pixel type and bevels
    theme/Ninety8Icons.kt  its icons: Pixelarticons (MIT), vendored as art
    theme/Aero.kt          the Aero theme's glass palette, type and gloss
    theme/AeroIcons.kt     its icons: Material's own Rounded cut
```
