package com.yuku.browser.ui

import com.yuku.browser.ui.theme.tuiSoftOutlineIf
import androidx.compose.foundation.border
import com.yuku.browser.ui.theme.tuiBloomIf
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import com.yuku.browser.ui.theme.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import com.yuku.browser.ui.theme.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuku.browser.core.HistoryEntry
import com.yuku.browser.core.HistorySort
import com.yuku.browser.core.SearchEngine
import com.yuku.browser.core.SearchSuggestions
import com.yuku.browser.core.UrlUtils
import com.yuku.browser.ui.theme.BarBg
import com.yuku.browser.ui.theme.Bevel
import com.yuku.browser.ui.theme.FieldBg
import com.yuku.browser.ui.theme.FROSTED_ELEMENT_ALPHA
import com.yuku.browser.ui.theme.frostedIf
import com.yuku.browser.ui.theme.LocalNinety8
import com.yuku.browser.ui.theme.Glassy
import com.yuku.browser.ui.theme.aeroGlassIf
import com.yuku.browser.ui.theme.aeroGlareIf
import com.yuku.browser.ui.theme.aeroPopIf
import com.yuku.browser.ui.theme.AeroPop
import com.yuku.browser.ui.theme.bevel98If
import com.yuku.browser.ui.theme.InkMuted
import com.yuku.browser.ui.theme.InkStrong
import com.yuku.browser.ui.theme.AccentColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import com.yuku.browser.ui.theme.specialCorner
import com.yuku.browser.ui.theme.SpecialCircle

/** The favicon button, and the size of its touch target in the pill. */
private val ENGINE_BUTTON = 32.dp
private val ENGINE_ICON = 22.dp
/** One engine in the strip: the touch target, and the icon drawn inside it. */
private val ENGINE_STRIP_ITEM = 40.dp
private val ENGINE_STRIP_ICON = 30.dp
/** Centre to centre, and the least it may be squeezed to before icons overlap. */
private val ENGINE_STRIP_STEP = 44.dp
private val ENGINE_STRIP_MIN_STEP = 28.dp
/**
 * Room inside the strip's own window for what hangs outside the strip: an icon
 * sitting on the button at one end, the arrival's carry past the other.
 */
private val ENGINE_STRIP_SLACK = 12.dp
/** Air kept between the strip's far end and the edge of the screen. */
private val ENGINE_STRIP_MARGIN = 10.dp
private const val ENGINE_STRIP_ENTER_MS = 260
private const val ENGINE_STRIP_EXIT_MS = 190
/**
 * The size an icon is drawn at while it is on the button, as a fraction of its
 * size in the strip — the button's own icon size, so the two are the same
 * picture at the moment one hands over to the other.
 */
private const val ENGINE_STRIP_HOME_SCALE = 22f / 30f
/** What the icon under the finger swells to while a drag is over it. */
private const val ENGINE_STRIP_HOVER_SCALE = 1.16f
/**
 * How far the field's own text takes to come back after the last icon: the
 * strip has no ground of its own, so what keeps it legible is the text under
 * it going away, and an edge that simply cut would read as a mask rather than
 * as the strip having room.
 */
/**
 * The ramp is a third of a STEP — of the strip's own pitch, the distance from
 * one icon to the next — rather than a distance of its own. What the eye
 * measures the fade against is the row it sits at the end of, so borrowing
 * that spacing makes the text come back on the strip's rhythm instead of on a
 * number picked next to it, and it follows the step down when a narrow screen
 * shrinks the strip. A third of it because the ramp starts at the last mark's
 * ink: over a whole step the text was still arriving a mark's width later,
 * which reads as the strip holding a space nothing is in.
 */
private const val ENGINE_STRIP_VEIL_FADE_STEPS = 1f / 3f
private val ENGINE_STRIP_VEIL_FADE_MIN = 10.dp
/**
 * How much later each icon starts to arrive, as a fraction of the whole, and
 * how long any one of them takes to fade in. The fan is what separates five
 * icons leaving one point; without it they are a pile that resolves.
 */
private const val ENGINE_STRIP_STAGGER = 0.10f
private const val ENGINE_STRIP_FADE = 0.35f

