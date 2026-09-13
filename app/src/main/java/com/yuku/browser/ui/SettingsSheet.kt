package com.yuku.browser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.yuku.browser.ui.theme.FieldBg
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Vignette
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon as MaterialIcon
import com.yuku.browser.ui.theme.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.yuku.browser.ui.theme.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuku.browser.core.AccentTheme
import com.yuku.browser.core.BlocklistStore
import com.yuku.browser.core.BrowserViewModel
import com.yuku.browser.core.SpecialTheme
import androidx.compose.material.icons.filled.BlurOn
import com.yuku.browser.ui.theme.aeroRefractionSupported
import com.yuku.browser.core.HistorySort
import com.yuku.browser.core.NewTabPlacement
import com.yuku.browser.core.PageDarkMode
import com.yuku.browser.core.PermissionRule
import com.yuku.browser.core.SitePermission
import com.yuku.browser.core.SitePermissionGrant
import com.yuku.browser.core.displayOrigin
import com.yuku.browser.core.SavedAddress
import com.yuku.browser.core.SavedCard
import com.yuku.browser.core.SavedPassword
import com.yuku.browser.core.SearchEngine
import com.yuku.browser.core.TabManagerMode
import com.yuku.browser.core.ThemeMode
import com.yuku.browser.core.ZOOM_STEPS
import com.yuku.browser.ui.theme.AccentColor
import com.yuku.browser.ui.theme.BarBg
import com.yuku.browser.ui.theme.HairLine
import com.yuku.browser.BuildConfig
import com.yuku.browser.ui.theme.Ink
import com.yuku.browser.ui.theme.InkMuted
import com.yuku.browser.ui.theme.InkStrong
import com.yuku.browser.ui.theme.color
import kotlin.math.roundToInt
import com.yuku.browser.ui.theme.specialCorner
import com.yuku.browser.ui.theme.SpecialCircle

/**
 * Which pane of the full-screen settings page is currently showing.
 *
 * Declaration order is not incidental: the pane transition takes its
 * direction from the ordinals, so a pane must come after the one it is
 * reached from. [parent] is what back follows, which is only interesting for
 * [SavedPasswords] — it is the one pane two steps in.
 */
enum class SettingsPane {
    Root, SearchEngine, Appearance, Behavior, Gestures,
    Passwords, SavedPasswords, SavedAddresses, SavedCards,
    Permissions, AdBlock, ClearData;

    val parent: SettingsPane?
        get() = when (this) {
            Root -> null
            SavedPasswords, SavedAddresses, SavedCards -> Passwords
            else -> Root
        }
}

/**
 * Reached from the menu's "App settings" row as a full-screen page rather
 * than another bottom sheet — it's a destination in its own right, not a
 * quick action over the current page, so it gets the whole screen and its
 * own drill-down navigation between [SettingsPane]s.
 */