/**
 * The + sheet. Its private button is the main way into and out of the private
 * SPACE — the browser's whole tab list, and its colour, switch over with it
 * (see BrowserViewModel.setPrivateMode). Which is why this sheet also stops
 * offering history the moment it is on: in there, there is none to offer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTabSheet(
    visible: Boolean,
    // Shared with the menu's address bar: see aeroPopIf.
    omniboxPop: AeroPop? = null,
    // Pages actually visited, latest first — not every page that loaded.
    // See BrowserViewModel.recentlyVisited.
    recentlyVisited: List<HistoryEntry>,
    mostVisited: List<HistoryEntry>,
    // Every page the visit tallies know plus recent history, best first —
    // what a typed query is matched against. See BrowserViewModel.omniboxHistory.
    omniboxHistory: List<HistoryEntry>,
    historySort: HistorySort,
    searchEngine: SearchEngine,
    // The engines offered by the picker: the ones the user has left switched
    // on in Settings, their own among them.
    engines: List<SearchEngine>,
    searchSuggestionsEnabled: Boolean,
    autoFocusKeyboard: Boolean,
    // Non-null when this sheet was reached by tapping the menu's address bar
    // rather than by +: the current tab's URL, which arrives pre-selected in
    // the field and which [onGo] navigates that same tab to. See
    // BrowserScreen's addressEdit — the two sheets are one omnibox in two
    // states, so editing an address is a change of sheet, not a second field.
    editingUrl: String?,
    // Changes on every open (BrowserScreen.openNewTabSheet). The field's
    // state is keyed on it, so an open rebuilds the field DURING that
    // composition rather than an effect-frame later — see below.
    openKey: Int,
    privateMode: Boolean,
    onTogglePrivate: () -> Unit,
    onGo: (url: String) -> Unit,
    onRemoveHistory: (url: String) -> Unit,
    // Translucent sheets: rows let the frosted sheet show through.
    frosted: Boolean = false,
) {
    // Keyed on [openKey], which is what makes the first frame of an open
    // correct. Resetting the field from an effect instead ran a frame late,
    // and this composable never leaves composition (it sits at zero height
    // between opens), so that frame was drawn with whatever the LAST open
    // left in the field: a previous address, matched against history, shown
    // as a one-row "Matching history" list before the real list replaced it.
    //
    // TextFieldValue rather than a bare String because an address arriving
    // from the menu has to be SELECTED, not just filled: one keystroke
    // replaces the whole thing, the way tapping a real omnibox behaves.
    var query by remember(openKey) {
        mutableStateOf(TextFieldValue(editingUrl.orEmpty(), TextRange(0, editingUrl.orEmpty().length)))
    }
    // The engine this ONE query goes to. Keyed on [openKey] like the field
    // itself, so it is a property of the query being typed rather than a
    // second place to change the app's setting: picking Google for one
    // lookup and closing the sheet leaves Settings' own choice untouched,
    // and the next open starts from it again.
    var engine by remember(openKey) { mutableStateOf(searchEngine) }
    // The setting can change under a sheet that is already open (Settings is
    // reachable from the menu), and a stale copy of it would be a query going
    // somewhere the user has just stopped choosing.
    LaunchedEffect(searchEngine) { engine = searchEngine }
    var enginePickerOpen by remember(openKey) { mutableStateOf(false) }
    // Shared by the button that unrolls the strip and the field it unrolls
    // across, which has to fade its own text out from under it.
    val engineStrip = remember { EngineStrip() }
    // Back closes the picker before it means anything else — it is the
    // topmost thing on screen while it is up.
    BackHandler(enabled = enginePickerOpen) { enginePickerOpen = false }

    // Hoisted: this is the app's mode now, not the sheet's own checkbox, so
    // it survives the sheet being closed and is what everything else is
    // already reading.
    val private = privateMode
    val focus = remember { FocusRequester() }
    // Hoisted out of the LazyColumn (which lives inside a branch) so it
    // survives the private-mode swap, and so the effect below can reach it.
    val listState = rememberLazyListState()
    val haptics = rememberHaptics()

    // An address arriving from the menu fills the field but is NOT a search:
    // until the user types, the list stays the ordinary recent/most-visited
    // one, so a sheet reached from the menu is as good for jumping somewhere
    // else as the + sheet is. Matching against the URL that is already on
    // screen would answer with the page the user is standing on and hide
    // everywhere they might go instead — and search suggestions for a full
    // URL are worse still.
    //
    // Compared against the whole field, not a flag: the moment a keystroke
    // makes it something other than the page's own address it is a query
    // again, and deleting back to the original address is a return to the
    // list rather than a search for it.
    val pristineAddress = editingUrl != null && query.text == editingUrl
    val trimmedQuery = if (pristineAddress) "" else query.text.trim()
    // Matched against every page the tallies know as well as recent history
    // ([omniboxHistory], highest-scoring first), since the page someone is
    // half-typing is far more likely one they open constantly than one of the
    // last twenty they happened to load. A page whose address or name STARTS
    // with the query goes above one that merely contains it; within each, the
    // score order stands. Only the top [MAX_HISTORY_MATCHES]: the engine's
    // suggestions go under them and have to stay above the keyboard.
    val matchingHistory = remember(omniboxHistory, trimmedQuery) {
        if (trimmedQuery.isEmpty()) emptyList() else omniboxHistory
            .filter {
                it.title.contains(trimmedQuery, ignoreCase = true) ||
                    it.host.contains(trimmedQuery, ignoreCase = true) ||
                    it.url.contains(trimmedQuery, ignoreCase = true)
            }
            .distinctBy { it.url }
            .sortedByDescending { it.startsWithQuery(trimmedQuery) }
            .take(MAX_HISTORY_MATCHES)
    }

    // Fetched whether or not history matched — the two are shown together,
    // pages first. Keyed on the open, so a new open doesn't start from the
    // last one's list.
    var suggestions by remember(openKey) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(trimmedQuery, searchSuggestionsEnabled) {
        if (trimmedQuery.isEmpty() || !searchSuggestionsEnabled) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(200) // debounce keystrokes before hitting the network
        // Distinct: the rows are keyed by their text.
        suggestions = SearchSuggestions.fetch(trimmedQuery).distinct()
    }

    // The two halves of an open that a `remember` key cannot do: the field
    // takes the keyboard, and the list goes back to its top (a previous
    // open's scroll is not this one's). Both keyed on `visible` as well,
    // since neither means anything while the sheet is down — and the focus
    // request in particular must not run then, or it would pull the keyboard
    // back up over a sheet that is on its way out.
    //
    // The keyboard is asked for FIRST, and that ordering is the whole point:
    // `scrollToItem` suspends until the list it belongs to has been laid
    // out, and in the private space there IS no list — the history is
    // replaced by a caption — so it never returns and everything after it in
    // this coroutine is unreachable. That was the private sheet opening
    // without a keyboard however it was reached, the launcher's "Private tab"
    // shortcut included. The scroll is skipped there for the same reason.
    LaunchedEffect(visible, openKey) {
        if (!visible) return@LaunchedEffect
        // An address edit always takes the keyboard: the user tapped a
        // field, whatever the + sheet's own autofocus setting says.
        if (autoFocusKeyboard || editingUrl != null) focus.requestFocus()
        if (!private) listState.scrollToItem(0)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .height(56.dp)
                .aeroPopIf(omniboxPop, yieldToChildren = true)
                // The one prominent element on this sheet — everything else
                // here is a plain list, so this is what should visually
                // read as "the input" at a glance, not just another row.
                //
                // 98 makes it prominent the other way up: a field there is
                // sunk into the sheet rather than floated over it, and the
                // drop shadow goes with the same reasoning as the menu
                // sheet's address bar (see there).
                .then(
                    // No drop shadow under the TUI either: a tube lights
                    // things, it does not cast shadows under them.
                    if (
                        LocalNinety8.current ||
                        // A shadow under a see-through field shows through it.
                        com.yuku.browser.ui.theme.LocalFrosted.current ||
                        com.yuku.browser.ui.theme.LocalAero.current ||
                        com.yuku.browser.ui.theme.LocalTui.current
                    ) Modifier
                    else Modifier.shadow(elevation = 6.dp, shape = specialCorner(24.dp), clip = false)
                )
                .clip(specialCorner(24.dp))
                .background(if (com.yuku.browser.ui.theme.LocalAero.current) FieldBg.copy(alpha = 0.22f) else FieldBg.frostedIf(FROSTED_ELEMENT_ALPHA))
                .bevel98If(Bevel.Sunken)
                // The menu sheet's address bar and this are the omnibox in
                // two states and are kept identical — see there for why the
                // glass sinks this one rather than raising it.
                .aeroGlassIf(24.dp, Glassy.Field)
                .aeroGlareIf(24.dp)
                // The field's own fill covers the sheet's halo (drawn under
                // content), so it glows on its own, above that fill.
                .tuiBloomIf()
                // The TUI's field takes the quick tiles' outline (see MenuSheet).
                .tuiSoftOutlineIf(AccentColor.copy(alpha = 0.4f))
                .padding(start = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Where the search glyph used to be, and in the same place: the
            // engine's own favicon, which says "this is a search field" and
            // WHICH search in one mark. The button is 32dp around a 22dp
            // icon so its centre lands exactly where the 24dp glyph's did
            // (10 + 16 == 14 + 12) — the pill's shape is unchanged.
            //
            // The private-mode toggle at the other end hides once there's
            // text to make room for a clear button; this one never moves.
            // The engine's own favicon where the search glyph used to be,
            // in the same place (the 32dp button around a 22dp icon centres
            // it exactly where the old 24dp glyph sat: 10 + 16 == 14 + 12),
            // and the strip of engines that unrolls out of it.
            Box(Modifier.aeroPopIf(claim = true)) { EngineButton(
                engine = engine,
                engines = engines,
                strip = engineStrip,
                open = enginePickerOpen,
                onOpenChange = { enginePickerOpen = it },
                onSelect = { engine = it },
            ) }
            BasicTextField(
                value = query,
                onValueChange = {
                    query = it
                    // A strip left lying across the bar while the query under
                    // it changes is stale furniture, and it is over the text
                    // being typed; the choice it offers is still one tap away.
                    enginePickerOpen = false
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = InkStrong),
                cursorBrush = SolidColor(InkStrong),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                    onGo = { onGo(UrlUtils.toUrlOrSearch(query.text, engine)) },
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    // The strip is drawn straight onto the bar, over this
                    // field: what makes room for it is this text going away
                    // under it, not a panel laid on top.
                    .engineStripVeil(engineStrip)
                    .focusRequester(focus),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.text.isEmpty()) {
                            Text(
                                text = if (private) "Search privately" else "Search or enter address",
                                color = InkMuted,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        inner()
                    }
                },
            )
            // The private button gives its slot up to the clear button as
            // soon as there is a query to clear — including the address this
            // sheet arrives with from the menu, which is a page the user is
            // already on: offering to reopen it in a space it isn't in reads
            // as a button that does nothing to what is in front of it.
            if (query.text.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .aeroPopIf(claim = true)
                        .clip(SpecialCircle)
                        .clickable { query = TextFieldValue("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = InkMuted,
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .aeroPopIf(claim = true)
                        .clip(SpecialCircle)
                        .background(if (private) AccentColor else Color.Transparent)
                        .clickable {
                            haptics.toggle(!private)
                            onTogglePrivate()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = "Private mode",
                        tint = if (private) Color.White else InkMuted,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (private) {
            // Showing browsing history inside the private space would be the
            // wrong signal, so it's replaced rather than merely filtered.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = InkMuted,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "Private tabs are kept apart from the rest: they aren't saved to " +
                        "history, never reach disk, and their previews are blurred in the tab " +
                        "switcher.",
                    color = InkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            // Typing: up to MAX_HISTORY_MATCHES pages, then the engine's
            // suggestions under them. Not typing: the ordinary list — where
            // Most visited has nothing on it yet (no page visited on enough
            // separate days), the recent list stands in rather than an empty
            // one under a header.
            val typing = trimmedQuery.isNotEmpty()
            val showMostVisited = historySort == HistorySort.MostVisited && mostVisited.isNotEmpty()
            val displayedHistory = when {
                typing -> matchingHistory
                showMostVisited -> mostVisited
                else -> recentlyVisited
            }
            val shownSuggestions = if (typing) suggestions else emptyList()
            val headerText = when {
                typing && matchingHistory.isEmpty() -> "Suggestions"
                typing -> "Matching history"
                showMostVisited -> "Most visited"
                else -> "Recently visited"
            }
            Box(Modifier.height(32.dp)) {
                ListHeading(headerText)
            }
            // Fills whatever's left of the sheet's height — generous when
            // the sheet is at its 2/3-screen rest height (or expanded), just
            // leaving blank space below if there isn't enough history to
            // fill it. The keyboard, when up, simply covers the bottom of
            // this list rather than resizing it.
            LazyColumn(Modifier.weight(1f), state = listState) {
                // Keyed so a removal animates the right row away (and the
                // per-row overflow state stays attached to its entry)
                // instead of Compose reusing slots by position.
                items(displayedHistory, key = { it.url }) { entry ->
                    HistoryRow(
                        entry = entry,
                        onGo = { onGo(entry.url) },
                        onRemove = { onRemoveHistory(entry.url) },
                        frosted = frosted,
                        modifier = Modifier.animateItem(),
                    )
                }
                // The second heading only where there is a first list for it
                // to separate from; with no pages matched, the fixed heading
                // above already says "Suggestions".
                if (matchingHistory.isNotEmpty() && shownSuggestions.isNotEmpty()) {
                    item(key = SUGGESTIONS_HEADING_KEY) {
                        Box(Modifier.height(32.dp)) {
                            ListHeading("Suggestions")
                        }
                    }
                }
                items(shownSuggestions, key = { "$SUGGESTION_KEY_PREFIX$it" }) { suggestion ->
                    SuggestionRow(
                        suggestion = suggestion,
                        onGo = { onGo(UrlUtils.toUrlOrSearch(suggestion, engine)) },
                        onEdit = {
                            query = TextFieldValue(suggestion, TextRange(suggestion.length))
                            focus.requestFocus()
                        },
                    )
                }
            }
        }
    }
}

/** How many visited pages a typed query shows above the search suggestions. */
private const val MAX_HISTORY_MATCHES = 3

private const val SUGGESTIONS_HEADING_KEY = "heading:suggestions"
private const val SUGGESTION_KEY_PREFIX = "suggestion:"

/**
 * Whether [query] is how this page's address or name BEGINS — `you` for
 * youtube.com, where a page that only mentions it somewhere in its path is a
 * weaker match.
 */
private fun HistoryEntry.startsWithQuery(query: String): Boolean =
    url.substringAfter("://").removePrefix("www.").startsWith(query, ignoreCase = true) ||
        host.startsWith(query, ignoreCase = true) ||
        title.startsWith(query, ignoreCase = true)

@Composable
private fun ListHeading(text: String) {
    Text(
        text = text,
        color = InkMuted,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** A visited page: tap to open it, swipe it away to remove it from history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onGo: () -> Unit,
    onRemove: () -> Unit,
    frosted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    // End-to-start only: a start-to-end swipe would fight the system back
    // gesture at the screen edge, and there is no second action to put there
    // anyway. confirmValueChange can be consulted more than once for the same
    // settle, so the removal (and its buzz) is latched to fire exactly once
    // per row.
    var dismissed by remember(entry.url) { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                if (!dismissed) {
                    dismissed = true
                    haptics.confirm()
                    onRemove()
                }
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        modifier = modifier,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    // Clipped to the strip the row has actually VACATED.
                    //
                    // The row above used to be relied on to hide this, being
                    // opaque — which stopped being true the moment a theme
                    // made its surfaces glass: under Aero the red ground and
                    // this icon showed through every row in the list at rest,
                    // so a page nobody had touched looked like one being
                    // deleted. Making the row opaque again would have been
                    // the wrong fix in the other direction, since that is the
                    // whole list and the sheet is meant to be seen through.
                    //
                    // So the backdrop stops being something that is covered
                    // and becomes something that is only drawn where it has
                    // been uncovered. Read in the DRAW phase (the offset
                    // changes every frame of a swipe) and clipped rather than
                    // faded: this is a thing appearing from behind another
                    // thing, and its edge is where that thing's edge is.
                    .drawWithContent {
                        val offset = runCatching { dismissState.requireOffset() }.getOrDefault(0f)
                        // End-to-start, so the offset is negative and the
                        // revealed strip is that wide at the end edge. Zero
                        // (at rest, or before the state has been measured)
                        // draws nothing at all.
                        val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
                        val revealed = (if (rtl) offset else -offset)
                            .takeIf { it.isFinite() }?.coerceIn(0f, size.width) ?: 0f
                        if (revealed <= 0f) return@drawWithContent
                        clipRect(
                            left = if (rtl) 0f else size.width - revealed,
                            right = if (rtl) revealed else size.width,
                        ) { this@drawWithContent.drawContent() }
                    }
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove from history",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // The delete backdrop clips itself to the exposed strip;
                // glass rows must not add another layer of sheet tint. Nor
                // may a TUI row: an opaque ground here covers the sheet's
                // raster, and a raster of its own would restart the scanline
                // phase at every row boundary.
                .background(
                    if (frosted || com.yuku.browser.ui.theme.LocalAero.current || com.yuku.browser.ui.theme.LocalTui.current) Color.Transparent
                    else BarBg
                )
                .clickable(onClick = onGo)
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Entries recorded before their tab reported an icon (or restored
            // from disk, which drops bitmaps) fall back to a fetched one
            // inside SiteIcon.
            SiteIcon(host = entry.host, captured = entry.favicon)
            // fadeEdges puts the row in an offscreen compositing layer, so it
            // costs a GPU render target switch per row it's applied to — real
            // money once a dozen rows are on screen and the sheet is
            // animating. One layer for the pair (not one each), and only once
            // something actually overflows the row's width.
            var titleOverflows by remember(entry.url) { mutableStateOf(false) }
            var hostOverflows by remember(entry.url) { mutableStateOf(false) }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
                    .then(
                        if (titleOverflows || hostOverflows) {
                            fadeEdges(edge = 16.dp, bothSides = false)
                        } else {
                            Modifier
                        },
                    )
            ) {
                Text(
                    entry.title,
                    color = InkStrong,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    onTextLayout = { titleOverflows = it.hasVisualOverflow },
                )
                Text(
                    entry.host,
                    color = InkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    onTextLayout = { hostOverflows = it.hasVisualOverflow },
                )
            }
        }
    }
}