@Composable
fun SettingsScreen(
    pane: SettingsPane,
    searchEngine: SearchEngine,
    // Every engine there is, the user's own included; [disabledSearchEngines]
    // is which of them are switched out of the omnibox's picker.
    searchEngines: List<SearchEngine>,
    disabledSearchEngines: Set<String>,
    searchSuggestionsEnabled: Boolean,
    themeMode: ThemeMode,
    pageDarkMode: PageDarkMode,
    accentTheme: AccentTheme,
    specialTheme: SpecialTheme,
    pageZoom: Int,
    pageLens: Boolean,
    translucentSheets: Boolean,
    translucency: Float,
    autoFocusNewTabKeyboard: Boolean,
    openNewTabSheetOnLaunch: Boolean,
    newTabHistorySort: HistorySort,
    tabManagerMode: TabManagerMode,
    doubleTapTabsSwitchesTab: Boolean,
    swipeToSwitchTabs: Boolean,
    flickToCloseTab: Boolean,
    linkStripperEnabled: Boolean,
    openExternalLinksInOverlay: Boolean,
    openLinksInApps: Boolean,
    pullToRefreshEnabled: Boolean,
    linkPreviewEnabled: Boolean,
    newTabPlacement: NewTabPlacement,
    keepPrivateTabs: Boolean,
    savePasswords: Boolean,
    externalPasswordManager: Boolean,
    savedPasswords: List<SavedPassword>,
    neverSavedSites: Set<String>,
    savedAddresses: List<SavedAddress>,
    savedCards: List<SavedCard>,
    fillAddresses: Boolean,
    fillPaymentMethods: Boolean,
    permissionRules: Map<SitePermission, PermissionRule>,
    sitePermissions: List<SitePermissionGrant>,
    // The ad blocker's own state, which used to belong to a destination of
    // its own off the browser menu. See [AdBlockPane].
    adBlockEnabled: Boolean,
    blocklistConfig: BlocklistStore.Config,
    blocklistMeta: BlocklistStore.Meta,
    blocklistRuleCount: Int,
    cosmeticRuleCount: Int,
    blockedOnPage: Int,
    blocklistUpdating: BrowserViewModel.BlocklistProgress?,
    blocklistImportError: String?,
    onNavigate: (SettingsPane) -> Unit,
    onBack: () -> Unit,
    onSelectSearchEngine: (SearchEngine) -> Unit,
    onArrangeSearchEngines: (order: List<String>, disabled: Set<String>) -> Unit,
    onAddCustomSearchEngine: (label: String, url: String) -> Boolean,
    onRemoveCustomSearchEngine: (SearchEngine) -> Unit,
    onToggleSearchSuggestions: () -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectPageDarkMode: (PageDarkMode) -> Unit,
    onSelectAccent: (AccentTheme) -> Unit,
    onSelectSpecialTheme: (SpecialTheme) -> Unit,
    onSetZoomStep: (Int) -> Unit,
    onTogglePageLens: () -> Unit,
    onToggleTranslucentSheets: () -> Unit,
    onSetTranslucency: (Float) -> Unit,
    onToggleAutoFocusNewTabKeyboard: () -> Unit,
    onToggleOpenNewTabSheetOnLaunch: () -> Unit,
    onSelectNewTabHistorySort: (HistorySort) -> Unit,
    onSelectTabManagerMode: (TabManagerMode) -> Unit,
    onToggleDoubleTapTabsSwitchesTab: () -> Unit,
    onToggleSwipeToSwitchTabs: () -> Unit,
    onToggleFlickToCloseTab: () -> Unit,
    onToggleLinkStripper: () -> Unit,
    onToggleOpenExternalLinksInOverlay: () -> Unit,
    onToggleOpenLinksInApps: () -> Unit,
    onTogglePullToRefresh: () -> Unit,
    onToggleLinkPreview: () -> Unit,
    onSelectNewTabPlacement: (NewTabPlacement) -> Unit,
    onToggleKeepPrivateTabs: () -> Unit,
    onToggleSavePasswords: () -> Unit,
    onSetExternalPasswordManager: (Boolean) -> Unit,
    onRemoveSavedPassword: (host: String, username: String) -> Unit,
    onAllowSavingForHost: (String) -> Unit,
    onClearSavedPasswords: () -> Unit,
    onToggleFillAddresses: () -> Unit,
    onToggleFillPaymentMethods: () -> Unit,
    onSaveAddress: (SavedAddress) -> Unit,
    onRemoveAddress: (String) -> Unit,
    onClearAddresses: () -> Unit,
    onSaveCard: (SavedCard) -> Unit,
    onRemoveCard: (String) -> Unit,
    onClearCards: () -> Unit,
    onSetPermissionRule: (SitePermission, PermissionRule) -> Unit,
    onClearSitePermission: (SitePermissionGrant) -> Unit,
    onClearSitePermissions: () -> Unit,
    onClearBrowsingData: (history: Boolean, cookies: Boolean, cache: Boolean, siteData: Boolean) -> Unit,
    onToggleAdBlock: () -> Unit,
    onToggleTrackerBlocking: () -> Unit,
    onToggleCookieBanners: () -> Unit,
    onToggleBlocklistAutoUpdate: () -> Unit,
    onUpdateBlocklist: () -> Unit,
    onAddBlocklistFeedUrl: (String) -> Unit,
    onRemoveBlocklistFeedUrl: (String) -> Unit,
    onImportBlocklistFile: (Uri) -> Unit,
    onRemoveImportedBlocklist: (String) -> Unit,
    onSetBlocklistCustomEntries: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(BarBg.copy(alpha = 1f))
            .statusBarsPadding()
    ) {
        AnimatedContent(
            targetState = pane,
            transitionSpec = {
                // Modelled on the system Settings app's own pane transition,
                // measured off this device (Android 16) by slowing animations
                // 20x and correlating each captured frame against the settled
                // one to recover both layers' offsets frame by frame.
                //
                // What it actually does, in both directions, is a shared axis:
                // BOTH panes travel the same way and they cross-fade. Going in,
                // the root drifts left and is gone within the first third while
                // the leaf comes in from the right; going back it mirrors
                // exactly — the leaf drifts right, the root returns from the
                // left. Measured leaf offsets on the way in were +104, +56,
                // +40, +32, +24, +16, +8, 0 px: a hard decelerate with a long
                // tail. The outgoing pane's own drift accelerates instead, and
                // its fade cuts it off well before it would have finished.
                //
                // So it is NOT one pane covering another — nothing is opaque
                // here, and neither pane needs a ground of its own.
                val arriveSlide = tween<IntOffset>(PANE_SLIDE_MS, easing = PaneDecelerate)
                val departSlide = tween<IntOffset>(PANE_SLIDE_MS, easing = Accelerate)
                // The fades are the asymmetric half: the outgoing pane is gone
                // over the first [PANE_FADE_OUT_MS], and the incoming one only
                // starts once it is, so the two are never both readable on top
                // of each other.
                val fadeAway = tween<Float>(PANE_FADE_OUT_MS, easing = LinearEasing)
                val fadeUp = tween<Float>(
                    PANE_SLIDE_MS - PANE_FADE_OUT_MS,
                    delayMillis = PANE_FADE_OUT_MS,
                    easing = LinearEasing,
                )
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(arriveSlide) { dir * it / PANE_SLIDE_FRACTION } +
                    fadeIn(fadeUp))
                    .togetherWith(
                        slideOutHorizontally(departSlide) { -dir * it / PANE_SLIDE_FRACTION } +
                            fadeOut(fadeAway)
                    )
            },
            label = "settingsPane",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            // The viewport is measured OUTSIDE the scroll — inside one, a
            // BoxWithConstraints is handed an infinite height and knows
            // nothing about the screen. [RootPane]'s footer needs the height
            // its content has to fill before it can sit at the bottom of it,
            // and that height is this box less the padding the scrolling
            // content carries below.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val paneHeight = maxHeight -
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() -
                    12.dp
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp)
                ) {
                    when (current) {
                        SettingsPane.Root -> RootPane(
                            paneHeight = paneHeight,
                            searchEngine = searchEngine,
                            themeMode = themeMode,
                            accentTheme = accentTheme,
                            specialTheme = specialTheme,
                            savedPasswordCount = savedPasswords.size,
                            permissionSiteCount = sitePermissions.map { it.origin }.distinct().size,
                            adBlockEnabled = adBlockEnabled,
                            onBack = onBack,
                            onNavigate = onNavigate,
                        )

                        SettingsPane.SearchEngine -> SearchEnginePane(
                            current = searchEngine,
                            engines = searchEngines,
                            disabled = disabledSearchEngines,
                            suggestionsEnabled = searchSuggestionsEnabled,
                            onSelect = onSelectSearchEngine,
                            onArrange = onArrangeSearchEngines,
                            onAddCustom = onAddCustomSearchEngine,
                            onRemoveCustom = onRemoveCustomSearchEngine,
                            onToggleSuggestions = onToggleSearchSuggestions,
                            onBack = onBack,
                        )

                        SettingsPane.Appearance -> AppearancePane(
                            themeMode = themeMode,
                            pageDarkMode = pageDarkMode,
                            accentTheme = accentTheme,
                            specialTheme = specialTheme,
                            pageZoom = pageZoom,
                            onSelectThemeMode = onSelectThemeMode,
                            onSelectPageDarkMode = onSelectPageDarkMode,
                            onSelectAccent = onSelectAccent,
                            onSelectSpecialTheme = onSelectSpecialTheme,
                            onSetZoomStep = onSetZoomStep,
                            pageLens = pageLens,
                            onTogglePageLens = onTogglePageLens,
                            translucentSheets = translucentSheets,
                            onToggleTranslucentSheets = onToggleTranslucentSheets,
                            translucency = translucency,
                            onSetTranslucency = onSetTranslucency,
                            onBack = onBack,
                        )

                        SettingsPane.Behavior -> BehaviorPane(
                            autoFocusNewTabKeyboard = autoFocusNewTabKeyboard,
                            openNewTabSheetOnLaunch = openNewTabSheetOnLaunch,
                            newTabHistorySort = newTabHistorySort,
                            newTabPlacement = newTabPlacement,
                            tabManagerMode = tabManagerMode,
                            linkStripperEnabled = linkStripperEnabled,
                            openExternalLinksInOverlay = openExternalLinksInOverlay,
                            openLinksInApps = openLinksInApps,
                            linkPreviewEnabled = linkPreviewEnabled,
                            keepPrivateTabs = keepPrivateTabs,
                            onToggleAutoFocusNewTabKeyboard = onToggleAutoFocusNewTabKeyboard,
                            onToggleOpenNewTabSheetOnLaunch = onToggleOpenNewTabSheetOnLaunch,
                            onSelectNewTabHistorySort = onSelectNewTabHistorySort,
                            onSelectNewTabPlacement = onSelectNewTabPlacement,
                            onSelectTabManagerMode = onSelectTabManagerMode,
                            onToggleLinkStripper = onToggleLinkStripper,
                            onToggleOpenExternalLinksInOverlay = onToggleOpenExternalLinksInOverlay,
                            onToggleOpenLinksInApps = onToggleOpenLinksInApps,
                            onToggleLinkPreview = onToggleLinkPreview,
                            onToggleKeepPrivateTabs = onToggleKeepPrivateTabs,
                            onBack = onBack,
                        )

                        SettingsPane.Gestures -> GesturesPane(
                            pullToRefreshEnabled = pullToRefreshEnabled,
                            swipeToSwitchTabs = swipeToSwitchTabs,
                            doubleTapTabsSwitchesTab = doubleTapTabsSwitchesTab,
                            flickToCloseTab = flickToCloseTab,
                            onTogglePullToRefresh = onTogglePullToRefresh,
                            onToggleSwipeToSwitchTabs = onToggleSwipeToSwitchTabs,
                            onToggleDoubleTapTabsSwitchesTab = onToggleDoubleTapTabsSwitchesTab,
                            onToggleFlickToCloseTab = onToggleFlickToCloseTab,
                            onBack = onBack,
                        )

                        SettingsPane.Passwords -> PasswordsPane(
                            savePasswords = savePasswords,
                            externalManager = externalPasswordManager,
                            savedCount = savedPasswords.size,
                            addressCount = savedAddresses.size,
                            cardCount = savedCards.size,
                            fillAddresses = fillAddresses,
                            fillPaymentMethods = fillPaymentMethods,
                            onToggleSavePasswords = onToggleSavePasswords,
                            onSetExternalManager = onSetExternalPasswordManager,
                            onToggleFillAddresses = onToggleFillAddresses,
                            onToggleFillPaymentMethods = onToggleFillPaymentMethods,
                            onOpenSaved = { onNavigate(SettingsPane.SavedPasswords) },
                            onOpenAddresses = { onNavigate(SettingsPane.SavedAddresses) },
                            onOpenCards = { onNavigate(SettingsPane.SavedCards) },
                            onBack = onBack,
                        )

                        SettingsPane.SavedAddresses -> SavedAddressesPane(
                            entries = savedAddresses,
                            onSave = onSaveAddress,
                            onRemove = onRemoveAddress,
                            onClearAll = onClearAddresses,
                            onBack = onBack,
                        )

                        SettingsPane.SavedCards -> SavedCardsPane(
                            entries = savedCards,
                            onSave = onSaveCard,
                            onRemove = onRemoveCard,
                            onClearAll = onClearCards,
                            onBack = onBack,
                        )

                        SettingsPane.SavedPasswords -> SavedPasswordsPane(
                            entries = savedPasswords,
                            neverSaved = neverSavedSites,
                            onRemove = onRemoveSavedPassword,
                            onAllowSaving = onAllowSavingForHost,
                            onClearAll = onClearSavedPasswords,
                            onBack = onBack,
                        )

                        SettingsPane.Permissions -> PermissionsPane(
                            rules = permissionRules,
                            grants = sitePermissions,
                            onSetRule = onSetPermissionRule,
                            onClear = onClearSitePermission,
                            onClearAll = onClearSitePermissions,
                            onBack = onBack,
                        )

                        SettingsPane.AdBlock -> AdBlockPane(
                            enabled = adBlockEnabled,
                            config = blocklistConfig,
                            meta = blocklistMeta,
                            ruleCount = blocklistRuleCount,
                            cosmeticCount = cosmeticRuleCount,
                            blockedOnPage = blockedOnPage,
                            updating = blocklistUpdating,
                            importError = blocklistImportError,
                            onToggleEnabled = onToggleAdBlock,
                            onToggleTrackers = onToggleTrackerBlocking,
                            onToggleCookieBanners = onToggleCookieBanners,
                            onToggleAutoUpdate = onToggleBlocklistAutoUpdate,
                            onUpdateNow = onUpdateBlocklist,
                            onAddFeedUrl = onAddBlocklistFeedUrl,
                            onRemoveFeedUrl = onRemoveBlocklistFeedUrl,
                            onImportFile = onImportBlocklistFile,
                            onRemoveImportedList = onRemoveImportedBlocklist,
                            onSetCustomEntries = onSetBlocklistCustomEntries,
                            onBack = onBack,
                        )

                        SettingsPane.ClearData -> ClearDataPane(
                            onClear = onClearBrowsingData,
                            onBack = onBack,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PaneHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back", tint = Ink)
        }
        Text(text = title, color = InkStrong, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun RootPane(
    /**
     * How tall the pane's content has to be for its footer to sit at the
     * bottom of the screen: the scroll viewport less the padding under it.
     * The root pane is the one screen here short enough to end above the
     * fold, so it is the only one that has to be told.
     */
    paneHeight: Dp,
    searchEngine: SearchEngine,
    themeMode: ThemeMode,
    accentTheme: AccentTheme,
    specialTheme: SpecialTheme,
    savedPasswordCount: Int,
    permissionSiteCount: Int,
    adBlockEnabled: Boolean,
    onBack: () -> Unit,
    onNavigate: (SettingsPane) -> Unit,
) {
    // Two children arranged apart inside a box at least a screen tall: the
    // list at the top, the signature at the bottom. `propagateMinConstraints`
    // is what carries the minimum through to the Column — a Box hands its
    // children a zero minimum otherwise, and a Column with an unbounded
    // maximum gives a weighted Spacer nothing, which is why this is an
    // arrangement rather than a weight. Content taller than a screen simply
    // grows past the minimum and the arrangement has no slack to spend.
    Box(Modifier.heightIn(min = paneHeight), propagateMinConstraints = true) {
        Column(verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                RootRows(
                    searchEngine = searchEngine,
                    themeMode = themeMode,
                    accentTheme = accentTheme,
                    specialTheme = specialTheme,
                    savedPasswordCount = savedPasswordCount,
                    permissionSiteCount = permissionSiteCount,
                    adBlockEnabled = adBlockEnabled,
                    onBack = onBack,
                    onNavigate = onNavigate,
                )
            }
            AboutFooter()
        }
    }
}

@Composable
private fun ColumnScope.RootRows(
    searchEngine: SearchEngine,
    themeMode: ThemeMode,
    accentTheme: AccentTheme,
    specialTheme: SpecialTheme,
    savedPasswordCount: Int,
    permissionSiteCount: Int,
    adBlockEnabled: Boolean,
    onBack: () -> Unit,
    onNavigate: (SettingsPane) -> Unit,
) {
    PaneHeader("Settings", onBack)

    SectionHeader("Browsing")
    MenuRow(Icons.Default.Search, "Search engine", onClick = { onNavigate(SettingsPane.SearchEngine) }) {
        RowValue(searchEngine.label)
    }
    MenuRow(Icons.Default.Palette, "Appearance", onClick = { onNavigate(SettingsPane.Appearance) }) {
        // A special theme replaces the accent's whole palette, so naming
        // the accent beside it would be describing colours that aren't on
        // screen — the row says which look is actually in force. Under the
        // Default look there is no such name to give, so the accent, which is
        // what that look is coloured by, takes the slot.
        RowValue(
            if (specialTheme == SpecialTheme.Default) "${themeMode.label} · ${accentTheme.label}"
            else "${themeMode.label} · ${specialTheme.label}"
        )
    }
    MenuRow(Icons.Default.Tune, "Behavior", onClick = { onNavigate(SettingsPane.Behavior) }) {
        Chevron()
    }
    MenuRow(Icons.Default.TouchApp, "Gestures", onClick = { onNavigate(SettingsPane.Gestures) }) {
        Chevron()
    }

    HorizontalDivider(color = HairLine, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

    // One section, not two. Passwords had a header of its own over a single
    // row, which is a heading that names its own row twice; and what it is
    // about — who else can get at what this browser knows about you — is what
    // the two rows under Privacy were about as well.
    SectionHeader("Privacy")
    MenuRow(Icons.Default.Key, "Saved passwords", onClick = { onNavigate(SettingsPane.Passwords) }) {
        RowValue(if (savedPasswordCount == 0) "None saved" else "$savedPasswordCount saved")
    }
    MenuRow(Icons.Default.PrivacyTip, "Site permissions", onClick = { onNavigate(SettingsPane.Permissions) }) {
        RowValue(if (permissionSiteCount == 0) "None set" else "$permissionSiteCount site${if (permissionSiteCount == 1) "" else "s"}")
    }
    // Moved here from the browser menu, where it sat behind a row over the
    // page it had nothing to do with — every switch on it is true of every
    // site at once, which is what this screen is for. The per-site half went
    // the other way, into the menu row that row used to be.
    MenuRow(Icons.Default.Block, "Ad blocker", onClick = { onNavigate(SettingsPane.AdBlock) }) {
        RowValue(if (adBlockEnabled) "On" else "Off")
    }
    MenuRow(Icons.Default.DeleteSweep, "Clear browsing data", onClick = { onNavigate(SettingsPane.ClearData) }) {
        Chevron()
    }

    HorizontalDivider(color = HairLine, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

    SectionHeader("Default browser")
    DefaultBrowserRow()
}

private const val FOOTER_ALPHA = 0.55f

/**
 * The signature at the foot of the list: what this is, which build of it, and
 * the cat.
 *
 * Drawn in the theme's own quiet ink, thinned — it is the end of the screen
 * rather than a row on it, and anything at full ink here reads as one more
 * setting with nothing to tap. Thinned rather than set to `InkFaint`, which
 * is the scheme's `outlineVariant`: right for a hairline, and a line of text
 * at that value is not quiet, it is unreadable.
 *
 * The face is the splash's own (see `tools/render_splash_cats.swift`), which
 * is what makes it a signature rather than a decoration: the thing that
 * opened the app is what closes its settings.
 *
 * `FontFamily.Default` for the face for [PlaceholderBlock]'s reason — none of
 * these glyphs live in the app's own faces, and a special theme's face has
 * fewer still, so per-glyph fallback has to be left to do its work or the
 * line comes out as tofu.
 */
@Composable
private fun AboutFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // No bottom padding of its own: the scroll Column already ends
            // in 12dp plus the navigation-bar inset, and the footer is meant
            // to sit at the very end of the screen rather than a gap above it.
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Yuku Browser ${BuildConfig.VERSION_NAME}",
            color = InkMuted.copy(alpha = FOOTER_ALPHA),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "\u0e05^\u2022\ufecc\u2022^\u0e05",
            color = InkMuted.copy(alpha = FOOTER_ALPHA),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Default,
        )
    }
}

/**
 * The device's own capabilities, and who has them.
 *
 * Two questions on one screen, and they are genuinely two: the pickers at the
 * top say what a site the user has never answered about gets when it asks,
 * and the list below is the sites they have answered about. A site's own
 * answer always wins — that is what makes the global "Block" usable at all,
 * since blocking everything and then allowing the two sites that need a
 * camera is the arrangement most people actually want.
 *
 * Ask is the default and cannot sensibly be anything else: Allow would hand
 * the device out on the strength of a page having loaded, and Block would
 * make every site's camera button look broken with nothing on screen to say
 * why. Notifications are the exception ([SitePermission.defaultRule]): off
 * until the user turns them to Ask, since nothing about a page is broken by
 * not being able to reach the notification shade.
 */
@Composable
private fun PermissionsPane(
    rules: Map<SitePermission, PermissionRule>,
    grants: List<SitePermissionGrant>,
    onSetRule: (SitePermission, PermissionRule) -> Unit,
    onClear: (SitePermissionGrant) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
) {
    PaneHeader("Site permissions", onBack)

    SectionHeader("When a site asks")
    SitePermission.entries.forEach { permission ->
        PickerLabel(permission.label)
        Picker(
            entries = PermissionRule.entries,
            selected = rules[permission] ?: permission.defaultRule,
            label = { it.label },
            onSelect = { onSetRule(permission, it) },
        )
        Hint(permissionHint(permission))
    }
    // Said once, at the bottom of the pickers, because it is the thing that
    // most often looks like a bug: allowing a site here is only half of it,
    // and the other half is a system dialog that appears the first time it
    // matters rather than now.
    Hint("Allowing a site also needs Android's own permission for this app — you'll be asked for it the first time a site uses one.")

    if (grants.isEmpty()) return

    Divider()
    SectionHeader("Sites you've answered")
    Hint("Clearing one means that site is asked about again.")
    grants
        .sortedWith(compareBy({ it.origin.lowercase() }, { it.permission.ordinal }))
        .forEach { grant -> SitePermissionRow(grant = grant, onClear = { onClear(grant) }) }

    Spacer(Modifier.height(8.dp))
    DangerRow(
        label = "Clear all site permissions",
        confirmLabel = "Tap again to clear ${grants.size}",
        onConfirm = onClearAll,
    )
    Spacer(Modifier.height(24.dp))
}

/** One site's standing answer for one capability, and the way to take it back. */
@Composable
private fun SitePermissionRow(grant: SitePermissionGrant, onClear: () -> Unit) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The site's own mark, as everywhere else a site is listed. The host
        // is what SiteIcon keys on, and an origin is a host with a scheme in
        // front of it.
        SiteIcon(
            host = grant.origin.substringAfter("://"),
            size = 32.dp,
            corner = 10.dp,
            letterSize = 14.sp,
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 8.dp),
        ) {
            Text(
                text = displayOrigin(grant.origin),
                color = InkStrong,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${grant.permission.label} — ${if (grant.allowed) "Allowed" else "Blocked"}",
                // The verdict carries the colour rather than the row: a list
                // of blocked sites is not a list of warnings, and a list of
                // allowed ones is the half worth being able to pick out.
                color = if (grant.allowed) AccentColor else InkMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        IconButton(onClick = {
            haptics.confirm()
            onClear()
        }) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Ask again for ${displayOrigin(grant.origin)}",
                tint = InkMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun permissionHint(permission: SitePermission): String = when (permission) {
    SitePermission.Location -> "Maps and shops asking where you are."
    SitePermission.Camera -> "Video calls, and anything scanning a code."
    SitePermission.Microphone -> "Video calls, voice search, and recording."
    SitePermission.Notifications ->
        "Messages and alerts from a site, while it's open in a tab. Off unless you change it; never in private tabs."
}

/**
 * What the browser can be asked to forget. Four switches and one button,
 * rather than a row per kind that clears on the spot: these are almost always
 * wanted together, and a list of separate destructive rows is four chances to
 * hit the wrong one.
 *
 * The four are separate at all because they answer different questions —
 * history is what you did, cookies are who the sites think you are, the cache
 * is only what was faster to keep, and site data is what pages stored
 * themselves. Signing out of everything is cookies; clearing a stuck page is
 * the cache; and those should not be the same press.
 *
 * What is deliberately NOT on this screen: the open tabs (clearing what has
 * been visited should not close what is being read) and the saved passwords,
 * which have their own list, their own delete-everything row and a device
 * lock in front of both. See BrowserViewModel.clearBrowsingData.
 */
@Composable
private fun ClearDataPane(
    onClear: (history: Boolean, cookies: Boolean, cache: Boolean, siteData: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    PaneHeader("Clear browsing data", onBack)

    var history by remember { mutableStateOf(true) }
    var cookies by remember { mutableStateOf(true) }
    var cache by remember { mutableStateOf(true) }
    var siteData by remember { mutableStateOf(true) }
    // Said once, where the press happened. The alternative is a screen that
    // looks exactly the same afterwards as before, which reads as nothing
    // having happened.
    var cleared by remember { mutableStateOf(false) }

    SectionHeader("What to clear")
    MenuRow(Icons.Default.History, "Browsing history", onClick = { history = !history }, toggledTo = !history) {
        MenuSwitch(checked = history) { history = !history }
    }
    Hint("The list under History, and the sites suggested on a new tab.")
    MenuRow(Icons.Default.Cookie, "Cookies", onClick = { cookies = !cookies }, toggledTo = !cookies) {
        MenuSwitch(checked = cookies) { cookies = !cookies }
    }
    Hint("Signs you out of everything you're signed in to.")
    MenuRow(Icons.Default.Storage, "Site data", onClick = { siteData = !siteData }, toggledTo = !siteData) {
        MenuSwitch(checked = siteData) { siteData = !siteData }
    }
    Hint("What pages saved on the device themselves — drafts, settings, offline copies.")
    MenuRow(Icons.Default.Layers, "Cached files", onClick = { cache = !cache }, toggledTo = !cache) {
        MenuSwitch(checked = cache) { cache = !cache }
    }
    Hint("Only what was quicker to keep. Sites will load slower once.")

    Divider()

    val anything = history || cookies || cache || siteData
    if (anything) {
        DangerRow(
            label = "Clear browsing data",
            confirmLabel = "Tap again to clear",
            onConfirm = {
                onClear(history, cookies, cache, siteData)
                cleared = true
            },
        )
    }
    if (cleared) Hint("Cleared. Open tabs and saved passwords were left alone.")
}

/** A drill-down row's current value, followed by the chevron into its pane. */
@Composable
internal fun RowValue(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            color = InkMuted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 6.dp),
        )
        Chevron()
    }
}