/** A search suggestion: the row searches it, the arrow takes it up into the field. */
@Composable
private fun SuggestionRow(
    suggestion: String,
    onGo: () -> Unit,
    onEdit: () -> Unit,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onGo)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = InkMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        // Same offscreen-layer cost as HistoryRow's: only a row whose text
        // overflows pays for one.
        var overflowing by remember(suggestion) { mutableStateOf(false) }
        Text(
            suggestion,
            color = InkStrong,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            onTextLayout = { overflowing = it.hasVisualOverflow },
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
                .then(if (overflowing) fadeEdges(edge = 16.dp, bothSides = false) else Modifier),
        )
        // Takes the suggestion back UP into the field instead of going to it:
        // the row itself is "search this", the arrow is "keep typing from
        // here". The arrow points at where the text is about to land, which is
        // why it is a corner arrow and not a chevron. Its own clickable
        // consumes the tap, so the row underneath does not also fire; the
        // field is re-focused because half the point is the next keystroke.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(SpecialCircle)
                .clickable {
                    haptics.tap()
                    onEdit()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.NorthWest,
                contentDescription = "Edit this search",
                tint = InkMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The search bar's leading favicon, and the strip of engines it opens.
 *
 * Not a menu of names: a ROW of the engines' own icons, unrolling to the right
 * of the button and level with it. A menu of labels was a panel over the very
 * history and suggestions the user is choosing between, and it asked them to
 * read five words to make a choice their eye can make from five marks — the
 * same marks that are already the button.
 *
 * So the button's icon is the strip's first frame, and the strip's first icon
 * is the button: the engine in use is drawn AT the button, and the rest start
 * stacked underneath it at exactly the size the button draws them and fan out
 * to the right. Picking one brings it back to that same spot and shrinks it
 * into the button, which is showing that engine by the time it lands — so the
 * swap is not a cross-fade of two marks in a menu, it is one mark travelling
 * to where the old one was.
 *
 * That the row STARTS on the button rather than past it is what keeps the bar
 * from growing a hole where its search mark was: an earlier arrangement put
 * the first icon to the right of the button and faded the button out under
 * the pile, which left the omnibox with nothing at its head for as long as
 * the strip was up. And being level with the bar rather than above it, the
 * strip no longer cares how far the sheet has been dragged up — the drop-UP's
 * one awkwardness (refused outright at full sheet height, since its top half
 * would have been off the screen) is gone with the drop.
 *
 * A tap toggles it. A HOLD opens it and then selects by drag, committing
 * whichever icon the finger is over when it lifts — one gesture instead of two
 * taps, which is what the thumb already resting on this corner of the bar is
 * in position for. Both live in one detector because they are one press: the
 * hold is simply the tap that hasn't ended yet.
 */
@Composable
private fun EngineButton(
    engine: SearchEngine,
    engines: List<SearchEngine>,
    strip: EngineStrip,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onSelect: (SearchEngine) -> Unit,
) {
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    // Where the strip may run to. Measured rather than assumed: the button's
    // place in the pill is the pill's padding plus the sheet's, and a strip
    // that runs off the right edge is one the user cannot reach the end of.
    var buttonRight by remember { mutableStateOf(0.dp) }
    // The icon the finger is over mid-drag, drawn lit; null whenever the strip
    // is merely latched open by a tap.
    var hover by remember { mutableStateOf<SearchEngine?>(null) }
    // The engine a collapse is carrying home, so it can stay opaque all the
    // way down while everything else fades: that is the whole "shrinks back
    // into the selected engine" effect. Null when the strip is just being put
    // away, where nothing is being carried and all of it fades together.
    var chosen by remember { mutableStateOf<SearchEngine?>(null) }
    // What the gesture reads instead of the state around it, and the reason
    // this class exists: a pointerInput block is a long-lived coroutine, and
    // its snapshot does NOT advance as the composition around it writes —
    // reading `open` in there answered `false` on every tap however many
    // times the strip had been opened, so the second tap opened it again
    // instead of closing it. Plain fields, written from the composition AFTER
    // a frame is drawn, are the honest source for "what is on screen right
    // now" as far as a gesture is concerned.
    val latch = remember { EngineStripLatch() }
    // The engines in the order the strip lays them out: the one in use first,
    // because its place is the button itself. Held in state and refreshed only
    // on the way OPEN (see [setOpen]) — recomputing it in composition would
    // reshuffle the row half way through the collapse that a selection starts,
    // since that selection is exactly what changes which engine is first.
    var order by remember { mutableStateOf(engines) }

    // One animated float drives the whole thing: 0 is every icon stacked on
    // the button at the button's own size, 1 is the strip unrolled. Read only
    // inside graphicsLayer/draw lambdas below — never in composition — so a
    // frame of it costs a draw and not a recomposition of the sheet.
    val progress = strip.progress
    val unrolled by remember { derivedStateOf { progress.value > 0f } }
    val showStrip = open || unrolled

    val count = order.size
    // The strip shrinks its own step before it will run off the screen; below
    // ENGINE_STRIP_MIN_STEP the icons overlap, which is still a fan of icons
    // and still one tap each, where an unreachable tail is neither.
    val step = if (count <= 1) {
        ENGINE_STRIP_STEP
    } else {
        val last = screenWidth - ENGINE_STRIP_MARGIN - ENGINE_STRIP_ITEM / 2
        val first = buttonRight - ENGINE_BUTTON / 2
        ((last - first) / (count - 1)).coerceIn(ENGINE_STRIP_MIN_STEP, ENGINE_STRIP_STEP)
    }

    LaunchedEffect(open) {
        if (open) {
            chosen = null
            progress.animateTo(1f, arrive(ENGINE_STRIP_ENTER_MS))
        } else {
            progress.animateTo(0f, depart(ENGINE_STRIP_EXIT_MS))
            chosen = null
        }
    }

    // The one door in, so the row's order is settled BEFORE the composition
    // that first draws it: written from an event handler, it lands in the same
    // frame as the open itself, where an effect would land a frame after it —
    // long enough to draw the previous open's first icon over the button.
    val setOpen: (Boolean) -> Unit = { want ->
        if (want) order = listOf(engine) + engines.filter { it.id != engine.id }
        onOpenChange(want)
    }
    val choose: (SearchEngine) -> Unit = { candidate ->
        chosen = candidate
        onSelect(candidate)
        onOpenChange(false)
    }

    Box {
        Box(
            modifier = Modifier
                .size(ENGINE_BUTTON)
                .onGloballyPositioned {
                    buttonRight = with(density) {
                        (it.positionInWindow().x + it.size.width).toDp()
                    }
                }
                .clip(SpecialCircle)
                .pointerInput(step) {
                    val itemPx = ENGINE_STRIP_ITEM.toPx()
                    val stepPx = step.toPx()
                    val buttonPx = ENGINE_BUTTON.toPx()
                    // The strip is another window and never sees this
                    // gesture, so the icon under the finger is arithmetic on
                    // the button's own coordinates: icon i is centred i steps
                    // to the right of the button's own centre. Generous
                    // vertically — a thumb sliding along a 40dp strip should
                    // not have to stay inside its band — and it reads the
                    // ORDER off the latch rather than closing over it, since
                    // the order changes on the way open and a changed
                    // pointerInput key would cancel the very press that is
                    // opening it.
                    fun iconAt(p: Offset): SearchEngine? {
                        if (p.y < -itemPx || p.y > buttonPx + itemPx) return null
                        val laid = latch.order
                        val index = ((p.x - buttonPx / 2f) / stepPx + 0.5f).toInt()
                        if (p.x < buttonPx / 2f - itemPx / 2f) return null
                        if (index !in laid.indices) return null
                        return laid[index]
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Deliberately NOT waitForUpOrCancellation: that one
                        // also gives up the moment the finger leaves the
                        // node's bounds, and this button is 32dp — a press
                        // that drifts three pixels off it on the way to
                        // being a hold is still a hold, and the drag it
                        // becomes is entirely outside those bounds anyway.
                        // The only outcomes that end the press early are the
                        // finger LIFTING (a tap) and another node consuming
                        // it (the sheet taking the pointer for a drag), and
                        // the second is not a hold either.
                        var lifted = false
                        var stolen = false
                        var held = false
                        try {
                            withTimeout(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val change = awaitPointerEvent().changes
                                        .firstOrNull { it.id == down.id }
                                    if (change == null) {
                                        stolen = true
                                        break
                                    }
                                    if (!change.pressed) {
                                        lifted = true
                                        break
                                    }
                                    // Movement inside the hold window is
                                    // eaten so the sheet's own drag can't
                                    // claim the press half way to being a
                                    // hold — which it did, settling the sheet
                                    // and dropping the keyboard under a
                                    // finger that was only on its way to an
                                    // icon. Dragging the SHEET by this 32dp
                                    // button is what that costs, and there is
                                    // a whole bar to drag it by.
                                    if (change.positionChange() != Offset.Zero) change.consume()
                                }
                            }
                        } catch (_: PointerEventTimeoutCancellationException) {
                            held = true
                        }
                        if (!held) {
                            if (lifted && !stolen) {
                                haptics.tap()
                                latch.onOpenChange(!latch.open)
                                hover = null
                            }
                            return@awaitEachGesture
                        }
                        haptics.longPress()
                        latch.onOpenChange(true)
                        var over = iconAt(down.position)
                        hover = over
                        while (true) {
                            val change = awaitPointerEvent().changes
                                .firstOrNull { it.id == down.id } ?: break
                            val now = iconAt(change.position)
                            // One tick per icon crossed, the same rate the
                            // switcher ticks as cards pass. Compared against
                            // a local, not against `hover`: see the latch.
                            if (now != over && now != null) haptics.tick()
                            over = now
                            hover = now
                            change.consume()
                            if (!change.pressed) break
                        }
                        hover = null
                        // Released back on the button, or off the strip
                        // entirely, is not a cancel: it stays up, latched,
                        // exactly as a tap would have left it.
                        if (over != null) {
                            haptics.confirm()
                            latch.onChoose(over)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // The button's own icon gives the strip its place and takes it
            // back: hidden outright while an engine is being carried home (the
            // icon landing on it is the same icon at the same size, so there
            // is nothing to cross-fade and a second copy of it would read as
            // a ghost), and cross-faded with the pile otherwise.
            Box(
                modifier = Modifier.graphicsLayer {
                    // Hidden for exactly as long as the strip has an icon of
                    // its own standing on this spot, and back the instant it
                    // does not — no fade, because what replaces it is the same
                    // mark at the same size and a cross-fade of a picture with
                    // itself is a flicker.
                    alpha = if (progress.value > 0f) 0f else 1f
                },
            ) {
                EngineIcon(engine = engine, size = ENGINE_ICON, corner = 6.dp, letterSize = 12.sp)
            }
        }

        // While the strip is up, every tap outside it is the same tap: put it
        // away. This is a window of its own, laid over the whole of the app's,
        // UNDER the strip (popup windows stack in the order they are composed)
        // — and it is a window rather than a scrim inside the sheet because
        // the point is that the touch never reaches what it landed on. A
        // non-focusable popup does get told about outside touches, but the
        // app window is told as well, so dismissing that way still opened the
        // history row the finger came down on.
        //
        // Gated on `open` rather than on the animation, so the time the strip
        // takes to roll back up isn't that long a stretch of swallowed taps.
        // The keyboard is unaffected: the IME is not this window's to cover.
        if (open) {
            Popup(
                popupPositionProvider = WindowOriginPositionProvider,
                properties = PopupProperties(focusable = false),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                // Consumed on the way DOWN, so nothing else
                                // can read the press as the start of a drag,
                                // and closed on the way up — a tap, not a
                                // press, is what dismisses.
                                awaitFirstDown(requireUnconsumed = false).consume()
                                waitForUpOrCancellation()?.let {
                                    it.consume()
                                    latch.onOpenChange(false)
                                }
                            }
                        },
                )
            }
        }

        if (showStrip) {
            // Icon 0 is centred on the button; the rest march right one step
            // at a time. The strip's ground runs between the outer two icons'
            // edges, so at rest it is a circle around the button.
            val itemLeft = ENGINE_STRIP_SLACK + ENGINE_BUTTON / 2 - ENGINE_STRIP_ITEM / 2
            val stripWidth = ENGINE_STRIP_ITEM + step * (count - 1).coerceAtLeast(0)
            val provider = remember(density) {
                EngineStripPositionProvider(
                    slackPx = with(density) { ENGINE_STRIP_SLACK.roundToPx() },
                    buttonPx = with(density) { ENGINE_BUTTON.roundToPx() },
                    itemPx = with(density) { ENGINE_STRIP_ITEM.roundToPx() },
                )
            }
            Popup(
                popupPositionProvider = provider,
                onDismissRequest = { setOpen(false) },
                // NOT focusable, deliberately: a focusable popup takes the
                // window's focus, which drops the keyboard and leaves the
                // field the user is typing in behind a strip that was only
                // ever about where the query goes.
                properties = PopupProperties(focusable = false),
            ) {
                Box(
                    modifier = Modifier
                        // The window covers the button as well as the strip,
                        // and a little past both ends: an icon on its way home
                        // sits ON the button, and the arrival carries a hair
                        // past the far end. Anything outside a popup's own
                        // content is clipped by its window.
                        .width(itemLeft + stripWidth + ENGINE_STRIP_SLACK)
                        .height(ENGINE_STRIP_ITEM)
                        // A tap in the gaps between icons is a tap outside,
                        // and this window is over the one that would have
                        // caught it. Below the icons, which are its children.
                        .pointerInput(open) {
                            if (!open) return@pointerInput
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false).consume()
                                waitForUpOrCancellation()?.let {
                                    it.consume()
                                    latch.onOpenChange(false)
                                }
                            }
                        },
                ) {
                    // Nothing is drawn under the icons: no pill, no hairline,
                    // no lit circle behind the one in use. The strip sits
                    // directly on the omnibox and what makes room for it is
                    // the field's own text fading out from under it (see
                    // [engineStripVeil]) — a panel would have been a second
                    // surface inside a surface, and a ring around one icon
                    // says twice what its position already says.
                    //
                    // No separate hit target over the button: icon 0 is the
                    // engine in use and covers it, so a tap there picks what
                    // is already picked, which is a close and nothing else.
                    order.forEachIndexed { index, candidate ->
                        // With no wash to light up, the icon under a dragging
                        // finger says so by swelling — which is also the one
                        // feedback that survives being under the finger.
                        val hovered = animateFloatAsState(
                            targetValue = if (candidate == hover) ENGINE_STRIP_HOVER_SCALE else 1f,
                            animationSpec = tween(120),
                            label = "engineHover",
                        )
                        Box(
                            modifier = Modifier
                                .offset(x = itemLeft + step * index)
                                .size(ENGINE_STRIP_ITEM)
                                .graphicsLayer {
                                    val p = progress.value
                                    // Home is the button's centre, which is
                                    // icon 0's place at rest too: every other
                                    // icon starts and ends stacked under it at
                                    // the size the button draws them, so the
                                    // one that is chosen lands exactly on the
                                    // mark the button is about to show.
                                    translationX = -step.toPx() * index * (1f - p)
                                    val scale = (
                                        ENGINE_STRIP_HOME_SCALE +
                                            (1f - ENGINE_STRIP_HOME_SCALE) * p
                                        ) * hovered.value
                                    scaleX = scale
                                    scaleY = scale
                                    // Three cases, and only the first two are
                                    // the strip's own: the icon being carried
                                    // home stays opaque the whole way down (it
                                    // is the thing the eye is following), the
                                    // one AT the button hands over to it by
                                    // fading under it — which is the icon swap,
                                    // done in the one place both marks are —
                                    // and the rest fan in and out in turn.
                                    alpha = when {
                                        candidate == chosen -> 1f
                                        index == 0 && chosen == null -> 1f
                                        else -> {
                                            val clamped = p.coerceIn(0f, 1f)
                                            val total = 1f + (count - 1) * ENGINE_STRIP_STAGGER
                                            ((clamped * total - index * ENGINE_STRIP_STAGGER) /
                                                ENGINE_STRIP_FADE).coerceIn(0f, 1f)
                                        }
                                    }
                                }
                                .clip(SpecialCircle)
                                .clickable(enabled = open) {
                                    haptics.tap()
                                    choose(candidate)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            EngineIcon(
                                engine = candidate,
                                size = ENGINE_STRIP_ICON,
                                corner = 8.dp,
                                letterSize = 15.sp,
                            )
                        }
                    }
                }
            }
        }
    }
    // Written where the composition can see it, read by the gesture, which
    // cannot: `open` as of the last frame.
    SideEffect {
        latch.open = open
        latch.onOpenChange = setOpen
        latch.onChoose = choose
        latch.order = order
        // The field beside this one yields to whatever these say, so they are
        // published from the composition that measured them rather than from
        // the layout pass that would have to write during a draw.
        strip.showing = showStrip
        val centre = with(density) { (buttonRight - ENGINE_BUTTON / 2).toPx() }
        // Measured off the icon's own INK (30dp drawn inside a 40dp slot),
        // plus one breath of air. Taking it off the slot instead put the
        // veil's first opaque pixel 5dp past the last mark and its last one
        // a whole fade beyond that, which is the gap between the icons and
        // the text coming back.
        val half = with(density) { (ENGINE_STRIP_ICON / 2).toPx() }
        strip.restEdgePx = centre + half
        strip.fullEdgePx = centre + half + with(density) { (step * (count - 1)).toPx() }
        strip.fadePx = with(density) {
            (step * ENGINE_STRIP_VEIL_FADE_STEPS).coerceAtLeast(ENGINE_STRIP_VEIL_FADE_MIN).toPx()
        }
    }
}

/**
 * What the strip is doing, shared with the field it is drawn over.
 *
 * The strip lives inside [EngineButton] but it lies across the omnibox's text,
 * and with no ground of its own (deliberately — see there) the only thing that
 * keeps that text from running under the icons is the text getting out of the
 * way. So the one float driving the strip is held out here, where both the
 * button that animates it and the field that has to yield to it can read it,
 * along with where the strip's far edge is in window coordinates at each end
 * of the animation.
 */
@Stable
private class EngineStrip {
    /** 0 is every icon stacked on the button; 1 is the strip unrolled. */
    val progress = Animatable(0f)
    /** Whether there is a strip at all — nothing yields to one that is down. */
    var showing by mutableStateOf(false)
    /** Window x of the strip's right edge, closed and fully open. */
    var restEdgePx by mutableFloatStateOf(0f)
    var fullEdgePx by mutableFloatStateOf(0f)
    /** How far past that edge the text takes to come back — one icon step. */
    var fadePx by mutableFloatStateOf(0f)

    /**
     * Where the strip's right edge is right now, in window coordinates. A
     * draw-phase read: it moves every frame, and nothing that reads it needs
     * to be re-composed for that.
     */
    fun edgePx(): Float {
        if (!showing) return 0f
        val p = progress.value.coerceIn(0f, 1f)
        return restEdgePx + (fullEdgePx - restEdgePx) * p
    }
}