/** The one-line explanation under a setting. */
@Composable
internal fun Hint(text: String) {
    Text(
        text = text,
        color = InkMuted,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** The label over a segmented picker. */
@Composable
internal fun PickerLabel(text: String) {
    Text(
        text = text,
        color = InkStrong,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
    )
}

/** The app's one segmented picker, accented to match everything else. */
@Composable
internal fun <T> Picker(entries: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        entries.forEachIndexed { index, entry ->
            SegmentedButton(
                selected = entry == selected,
                onClick = { onSelect(entry) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = AccentColor,
                    activeContentColor = Color.White,
                    activeBorderColor = AccentColor,
                ),
            ) {
                Text(label(entry))
            }
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = HairLine, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
}

@Composable
private fun DefaultBrowserRow() {
    val context = LocalContext.current
    // Checked fresh each time this pane enters composition — the user's
    // most likely route to actually changing this is leaving via the row
    // below, to the system Settings app, then coming straight back here.
    val isDefaultBrowser = remember {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val resolved = context.packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
        resolved?.activityInfo?.packageName == context.packageName
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.startActivity(Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Public, contentDescription = null, tint = Ink, modifier = Modifier.size(24.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                text = if (isDefaultBrowser) "This is your default browser" else "Not your default browser",
                color = InkStrong,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (!isDefaultBrowser) {
                Text(text = "Tap to open system settings", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (isDefaultBrowser) {
            Icon(Icons.Default.Check, contentDescription = null, tint = AccentColor, modifier = Modifier.size(24.dp))
        } else {
            Chevron()
        }
    }
}

/** Every row in the arrangement is this tall, which is what makes a drag's
 *  travel divisible into rows — the section header included. */
private val ENGINE_ROW = 56.dp

@Composable
private fun SearchEnginePane(
    current: SearchEngine,
    engines: List<SearchEngine>,
    disabled: Set<String>,
    suggestionsEnabled: Boolean,
    onSelect: (SearchEngine) -> Unit,
    onArrange: (order: List<String>, disabled: Set<String>) -> Unit,
    onAddCustom: (label: String, url: String) -> Boolean,
    onRemoveCustom: (SearchEngine) -> Unit,
    onToggleSuggestions: () -> Unit,
    onBack: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }

    PaneHeader("Search engine", onBack)

    SectionHeader("Search engine")
    EngineArrangement(
        engines = engines,
        disabled = disabled,
        current = current,
        onSelect = onSelect,
        onArrange = onArrange,
        onRemove = onRemoveCustom,
    )
    Hint(
        "Drag by the handle to reorder, or across the line to stop offering an engine. " +
            "Swipe a row away to remove it."
    )

    MenuRow(Icons.Default.Add, "Add search engine", onClick = { adding = true }) {}

    HorizontalDivider(color = HairLine, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

    SectionHeader("Suggestions")
    MenuRow(Icons.Default.Search, "Search suggestions", onClick = onToggleSuggestions, toggledTo = !suggestionsEnabled) {
        MenuSwitch(checked = suggestionsEnabled, onToggle = onToggleSuggestions)
    }
    Hint("Suggestions from ${current.label} as you type in the address bar.")

    if (adding) {
        AddEngineDialog(
            onAdd = onAddCustom,
            onDismiss = { adding = false },
        )
    }
}

/**
 * The engine list as one column the user arranges by hand: the engines on
 * offer, a line, and the ones that aren't.
 *
 * There is no switch on a row any more, and that is the point — the two
 * things a row can be (offered, not offered) are a PLACE in this list rather
 * than a property of it, so ordering the picker and deciding what is in it are
 * the same gesture. Dragging is by the handle only, which is what lets a row
 * also be swiped away without the two detectors having to guess at each
 * other; the pane it sits in scrolls, and a drag that starts on a dedicated
 * grip cannot be mistaken for one.
 *
 * Every row — the "Not offered" header included — is [ENGINE_ROW] tall, so a
 * drag's travel divides into whole rows and the header is just another slot a
 * row can be dropped past.
 */
@Composable
private fun EngineArrangement(
    engines: List<SearchEngine>,
    disabled: Set<String>,
    current: SearchEngine,
    onSelect: (SearchEngine) -> Unit,
    onArrange: (order: List<String>, disabled: Set<String>) -> Unit,
    onRemove: (SearchEngine) -> Unit,
) {
    val haptics = rememberHaptics()
    val rowPx = with(LocalDensity.current) { ENGINE_ROW.toPx() }
    // Active, the line, then the rest. Rebuilt from the arrangement itself
    // rather than kept alongside it, so a drop is committed by publishing the
    // new arrangement and reading it back — there is no second copy to drift.
    val slots: List<SearchEngine?> = remember(engines, disabled) {
        engines.filter { it.id !in disabled } + listOf(null) + engines.filter { it.id in disabled }
    }

    var dragging by remember { mutableStateOf<String?>(null) }
    // A plain float state, read in the DRAW phase (see the graphicsLayer
    // below) rather than in composition, so a drag doesn't recompose seven
    // rows per frame.
    val dragY = remember { mutableFloatStateOf(0f) }
    val from = slots.indexOfFirst { it?.id == dragging }
    // Where the dragged row would land if it were dropped now, in whole rows
    // of travel — every slot is the same height, so this is division. Derived,
    // because it changes a handful of times per drag while the float behind
    // it changes every frame, and it is what the OTHER rows are laid out
    // against.
    val to by remember(slots, from, rowPx) {
        derivedStateOf {
            if (from < 0) -1
            else (from + (dragY.floatValue / rowPx).roundToInt()).coerceIn(0, slots.lastIndex)
        }
    }
    // One tick per row crossed, the same rate the tab switcher ticks as cards
    // pass under the finger.
    LaunchedEffect(to) { if (to >= 0 && to != from) haptics.tick() }

    Column {
        slots.forEachIndexed { index, engine ->
            val held = engine != null && engine.id == dragging
            // Rows between where the drag started and where it would land
            // step out of the way by exactly one row; the held row is drawn
            // at the finger.
            val shift = when {
                held || from < 0 -> 0f
                index in (from + 1)..to -> -rowPx
                index in to..<from -> rowPx
                else -> 0f
            }
            // Keyed on the arrangement itself, so a COMMITTED drop starts
            // from rest. The displaced rows carry a full row of offset while
            // a drag is in flight; the drop then lays them out one row along
            // for real, and an animation retained across that reorder would
            // animate that same offset away from a position that is already
            // correct — the row jumping a slot and sliding back, which is the
            // stray slide-up an engine appeared to make on its way above the
            // line. A fresh Animatable at 0 lands it where it belongs.
            val settle = remember(slots) { Animatable(0f) }
            LaunchedEffect(slots, shift) { settle.animateTo(shift, tween(120)) }

            // Keyed by the engine, not by the slot: these rows carry state
            // (a swipe's offset above all) and the list they live in
            // reorders under them, so an unkeyed row hands its swiped-away
            // position to whichever engine slides into that index — which
            // showed as an empty red row where a perfectly ordinary engine
            // should be.
            key(engine?.id ?: "line") {
            Box(
                Modifier
                    .zIndex(if (held) 1f else 0f)
                    // The held row follows the finger with no animation of
                    // its own; the rows it displaces step aside over 120ms.
                    .graphicsLayer { translationY = if (held) dragY.floatValue else settle.value }
                    .height(ENGINE_ROW)
                    .fillMaxWidth(),
            ) {
                if (engine == null) {
                    DisabledDivider()
                } else {
                    EngineRow(
                        engine = engine,
                        isDefault = engine.id == current.id,
                        offered = engine.id !in disabled,
                        held = held,
                        onClick = {
                            if (engine.id in disabled) {
                                // Tapping a row below the line puts it back
                                // on offer — the drag is the precise way to
                                // do it, this is the quick one.
                                onArrange(
                                    (engines.filter { it.id !in disabled } + engine +
                                        engines.filter { it.id in disabled && it.id != engine.id })
                                        .map { it.id },
                                    disabled - engine.id,
                                )
                            } else {
                                onSelect(engine)
                            }
                        },
                        onRemove = {
                            haptics.confirm()
                            if (engine.isCustom) {
                                onRemove(engine)
                            } else {
                                // A built-in cannot be deleted — there is
                                // nothing to delete it FROM — so the swipe
                                // means the strongest thing it can: stop
                                // offering it. Which is where the row goes.
                                onArrange(engines.map { it.id }, disabled + engine.id)
                            }
                        },
                        dragModifier = Modifier.pointerInput(slots, rowPx) {
                            // Everything this gesture decides with is worked
                            // out INSIDE it: a `pointerInput` block keeps
                            // whatever it closed over until one of its keys
                            // changes, so a `from`/`to` read off the
                            // composition would still be the values from
                            // before this drag began (-1, i.e. no drop would
                            // ever commit — the same trap the engine menu's
                            // own latch exists for). `slots` is a key
                            // precisely so the one thing it does close over
                            // is current.
                            var origin = -1
                            var travel = 0f
                            fun landing() =
                                (origin + (travel / rowPx).roundToInt()).coerceIn(0, slots.lastIndex)
                            detectDragGestures(
                                onDragStart = {
                                    haptics.gestureStart()
                                    origin = slots.indexOfFirst { it?.id == engine.id }
                                    travel = 0f
                                    dragging = engine.id
                                    dragY.floatValue = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    travel += amount.y
                                    dragY.floatValue = travel
                                },
                                onDragEnd = {
                                    haptics.gestureEnd()
                                    commitDrag(slots, origin, landing())?.let { (order, off) ->
                                        onArrange(order, off)
                                    }
                                    dragging = null
                                    dragY.floatValue = 0f
                                },
                                onDragCancel = {
                                    dragging = null
                                    dragY.floatValue = 0f
                                },
                            )
                        },
                    )
                }
            }
            }
        }
    }
}

/**
 * The arrangement a drop would produce, or null if it would leave nothing on
 * offer — which is refused here rather than at the ViewModel, so the row
 * simply snaps back instead of appearing to move and then not having.
 */
private fun commitDrag(
    slots: List<SearchEngine?>,
    from: Int,
    to: Int,
): Pair<List<String>, Set<String>>? {
    if (from < 0 || to < 0 || from == to) return null
    val moved = slots.toMutableList()
    moved.add(to, moved.removeAt(from))
    val line = moved.indexOfFirst { it == null }
    val active = moved.take(line).filterNotNull()
    val off = moved.drop(line + 1).filterNotNull()
    if (active.isEmpty()) return null
    return (active + off).map { it.id } to off.map { it.id }.toSet()
}

/** The line the arrangement is split by: below it, an engine isn't offered. */
@Composable
private fun DisabledDivider() {
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Not offered",
            color = InkMuted,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(end = 12.dp),
        )
        HorizontalDivider(color = HairLine, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EngineRow(
    engine: SearchEngine,
    isDefault: Boolean,
    offered: Boolean,
    held: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    dragModifier: Modifier,
) {
    // Latched, because confirmValueChange can be consulted more than once for
    // one settle — the same shape NewTabSheet's history rows use. Reset when
    // the row changes section, so an engine put back on offer can be swiped
    // off it again.
    var dismissed by remember(engine.id, offered) { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart && !dismissed) {
                dismissed = true
                onRemove()
                // A deleted engine leaves the list, so its row can stay where
                // the swipe left it. A built-in doesn't — it only moves below
                // the line — so the swipe is REFUSED and the row snaps back,
                // and what the eye follows is the row travelling to the other
                // section rather than a gap where it used to be.
                engine.isCustom
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        // End-to-start only: a start-to-end swipe fights the system back
        // gesture at the screen edge, and there is no second action for it.
        enableDismissFromStartToEnd = false,
        // Nothing to remove a row for while it is being dragged somewhere.
        gesturesEnabled = !held,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    if (engine.isCustom) Icons.Default.Delete else Icons.Default.VisibilityOff,
                    contentDescription = if (engine.isCustom) "Delete" else "Stop offering",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    ) {
        Row(
            Modifier
                .fillMaxSize()
                // Opaque, so the swipe backdrop shows only where the row has
                // actually slid away — and so a held row covers the ones it
                // is being dragged over.
                .background(BarBg)
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EngineIcon(engine = engine, size = 24.dp, corner = 7.dp, letterSize = 12.sp)
            Text(
                engine.label,
                // A row below the line is still readable, just plainly not
                // in play.
                color = if (offered) Ink else InkMuted,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            )
            if (isDefault) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Default",
                    tint = InkStrong,
                    modifier = Modifier.size(24.dp),
                )
            }
            Box(
                Modifier
                    .size(48.dp)
                    .then(dragModifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "Reorder ${engine.label}",
                    tint = InkMuted,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * The form for an engine the app doesn't ship with, as a dialog rather than a
 * section that unfolds inside the list: it is two text fields and a decision,
 * on a screen that is otherwise a list of rows, and it takes the keyboard —
 * which in this window (`adjustNothing`) covers the bottom of a pane that
 * cannot scroll out from under it. A dialog brings its own window and its own
 * ground, and lands above the keyboard on its own.
 *
 * What it asks for is a name and the site's own search URL with the query cut
 * out of it: `%s` marks the hole, and a URL pasted straight out of the address
 * bar after a search usually needs nothing but its query replaced.
 */
@Composable
private fun AddEngineDialog(
    onAdd: (label: String, url: String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    // Set only by a rejected save, cleared on the next keystroke, so it
    // describes the fields as they are rather than as they were.
    var rejected by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BarBg,
        shape = specialCorner(24.dp),
        title = { Text("Add search engine", color = InkStrong) },
        text = {
            Column {
                EngineField(
                    value = label,
                    hint = "Name",
                    modifier = Modifier.focusRequester(focus),
                    onValueChange = { label = it; rejected = false },
                )
                Spacer(Modifier.height(8.dp))
                EngineField(
                    value = url,
                    hint = "https://example.com/?q=%s",
                    onValueChange = { url = it; rejected = false },
                )
                if (rejected) {
                    Text(
                        "That needs a name and a real search URL, with %s where the query goes.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (onAdd(label, url)) {
                        haptics.confirm()
                        onDismiss()
                    } else {
                        haptics.reject()
                        rejected = true
                    }
                },
            ) {
                Text("Add", color = AccentColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = InkMuted)
            }
        },
    )
}

@Composable
private fun EngineField(
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = InkStrong),
        cursorBrush = SolidColor(InkStrong),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrect = false,
            keyboardType = KeyboardType.Uri,
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(specialCorner(14.dp))
            .background(FieldBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    // One line, like the field itself: a hint that wraps
                    // makes the row twice as tall as what it is a hint for.
                    Text(
                        hint,
                        color = InkMuted,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun BehaviorPane(
    autoFocusNewTabKeyboard: Boolean,
    openNewTabSheetOnLaunch: Boolean,
    newTabHistorySort: HistorySort,
    newTabPlacement: NewTabPlacement,
    tabManagerMode: TabManagerMode,
    linkStripperEnabled: Boolean,
    openExternalLinksInOverlay: Boolean,
    openLinksInApps: Boolean,
    linkPreviewEnabled: Boolean,
    keepPrivateTabs: Boolean,
    onToggleAutoFocusNewTabKeyboard: () -> Unit,
    onToggleOpenNewTabSheetOnLaunch: () -> Unit,
    onSelectNewTabHistorySort: (HistorySort) -> Unit,
    onSelectNewTabPlacement: (NewTabPlacement) -> Unit,
    onSelectTabManagerMode: (TabManagerMode) -> Unit,
    onToggleLinkStripper: () -> Unit,
    onToggleOpenExternalLinksInOverlay: () -> Unit,
    onToggleOpenLinksInApps: () -> Unit,
    onToggleLinkPreview: () -> Unit,
    onToggleKeepPrivateTabs: () -> Unit,
    onBack: () -> Unit,
) {
    PaneHeader("Behavior", onBack)

    // Three sections, one per thing the settings are about: tabs, links, and
    // private mode. Tabs was two headers ("New tab" and, over on Appearance,
    // "Tab manager") for what is one subject — where a tab comes from, where
    // it lands, and how you look at the lot of them — and the placement
    // picker sat under Links because a link is what usually opens a tab in
    // the background, which is the trigger rather than the subject.
    //
    // Ordered within each section by how often the setting is likely to be
    // reached for: what happens when you open a tab first, then where it
    // goes, then the manager, which is picked once.
    SectionHeader("Tabs")
    MenuRow(Icons.Default.Keyboard, "Open keyboard on new tab", onClick = onToggleAutoFocusNewTabKeyboard, toggledTo = !autoFocusNewTabKeyboard) {
        MenuSwitch(checked = autoFocusNewTabKeyboard, onToggle = onToggleAutoFocusNewTabKeyboard)
    }
    Hint("Focuses the search field so you can type straight away.")
    MenuRow(Icons.AutoMirrored.Filled.OpenInNew, "New tab on launch", onClick = onToggleOpenNewTabSheetOnLaunch, toggledTo = !openNewTabSheetOnLaunch) {
        MenuSwitch(checked = openNewTabSheetOnLaunch, onToggle = onToggleOpenNewTabSheetOnLaunch)
    }
    Hint("Starting the browser opens the new tab.")
    PickerLabel("Suggested sites")
    Picker(HistorySort.entries, newTabHistorySort, { it.label }, onSelectNewTabHistorySort)
    Hint("Which sites show up under \"Recently visited\".")
    PickerLabel("Open in new tab")
    Picker(NewTabPlacement.entries, newTabPlacement, { it.label }, onSelectNewTabPlacement)
    Hint(
        if (newTabPlacement == NewTabPlacement.Foreground) "Opening a link in a new tab goes to it."
        else "Opening a link in a new tab leaves you on the page you were reading."
    )
    PickerLabel("Tab manager")
    Picker(TabManagerMode.entries, tabManagerMode, { it.label }, onSelectTabManagerMode)
    Hint(
        when (tabManagerMode) {
            TabManagerMode.Vertical -> "The tab button opens a zoomed carousel of tabs."
            TabManagerMode.Horizontal -> "The tab button opens a scrolling list of tabs."
        }
    )

    Divider()

    SectionHeader("Links")
    MenuRow(Icons.Default.Apps, "External apps", onClick = onToggleOpenLinksInApps, toggledTo = !openLinksInApps) {
        MenuSwitch(checked = openLinksInApps, onToggle = onToggleOpenLinksInApps)
    }
    Hint(
        if (openLinksInApps) "Opens external links in their app."
        else "Opens external links in the browser."
    )
    MenuRow(Icons.Default.Layers, "Quick tab", onClick = onToggleOpenExternalLinksInOverlay, toggledTo = !openExternalLinksInOverlay) {
        MenuSwitch(checked = openExternalLinksInOverlay, onToggle = onToggleOpenExternalLinksInOverlay)
    }
    Hint(
        if (openExternalLinksInOverlay) "Opens in-app links in an overlay."
        else "Opens in-app links in a new tab."
    )
    MenuRow(Icons.Default.Preview, "Quick preview", onClick = onToggleLinkPreview, toggledTo = !linkPreviewEnabled) {
        MenuSwitch(checked = linkPreviewEnabled, onToggle = onToggleLinkPreview)
    }
    Hint(
        if (linkPreviewEnabled) "Holding a link opens it in a card you can read before going there."
        else "Holding a link opens a menu instead."
    )
    MenuRow(Icons.Default.Link, "Link stripper", onClick = onToggleLinkStripper, toggledTo = !linkStripperEnabled) {
        MenuSwitch(checked = linkStripperEnabled, onToggle = onToggleLinkStripper)
    }
    Hint("Strips tracking from links for cleaner sharing.")

    Divider()

    SectionHeader("Private mode")
    MenuRow(Icons.Default.VisibilityOff, "Keep private tabs", onClick = onToggleKeepPrivateTabs, toggledTo = !keepPrivateTabs) {
        MenuSwitch(checked = keepPrivateTabs, onToggle = onToggleKeepPrivateTabs)
    }
    Hint(
        if (keepPrivateTabs) "Leaving private mode parks its tabs. They still end when the browser closes."
        else "Leaving private mode closes everything opened in it."
    )
}

@Composable
private fun GesturesPane(
    pullToRefreshEnabled: Boolean,
    swipeToSwitchTabs: Boolean,
    doubleTapTabsSwitchesTab: Boolean,
    flickToCloseTab: Boolean,
    onTogglePullToRefresh: () -> Unit,
    onToggleSwipeToSwitchTabs: () -> Unit,
    onToggleDoubleTapTabsSwitchesTab: () -> Unit,
    onToggleFlickToCloseTab: () -> Unit,
    onBack: () -> Unit,
) {
    PaneHeader("Gestures", onBack)

    // The page gesture first — it is met on every site — then the three
    // toolbar ones, in the order they come up in ordinary use.
    SectionHeader("Page")
    MenuRow(Icons.Default.Refresh, "Pull to refresh", onClick = onTogglePullToRefresh, toggledTo = !pullToRefreshEnabled) {
        MenuSwitch(checked = pullToRefreshEnabled, onToggle = onTogglePullToRefresh)
    }

    Divider()

    SectionHeader("Toolbar")
    MenuRow(Icons.Default.SwipeLeft, "Swipe to switch", onClick = onToggleSwipeToSwitchTabs, toggledTo = !swipeToSwitchTabs) {
        MenuSwitch(checked = swipeToSwitchTabs, onToggle = onToggleSwipeToSwitchTabs)
    }
    Hint("Swipe from the navbar corners to go between tabs.")
    MenuRow(Icons.Default.SwapHoriz, "Double tap to switch", onClick = onToggleDoubleTapTabsSwitchesTab, toggledTo = !doubleTapTabsSwitchesTab) {
        MenuSwitch(checked = doubleTapTabsSwitchesTab, onToggle = onToggleDoubleTapTabsSwitchesTab)
    }
    Hint("Double tap the tab button to move to the previous tab.")
    MenuRow(Icons.Default.NorthEast, "Flick to close", onClick = onToggleFlickToCloseTab, toggledTo = !flickToCloseTab) {
        MenuSwitch(checked = flickToCloseTab, onToggle = onToggleFlickToCloseTab)
    }
    Hint("Dragging from the tab button past the center of the screen closes the tab.")
}

@Composable
private fun AppearancePane(
    themeMode: ThemeMode,
    pageDarkMode: PageDarkMode,
    accentTheme: AccentTheme,
    specialTheme: SpecialTheme,
    pageZoom: Int,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectPageDarkMode: (PageDarkMode) -> Unit,
    onSelectAccent: (AccentTheme) -> Unit,
    onSelectSpecialTheme: (SpecialTheme) -> Unit,
    onSetZoomStep: (Int) -> Unit,
    pageLens: Boolean,
    onTogglePageLens: () -> Unit,
    translucentSheets: Boolean,
    onToggleTranslucentSheets: () -> Unit,
    translucency: Float,
    onSetTranslucency: (Float) -> Unit,
    onBack: () -> Unit,
) {
    PaneHeader("Appearance", onBack)

    // Two sections, and the split is what the setting is ABOUT rather than
    // what it happens to change: the app's own look first, then how a page
    // is rendered inside it. It used to be five accent-coloured headers —
    // Theme, Accent, Text size, Tab manager, Special themes — which read as
    // five unrelated screens stacked on one, and put the app's light/dark
    // switch next to the page's, and the accent two headers away from the
    // look it colours. The three controls that are all one decision ("what
    // the app looks like") are now one section under sub-labels, the two
    // that are about the page are the other, and the tab manager's layout,
    // which is neither, moved to Behavior beside the rest of the tab
    // settings.
    SectionHeader("App")
    PickerLabel("Theme")
    Picker(ThemeMode.entries, themeMode, { it.label }, onSelectThemeMode)
    Hint("Light, dark, or whatever the system is set to.")

    // In force under every look except 98, whose sixteen system colours have
    // no role a chosen one could take (see `setAccentTheme`): the TUI and
    // Nothing keep their own greys and take the pick through their accent
    // roles alone, so a swatch tapped under either of them is a swatch that
    // shows up on screen.
    //
    // Under 98 nothing here is ticked, and that is the same rule as before:
    // the accent the user last picked is still stored underneath, but a
    // ticked swatch claims that colour is what the app is wearing, and under
    // 98 it is not — the row would be pointing at a colour nowhere on
    // screen. So the tick goes out with the palette it describes, and the
    // first tap puts both back by dropping the look.
    val accentInForce = specialTheme != SpecialTheme.Ninety8
    PickerLabel("Accent")
    DynamicAccentRow(
        selected = accentInForce && accentTheme == AccentTheme.Dynamic,
        onClick = { onSelectAccent(AccentTheme.Dynamic) },
    )
    AccentRow(
        accentTheme = accentTheme.takeIf { accentInForce },
        onSelectAccent = onSelectAccent,
    )

    // A whole look rather than another swatch — it replaces the shapes, the
    // type and the greys above — so it is picked here, below the accent, and
    // not among the dots. The look picked here is in force on this screen
    // too, this section included.
    //
    // Default IS one of the targets now, where it used to be reached only by
    // tapping an accent. That way out went with the accent's new job: a
    // colour tapped under the TUI or Nothing now recolours the look instead
    // of leaving it, so the only gesture that ever left one had to become a
    // cell like the others. Which it should have been anyway — "the app's
    // own look" is a look, and a picker of looks that cannot name the one
    // most people are wearing is a picker with a hole in it.
    //
    // No hint, unlike every other control on this screen: the grid shows each
    // look as its own glyph and applies it the moment it is tapped, so a
    // paragraph describing what the user is already looking at is a
    // paragraph nobody reads — and the one sentence that used to be here,
    // naming the way out, is now a target with a label on it.
    PickerLabel("Special themes")
    SpecialThemeGrid(specialTheme, onSelectSpecialTheme)

    // Frosted-glass sheets over the page. Only the Default and Nothing looks
    // take it — TUI and 98 are opaque by nature and Aero is glass already —
    // so under the others the switch is dead rather than hidden, with the
    // reason under it.
    val translucencyApplies = specialTheme == SpecialTheme.Default || specialTheme == SpecialTheme.Nothing
    val translucencyEnabled = translucencyApplies && aeroRefractionSupported
    MenuRow(
        Icons.Default.BlurOn,
        "Translucency",
        onClick = onToggleTranslucentSheets,
        enabled = translucencyEnabled,
        toggledTo = !translucentSheets,
    ) {
        MenuSwitch(
            checked = translucentSheets && translucencyEnabled,
            enabled = translucencyEnabled,
            onToggle = onToggleTranslucentSheets,
        )
    }
    // How solid the glass is, canvas and elements together, while it is on.
    if (translucentSheets && translucencyEnabled) {
        OpacityRow(value = translucency, onChange = onSetTranslucency)
    }
    // No description, as with CRT: the one line kept is the reason the switch
    // is dead where it cannot apply.
    when {
        !aeroRefractionSupported -> Hint("Needs Android 13 or newer.")
        !translucencyApplies -> Hint("Only for the Default and Nothing themes.")
    }

    Divider()

    // The page's own rendering. Both of these are done to somebody else's
    // document rather than to our chrome, which is why the page's darkening
    // is here and not beside the app's light/dark switch: they read as one
    // decision and they are not — one repaints the toolbar, the other
    // overrides a site.
    SectionHeader("Web pages")
    PickerLabel("Dark mode")
    Picker(PageDarkMode.entries, pageDarkMode, { it.label }, onSelectPageDarkMode)
    Hint("Sites with a dark theme use it, sites without are forced to be dark.")
    TextSizeRow(zoom = pageZoom, onSetStep = onSetZoomStep)
    Hint("Scales text on every page to $pageZoom%. Sites that lay themselves out for the screen reflow to it.")

    Divider()

    // Last on the screen and under its own header: things that change how a
    // page is drawn but are not yet something to recommend. See PageLens.
    SectionHeader("Experimental")
    MenuRow(
        Icons.Default.Vignette,
        "CRT",
        onClick = onTogglePageLens,
        enabled = pageLensSupported,
        toggledTo = !pageLens,
    ) {
        MenuSwitch(checked = pageLens && pageLensSupported, enabled = pageLensSupported, onToggle = onTogglePageLens)
    }
    // No description: the name says it, and the switch shows it on the page
    // the moment it is thrown. The one line kept is the reason the switch is
    // dead where it cannot run at all.
    if (!pageLensSupported) Hint("Needs Android 13 or newer.")
}

/**
 * Page text scale, on one row: the label on the left, a slider over the rungs
 * of [ZOOM_STEPS] taking the rest of the width. This is a preference rather
 * than something done to the page in front of you — it applies to every tab
 * and survives a relaunch — which is why it lives here and not in the menu,
 * where its placement among the page tiles implied a scope it never had.
 *
 * Snapped to the rungs (`steps`), not continuous: [setZoomStep] takes an
 * INDEX, so a thumb dropped between two ticks cannot produce a percentage the
 * ladder doesn't have, and the ticks show where it will land before the
 * finger commits. That is also what replaces the old readout-as-reset: 100%
 * is a tick like any other and can simply be slid back to, so there is
 * nothing left for a tap on the number to undo. The current value is in the
 * hint under the row, where every other setting here explains itself.
 */
@Composable
private fun TextSizeRow(zoom: Int, onSetStep: (Int) -> Unit) {
    val haptics = rememberHaptics()
    // Derived from the value rather than held as its own state, so a restore
    // moves the thumb without the two being able to disagree about where it is.
    val index = ZOOM_STEPS.indexOfFirst { it >= zoom }.let {
        if (it < 0) ZOOM_STEPS.lastIndex else it
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Text size",
            color = InkStrong,
            style = MaterialTheme.typography.bodyLarge,
        )
        // A rung passed under the finger is a tick, the same one the tab
        // switcher gives per card passed: it is a notch on a ladder, not a
        // state being set. Fired on the CHANGE, not on every callback — a drag
        // reports continuously and most of those land on the rung the thumb is
        // already on.
        Slider(
            value = index.toFloat(),
            onValueChange = {
                val next = it.roundToInt()
                if (next != index) {
                    haptics.tick()
                    onSetStep(next)
                }
            },
            valueRange = 0f..ZOOM_STEPS.lastIndex.toFloat(),
            steps = ZOOM_STEPS.size - 2,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        )
    }
}

/**
 * The Translucency slider: continuous, nearly transparent at the left and
 * nearly solid at the right, laid out like [TextSizeRow]. It moves the frosted
 * canvas and the elements on it together (see `frostCanvasAlpha`).
 */
@Composable
private fun OpacityRow(value: Float, onChange: (Float) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Opacity",
            color = InkStrong,
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        )
    }
}

/**
 * The fixed swatches as one row scrolled sideways rather than a grid: a grid
 * of dots with labels of different lengths under them never lines up, and a
 * row of colours is the thing being scanned anyway. The end padding is on the
 * CONTENT, not the container, or the last swatch is clipped against the edge
 * instead of scrolling clear of it.
 *
 * [accentTheme] is null when 98 has taken the palette over: the accent still
 * exists underneath, but none of these swatches is what the app is wearing,
 * so none of them is ticked.
 *
 * Graphite is a swatch like the other eight, under every look: a grey accent
 * is a choice — the one a terminal makes, and the one Nothing's whole
 * premise is — rather than an abstention, so it tints the special themes
 * too instead of standing for "keep your own colour".
 */
@Composable
private fun AccentRow(
    accentTheme: AccentTheme?,
    onSelectAccent: (AccentTheme) -> Unit,
) {
    val swatches = remember { AccentTheme.entries.filter { it != AccentTheme.Dynamic } }
    PeekingRow(count = swatches.size, nominalCell = 72.dp, modifier = Modifier.padding(vertical = 8.dp)) { cell ->
        swatches.forEach { theme ->
            AccentSwatch(
                color = theme.color(),
                label = theme.label,
                selected = theme == accentTheme,
                onClick = { onSelectAccent(theme) },
                modifier = Modifier.width(cell),
            )
        }
    }
}

/** Gutter before the first cell and after the last in a [PeekingRow]. */
private val PEEK_ROW_GUTTER = 12.dp

/**
 * A sideways-scrolled row of fixed-width cells, sized so that when the cells
 * do not all fit, the last one on screen is cut in HALF — the visible hint
 * that the row scrolls. The width is worked out from the screen alone, never
 * from what the cells contain, so switching look (a different face, other
 * label widths) cannot move a single target.
 */
@Composable
private fun PeekingRow(
    count: Int,
    nominalCell: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (cell: Dp) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val avail = maxWidth - PEEK_ROW_GUTTER
        val cell = if (nominalCell * count <= avail) {
            nominalCell
        } else {
            // Whole cells that fit with half of one more beside them.
            val whole = (avail / nominalCell - 0.5f).roundToInt().coerceIn(1, count - 1)
            avail / (whole + 0.5f)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.width(PEEK_ROW_GUTTER))
            content(cell)
            Spacer(Modifier.width(PEEK_ROW_GUTTER))
        }
    }
}

/**
 * A cell's name, in a box of fixed height and a style that is not a label
 * face: under Nothing `labelLarge` is the mono face, which is cased up, and a
 * face's own line height would move every target when the look changes.
 */
@Composable
private fun CellLabel(text: String, selected: Boolean) {
    Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = if (selected) InkStrong else InkMuted,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DynamicAccentRow(selected: Boolean, onClick: () -> Unit) {
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val previewColor = if (dynamicAvailable) {
        remember(isDark) {
            (if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
        }
    } else {
        InkMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = dynamicAvailable, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(SpecialCircle)
                .background(previewColor),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(text = AccentTheme.Dynamic.label, color = InkStrong, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (dynamicAvailable) "Matches your wallpaper" else "Requires Android 12 or newer",
                color = InkMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (selected && dynamicAvailable) {
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = AccentColor, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun AccentSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        // The ripple takes the whole cell, not just the dot, so the taps
        // land where the grid says the target is.
        modifier = modifier
            .clip(specialCorner(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(SpecialCircle)
                .background(color)
                .border(width = if (selected) 2.dp else 0.dp, color = InkStrong, shape = SpecialCircle),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                // Not a fixed white: the swatches are bold now, and a white
                // check on a full-saturation yellow is a tick nobody can
                // see. Whichever end the dot is nearer loses.
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = if (color.luminance() > 0.45f) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        CellLabel(label, selected)
    }
}

/**
 * The special themes, as a grid of big round targets four to a row rather
 * than as the segmented picker every other setting on this screen uses.
 *
 * A segmented toggle is right for a setting whose options are WORDS the user
 * reads — three modes of one thing. These are not that: each one is a
 * different interface, and what the user is picking between is closer to a
 * wallpaper than to a mode. So they are given the accent swatches' shape (a
 * round target with its name under it, on a cell whose ripple is the whole
 * cell) at a size that can carry a glyph, and the glyph does the work the
 * label cannot — "TUI" and "Nothing" say nothing to someone who has not seen
 * either.
 *
 * One sideways-scrolled row, like the accents above it ([PeekingRow]), in a
 * fixed order that is the picker's own rather than the enum's.
 *
 * Default is the first cell, and it is a cell like the others: it is the
 * look the app ships in rather than the absence of a look, and now that an
 * accent tapped under the TUI or Nothing recolours them instead of leaving
 * them, it is also the only way back. First rather than last because it is
 * where every one of these journeys started.
 */
@Composable
private fun SpecialThemeGrid(selected: SpecialTheme, onSelect: (SpecialTheme) -> Unit) {
    PeekingRow(
        count = SPECIAL_THEME_ORDER.size,
        nominalCell = 88.dp,
        modifier = Modifier.padding(vertical = 4.dp),
    ) { cell ->
        SPECIAL_THEME_ORDER.forEach { theme ->
            SpecialThemeCell(
                theme = theme,
                selected = theme == selected,
                onClick = { onSelect(theme) },
                modifier = Modifier.width(cell),
            )
        }
    }
}

private val SPECIAL_THEME_ORDER = listOf(
    SpecialTheme.Default,
    SpecialTheme.Nothing,
    SpecialTheme.Tui,
    SpecialTheme.Aero,
    SpecialTheme.Ninety8,
)

/**
 * One theme's target: a filled circle carrying its glyph, its name under it.
 *
 * The selected one is drawn in the accent and ringed, exactly as an accent
 * swatch is — this screen already teaches that shape one section above, and
 * a second selection idiom for the same kind of choice would be a second
 * thing to learn.
 */
@Composable
private fun SpecialThemeCell(
    theme: SpecialTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(specialCorner(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(SpecialCircle)
                .background(if (selected) AccentColor else FieldBg)
                .border(width = if (selected) 2.dp else 0.dp, color = InkStrong, shape = SpecialCircle),
            contentAlignment = Alignment.Center,
        ) {
            // Material's `Icon`, not the theme-aware one every other call
            // site here uses: these five cells are a row of CHOICES, and a
            // choice has to be drawn the same way as the ones beside it or
            // the picker is showing the active look rather than the looks on
            // offer. (Only the TUI could reach this — Nothing and 98 turn
            // themselves off for Settings' subtree — which is exactly the
            // asymmetry that made it worth pinning.)
            MaterialIcon(
                imageVector = theme.icon(),
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else Ink,
                modifier = Modifier.size(28.dp),
            )
        }
        CellLabel(theme.label, selected)
    }
}

/**
 * What each look looks like in one glyph. Kept here rather than on the enum
 * for the reason `core/` has no Compose dependency at all: an ImageVector on
 * `SpecialTheme` would drag Compose into the model layer.
 */
private fun SpecialTheme.icon(): ImageVector = when (this) {
    // The app's own look: a palette, which is exactly what this look is —
    // the one whose colour comes from the accent row above and nowhere else.
    SpecialTheme.Default -> Icons.Outlined.Palette
    SpecialTheme.Tui -> Icons.Outlined.Terminal
    // A grid of dots — the one glyph in the set that is literally what the
    // theme's type is made of.
    SpecialTheme.Nothing -> Icons.Outlined.BlurOn
    // A CRT, which is what "desktop" meant in 1998 — and under the theme
    // itself this is the one glyph in `Ninety8Icons` that draws the machine
    // the whole look came off.
    SpecialTheme.Ninety8 -> Icons.Outlined.DesktopWindows
    // A drop. Every other cell in this row names a machine or a material the
    // look came off; this one names what the look is MADE of, which for
    // Frutiger Aero is water — the wallpaper of the whole era was a bead of
    // it on a leaf against a blue sky.
    SpecialTheme.Aero -> Icons.Outlined.WaterDrop
}

@Composable
internal fun SectionHeader(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(
        text = text,
        color = AccentColor,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