/**
 * Fades this node's content out under the engine strip, and back in over
 * [ENGINE_STRIP_VEIL_FADE] past its last icon.
 *
 * Applied to the omnibox's field: with the strip drawn straight onto the bar,
 * "Search or enter address" would otherwise read out from between the icons.
 * The mask is the same DstIn-through-an-offscreen-layer trick as [fadeEdges],
 * with two differences that matter — the edge MOVES, so the gradient is drawn
 * as a rect at a per-frame offset rather than compiled per frame, and the
 * layer is only asked for while there is a strip to hide under, since every
 * keystroke into this field would otherwise be composited offscreen.
 */
/** How many flat bands the veil's ramp is drawn as. See the draw below. */
private const val VEIL_SLICES = 24

@Composable
private fun Modifier.engineStripVeil(strip: EngineStrip): Modifier {
    var left by remember { mutableFloatStateOf(0f) }
    return this
        .onGloballyPositioned { left = it.positionInWindow().x }
        .graphicsLayer {
            compositingStrategy = if (strip.showing) {
                CompositingStrategy.Offscreen
            } else {
                CompositingStrategy.Auto
            }
        }
        .drawWithCache {
            onDrawWithContent {
                val fade = strip.fadePx
                drawContent()
                val edge = strip.edgePx() - left
                if (edge <= 0f) return@onDrawWithContent
                drawRect(
                    color = Color.Transparent,
                    size = Size(edge.coerceAtMost(size.width), size.height),
                    blendMode = BlendMode.DstIn,
                )
                if (edge < size.width) {
                    // The ramp is STEPPED by hand rather than drawn with a
                    // gradient brush, and that is a finding rather than a
                    // preference. A Brush's shader is laid out in the canvas's
                    // own coordinates from 0 to the size it is applied at, so
                    // a gradient stamped at `topLeft = edge` has run out
                    // before it starts and every pixel takes its clamped end
                    // colour — which is the straight cut this fade kept coming
                    // back as. Moving the canvas under it instead
                    // (`translate`) reads correctly and, measured on device
                    // against a screenshot of the same bar with no strip up,
                    // changed nothing at all: mask alpha went 0 to 1 in six
                    // pixels either way. Bands of flat colour are the one
                    // shape of this that provably lands — [VEIL_SLICES] of
                    // them across a fade this short is finer than the display
                    // can resolve, and each is a plain rect inside a layer
                    // that is only asked for while a strip is up.
                    val span = fade.coerceAtMost(size.width - edge)
                    for (i in 0 until VEIL_SLICES) {
                        // Smoothstep, sampled at each band's middle: a linear
                        // ramp is continuous in value but not in slope, so
                        // both of its ends are a corner the eye reads as an
                        // edge. This one leaves the icons and arrives at full
                        // strength without either end landing anywhere.
                        val t = (i + 0.5f) / VEIL_SLICES
                        drawRect(
                            color = Color.Black.copy(alpha = t * t * (3f - 2f * t)),
                            topLeft = Offset(edge + span * i / VEIL_SLICES, 0f),
                            size = Size(span / VEIL_SLICES, size.height),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                }
            }
        }
}

/**
 * See [EngineButton]: plain fields a gesture can read, because state read
 * inside a `pointerInput` block is read through a snapshot that does not
 * advance with the composition.
 */
private class EngineStripLatch {
    /** Whether the strip is on screen as of the last frame drawn. */
    var open = false
    /**
     * The CURRENT callbacks, not the ones that existed when the gesture's
     * coroutine was started — which is a second way the same trap is sprung.
     * `Modifier.pointerInput(keys)` keeps its block, and everything the block
     * closed over, until one of its keys changes: after the + sheet was
     * re-opened, `onOpenChange` still wrote to the state object belonging to
     * the PREVIOUS open (`remember(openKey)`), so the tap set a flag nothing
     * was reading any more and the strip could not be opened again for the
     * life of the process.
     */
    var onOpenChange: (Boolean) -> Unit = {}
    var onChoose: (SearchEngine) -> Unit = {}
    /** The engines in the order the strip has them laid out. */
    var order: List<SearchEngine> = emptyList()
}

/** Puts a popup's content over the whole of the app's window, at its origin. */
private object WindowOriginPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

/**
 * Lays the strip's window over the button and off to its right, level with it.
 *
 * Doing this here rather than by offsetting the popup's content is what keeps
 * the coordinates honest: [anchorBounds] and the returned offset are in the
 * same space by contract, where a popup's own content is laid out in a window
 * whose origin is not this one's (measured: 125px out, the status bar).
 *
 * The window deliberately COVERS the button rather than starting past it: an
 * icon collapsing home has to be drawn sitting on the button, and a popup
 * clips at its own bounds. The button's tap is given back inside the content.
 */
private class EngineStripPositionProvider(
    private val slackPx: Int,
    private val buttonPx: Int,
    private val itemPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        anchorBounds.left - slackPx,
        anchorBounds.top + buttonPx / 2 - itemPx / 2,
    )
}
