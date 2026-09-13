package com.yuku.browser.ui

import com.yuku.browser.ui.theme.tuiBloomIf
import com.yuku.browser.ui.theme.tuiCrtIf
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.net.http.SslError
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.yuku.browser.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuku.browser.core.BrowserViewModel
import com.yuku.browser.core.FileDownloads
import com.yuku.browser.core.displayOrigin
import com.yuku.browser.core.UrlUtils
import com.yuku.browser.ui.theme.AccentColor
import com.yuku.browser.ui.theme.FieldBg
import com.yuku.browser.ui.theme.HairLine
import com.yuku.browser.ui.theme.Ink
import com.yuku.browser.ui.theme.InkMuted
import com.yuku.browser.ui.theme.InkStrong
import com.yuku.browser.ui.theme.PageBg
import kotlinx.coroutines.launch
import com.yuku.browser.ui.theme.specialCorner

/**
 * Everything a page can ask the browser for that has to be answered from
 * outside the page: a file to download, a file to upload, the screen for a
 * video, a script's dialog, a certificate the device won't vouch for, a login
 * a server is asking for, a form it wants sent twice, and the camera,
 * microphone or location of the person reading it.
 *
 * One composable for all of them because they share one property — each is a
 * request already in flight in the renderer, held as state on the ViewModel
 * (see its "web platform requests" and "site permissions" sections) and
 * needing exactly one answer. That also lets the link overlay
 * [com.yuku.browser.ui.CustomTabScreen] have the lot by dropping in the same
 * line, rather than by growing its own half of each.
 *
 * Composed LAST by its hosts, so its back handling sits above theirs: a
 * dialog, a fullscreen video and a certificate warning are the innermost
 * things on screen whenever they are on screen at all.
 */
@Composable
fun WebPlatform(
    vm: BrowserViewModel,
    // A confirmed download's icon, handed over at the moment of the yes with
    // the centre of the card's badge in WINDOW coordinates — the browser's
    // own screen flies it from there into the toolbar. Null in the link
    // overlay, which has no toolbar to fly to and says it with a toast.
    onDownloadConfirmed: ((originInWindow: Offset) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------ downloads

    fun runDownload(request: FileDownloads.Request) {
        scope.launch {
            try {
                val started = FileDownloads.start(context, request)
                started.managerId?.let(vm::trackDownload)
                // A data: URI has already been written by the time this
                // returns; a queued http download has not, and the tracker
                // refreshes the list again when it lands.
                vm.refreshDownloads()
                if (onDownloadConfirmed == null) toast("Downloading ${started.name}")
            } catch (e: Exception) {
                toast(e.message ?: "Couldn't download that file")
            }
        }
    }

    // Writing into the public Downloads folder needs WRITE_EXTERNAL_STORAGE
    // up to API 28 and nothing at all from 29 (see the manifest). Held across
    // the system dialog so the download can resume on the far side of it.
    var pendingDownload by remember { mutableStateOf<FileDownloads.Request?>(null) }
    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingDownload
        pendingDownload = null
        when {
            request == null -> Unit
            granted -> runDownload(request)
            else -> toast("Downloads need permission to save to your storage")
        }
    }

    fun beginDownload(request: FileDownloads.Request) {
        val needsPermission = FileDownloads.needsStoragePermission() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingDownload = request
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            runDownload(request)
        }
    }

    // A tap on a link is not a decision to put a file on the device: the
    // page chose to answer it with one, and a file is the one thing a page
    // can hand over that outlives the page. So it is asked about, with the
    // name and size the server gave, before anything is fetched.
    //
    // The card outlives its request by the length of its exit: the answer is
    // taken off the ViewModel at once (leaving it up would ask about a tap
    // that has been answered), and [leavingDownload] keeps the card drawn
    // while it fades — which is what the icon leaving it on a yes is seen
    // against.
    val download by vm.downloadRequest.collectAsStateWithLifecycle()
    var leavingDownload by remember { mutableStateOf<FileDownloads.Request?>(null) }
    var leftByYes by remember { mutableStateOf(false) }
    val badge = remember { PlacedNode() }
    (download ?: leavingDownload)?.let { request ->
        val leaving = download == null
        if (!leaving && request.url.startsWith("blob:", ignoreCase = true)) {
            // Nothing to ask: it fails the same way whatever the answer,
            // and a yes that leads straight to "can't be downloaded" is a
            // question that wasted a tap.
            LaunchedEffect(request) {
                vm.consumeDownloadRequest()
                runDownload(request)
            }
        } else key(request) {
            fun answer(yes: Boolean) {
                leftByYes = yes
                leavingDownload = request
                vm.consumeDownloadRequest()
            }
            ModalCard(
                title = "Download this file?",
                body = downloadSummary(context, request),
                dismissLabel = "Cancel",
                confirmLabel = "Download",
                onDismiss = { answer(false) },
                // Nothing in the renderer is waiting on this one, so unlike
                // the other cards, a tap outside it is an answer: no.
                onScrimTap = { answer(false) },
                onConfirm = {
                    val origin = badge.coordinates?.takeIf { it.isAttached }
                        ?.let { it.localToWindow(it.size.center.toOffset()) }
                    answer(true)
                    if (origin != null) onDownloadConfirmed?.invoke(origin)
                    beginDownload(request)
                },
                leaving = leaving,
                onLeft = { if (leavingDownload == request) leavingDownload = null },
                header = {
                    DownloadBadge(
                        Modifier
                            .onGloballyPositioned { badge.coordinates = it }
                            // Handed to the flight on a yes, which starts
                            // exactly here: the card fades WITHOUT it, so
                            // there is one icon leaving rather than two.
                            // The overlay has no flight and keeps its own.
                            .alpha(if (leaving && leftByYes && onDownloadConfirmed != null) 0f else 1f),
                    )
                },
            )
        }
    }

    // --------------------------------------------------------- file uploads

    val chooser by vm.fileChooser.collectAsStateWithLifecycle()
    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Cancelled counts: a file input whose callback never fires is dead
        // for the rest of the page's life, so parseResult's null answer has
        // to reach it too.
        vm.onFileChooserResult(result.resultCode, result.data)
    }
    LaunchedEffect(chooser) {
        val request = chooser ?: return@LaunchedEffect
        try {
            pickFiles.launch(request.intent)
        } catch (e: Exception) {
            vm.cancelFileChooser()
            toast("No app on this device can pick that")
        }
    }

    // ---------------------------------------------------- fullscreen video

    val fullscreen by vm.fullscreen.collectAsStateWithLifecycle()
    fullscreen?.let { FullscreenHost(view = it.view, onExit = vm::exitFullscreen) }

    // ----------------------------------------------------------- js dialogs

    val dialog by vm.jsDialog.collectAsStateWithLifecycle()
    dialog?.let { js ->
        val prompt = js.kind == BrowserViewModel.JsDialogKind.Prompt
        var entry by remember(js) {
            mutableStateOf(
                TextFieldValue(js.defaultText, selection = TextRange(0, js.defaultText.length))
            )
        }
        ModalCard(
            title = UrlUtils.registrableDomain(js.url).ifBlank { "This page" },
            body = when (js.kind) {
                BrowserViewModel.JsDialogKind.BeforeUnload ->
                    "Leave this page? Anything you've changed on it may not be saved."
                else -> js.message
            },
            // Alert has one way out, and it is not a decision — "OK" is the
            // whole vocabulary the page gets. Everything else is a question,
            // and the answer that changes nothing goes on the left.
            dismissLabel = if (js.kind == BrowserViewModel.JsDialogKind.Alert) null else "Cancel",
            confirmLabel = when (js.kind) {
                BrowserViewModel.JsDialogKind.BeforeUnload -> "Leave"
                else -> "OK"
            },
            onDismiss = { vm.answerJsDialog(false) },
            onConfirm = { vm.answerJsDialog(true, if (prompt) entry.text else null) },
        ) {
            if (prompt) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(js) { focus.requestFocus() }
                Spacer(Modifier.height(12.dp))
                BasicTextField(
                    value = entry,
                    onValueChange = { entry = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = InkStrong),
                    cursorBrush = SolidColor(InkStrong),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { vm.answerJsDialog(true, entry.text) },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(specialCorner(14.dp))
                        .background(FieldBg)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .focusRequester(focus),
                )
            }
        }
    }

    // ---------------------------------------------------- site permissions

    // The system's own dialog, for the app's half of an allow. Its result is
    // deliberately ignored: the ViewModel re-checks what is actually held,
    // which is the same answer for a grant and the right one for the paths
    // that return nothing useful (a permission the user has permanently
    // denied comes straight back as "denied" without a dialog at all).
    val systemPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.onOsPermissionResult() }

    val osRequest by vm.permissionOsRequest.collectAsStateWithLifecycle()
    LaunchedEffect(osRequest) {
        val permissions = osRequest ?: return@LaunchedEffect
        runCatching { systemPermissions.launch(permissions.toTypedArray()) }
            .onFailure { vm.onOsPermissionResult() }
    }

    val ask by vm.permissionAsk.collectAsStateWithLifecycle()
    ask?.let { question ->
        ModalCard(
            title = displayOrigin(question.origin),
            body = buildString {
                append("This site wants to ")
                append(question.permissions.joinToString(" and ") { it.request })
                append(".")
                if (question.private) {
                    // The buttons still say what they say — this is the one
                    // place they mean something narrower, so it is said here
                    // rather than by relabelling them.
                    append("\n\nIn a private tab nothing is remembered: this answer lasts until you leave.")
                }
            },
            // Three answers, and the order is least to most: what a misread
            // tap should land on is the one that gives nothing away.
            dismissLabel = "Block",
            neutralLabel = "Allow this time",
            confirmLabel = "Allow",
            onDismiss = { vm.answerPermissionAsk(BrowserViewModel.PermissionAnswer.Block) },
            onNeutral = { vm.answerPermissionAsk(BrowserViewModel.PermissionAnswer.AllowOnce) },
            onConfirm = { vm.answerPermissionAsk(BrowserViewModel.PermissionAnswer.Allow) },
        )
    }

    // ------------------------------------------------------------ http auth

    val auth by vm.httpAuth.collectAsStateWithLifecycle()
    auth?.let { challenge ->
        // Keyed on the challenge so a second one (another host, or the same
        // one after a rejected password) starts from empty fields rather than
        // from what was typed into the last.
        var user by remember(challenge) { mutableStateOf(TextFieldValue()) }
        var secret by remember(challenge) { mutableStateOf(TextFieldValue()) }
        val focus = remember(challenge) { FocusRequester() }
        LaunchedEffect(challenge) { focus.requestFocus() }
        ModalCard(
            title = challenge.host,
            // The realm is the server's own words for what is behind the
            // door, and is often the only thing distinguishing two logins on
            // one host — shown when there is one, and not invented when not.
            body = buildString {
                append("This site is asking you to sign in.")
                if (challenge.realm.isNotBlank()) append("\n\n\u201C${challenge.realm}\u201D")
            },
            dismissLabel = "Cancel",
            confirmLabel = "Sign in",
            onDismiss = vm::cancelHttpAuth,
            onConfirm = { vm.answerHttpAuth(user.text, secret.text) },
        ) {
            Spacer(Modifier.height(12.dp))
            CardField(
                value = user,
                onValueChange = { user = it },
                placeholder = "Username",
                imeAction = ImeAction.Next,
                modifier = Modifier.focusRequester(focus),
            )
            Spacer(Modifier.height(8.dp))
            CardField(
                value = secret,
                onValueChange = { secret = it },
                placeholder = "Password",
                imeAction = ImeAction.Done,
                masked = true,
                onDone = { vm.answerHttpAuth(user.text, secret.text) },
            )
        }
    }

    // ------------------------------------------------- form resubmission

    val resubmission by vm.formResubmission.collectAsStateWithLifecycle()
    resubmission?.let { request ->
        ModalCard(
            title = UrlUtils.registrableDomain(request.url).ifBlank { "This page" },
            // Said as what it costs rather than as "confirm form
            // resubmission", which describes the mechanism and not the risk.
            body = "To show this page again, what you sent — a search, a form, " +
                "an order — has to be sent again. Doing that twice can repeat " +
                "whatever it did the first time.",
            dismissLabel = "Cancel",
            confirmLabel = "Send again",
            onDismiss = { vm.answerFormResubmission(false) },
            onConfirm = { vm.answerFormResubmission(true) },
        )
    }

    // ----------------------------------------------------- renderer crashes

    // Not a card: nothing is waiting on an answer and the page is already
    // coming back on its own (see BrowserViewModel.handleRenderProcessGone).
    // What the user needs is the reason the page they were reading restarted.
    // A renderer the SYSTEM killed to reclaim memory raises nothing at all —
    // see BrowserViewModel.PageCrash.
    val crash by vm.pageCrash.collectAsStateWithLifecycle()
    LaunchedEffect(crash) {
        crash ?: return@LaunchedEffect
        vm.consumePageCrash()
        toast("This page crashed, and was reloaded")
    }

    // -------------------------------------------------------- certificates

    val ssl by vm.sslPrompt.collectAsStateWithLifecycle()
    ssl?.let { prompt ->
        ModalCard(
            title = "This connection isn't private",
            body = "${prompt.host}: ${sslReason(prompt.error)}\n\n" +
                "Someone could be reading or changing what this site sends. " +
                "Continue only if you know why the certificate is wrong.",
            dismissLabel = "Go back",
            confirmLabel = "Continue anyway",
            // Proceeding through a broken certificate is the one action here
            // that can cost the user something, so it does not get the
            // accent: the safe answer is the one that looks like the answer.
            confirmIsDangerous = true,
            onDismiss = { vm.answerSslPrompt(false) },
            onConfirm = { vm.answerSslPrompt(true) },
        )
    }
}

/**
 * Chromium's own view for a fullscreened element, put on screen.
 *
 * The view is BORROWED — it belongs to the WebView and is handed back by
 * `onRelease` — so nothing here may keep it, and nothing may assume it
 * arrives without a parent: a re-entrant fullscreen (a video handing off to
 * another) can bring the same view back while it is still in a container.
 */
@Composable
private fun FullscreenHost(view: View, onExit: () -> Unit) {
    val context = LocalContext.current
    val window = remember(context) { context.findActivity()?.window }

    // The bars go away, and the screen stays on. Both are what fullscreen
    // MEANS here — a video is the one thing the browser shows that the user
    // watches without touching, which is exactly when the display times out.
    DisposableEffect(window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    BackHandler(enabled = true, onBack = onExit)

    Box(
        Modifier
            .fillMaxSize()
            // Black, not the theme's page background: this is the letterbox
            // around a video, and every player on the platform draws it in
            // the one colour that disappears next to the picture.
            .background(Color.Black)
            // The page is still live underneath. Nothing on it should be
            // reachable through the video.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { FrameLayout(it) },
            update = { host ->
                if (view.parent !== host) {
                    (view.parent as? ViewGroup)?.removeView(view)
                    host.removeAllViews()
                    host.addView(
                        view,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Gravity.CENTER,
                        ),
                    )
                }
            },
            onRelease = { host -> host.removeAllViews() },
        )
    }
}

/**
 * The app's one modal card: a scrim, a panel, and up to two answers.
 *
 * A card rather than a bottom sheet, and centred rather than anchored: these
 * are the surfaces that STOP something — a script waiting on an answer, a
 * load waiting on a decision — where every sheet in this app is a place to go
 * next. Tapping the scrim does nothing on purpose; something in the renderer
 * is blocked until one of the buttons is pressed, and a dismissal that could
 * happen by accident is not an answer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModalCard(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null,
    // The middle answer, for the one question here that has three: allow,
    // allow once, refuse. Drawn between the other two, in the plain ink the
    // dismissal uses — the accent marks the standing answer, and "this time"
    // is deliberately not one.
    neutralLabel: String? = null,
    onNeutral: () -> Unit = {},
    confirmIsDangerous: Boolean = false,
    // A tap on the scrim, for a card whose question can be walked away from.
    // Null keeps the scrim inert, which is right for every card where
    // something in the renderer is blocked on the answer.
    onScrimTap: (() -> Unit)? = null,
    // Drawn above the title — the download card's badge.
    header: (@Composable () -> Unit)? = null,
    // The card on its way out after an answer, and what to call once it has
    // gone. Only a caller that keeps the card composed past its request can
    // use it (see the download card); the rest vanish with the request, as
    // they always have.
    leaving: Boolean = false,
    onLeft: () -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val haptics = rememberHaptics()
    // Arrives the way every surface in this app arrives — carried slightly
    // past its mark and settled back (see Motion.kt) — rather than appearing
    // whole, which on a card this small reads as a flash. Leaves on
    // [depart], scrim and all.
    val scale = remember { Animatable(0.94f) }
    val fade = remember { Animatable(1f) }
    val left by rememberUpdatedState(onLeft)
    LaunchedEffect(leaving) {
        if (!leaving) {
            scale.animateTo(1f, arrive(SURFACE_ENTER_MS))
        } else {
            launch { scale.animateTo(0.94f, depart(SURFACE_EXIT_MS)) }
            fade.animateTo(0f, tween(SURFACE_EXIT_FADE_MS))
            left()
        }
    }

    BackHandler(enabled = !leaving, onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(Color.Black.copy(alpha = 0.45f))
            // A card that has been answered takes no more touches: the page
            // under its fading scrim is the page again.
            .then(
                if (leaving) Modifier
                else Modifier.pointerInput(Unit) { detectTapGestures { onScrimTap?.invoke() } }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .widthIn(max = 400.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    alpha = scale.value.coerceIn(0f, 1f)
                }
                // The card's own body is not the scrim: a tap on its text
                // must not reach the detector above and count as "outside".
                .pointerInput(Unit) { detectTapGestures { } }
                .clip(specialCorner(24.dp))
                .background(PageBg)
                .border(1.dp, HairLine, specialCorner(24.dp))
                .tuiCrtIf()
                .tuiBloomIf()
                .padding(20.dp),
        ) {
            if (header != null) {
                header()
                Spacer(Modifier.height(14.dp))
            }
            Text(
                text = title,
                color = InkStrong,
                style = MaterialTheme.typography.titleMedium,
            )
            if (body.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = body,
                    color = InkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    // A page can put anything in here, at any length. It
                    // scrolls inside the card rather than growing it off the
                    // top and bottom of the screen with the buttons.
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
            content()
            Spacer(Modifier.height(18.dp))
            // A FlowRow rather than a Row: three answers do not fit across a
            // narrow screen, and wrapping them is better than either
            // truncating a label or shortening it into a riddle.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (dismissLabel != null) {
                    CardAction(dismissLabel, tint = Ink) {
                        haptics.tap()
                        onDismiss()
                    }
                    Spacer(Modifier.width(4.dp))
                }
                if (neutralLabel != null) {
                    CardAction(neutralLabel, tint = Ink) {
                        haptics.tap()
                        onNeutral()
                    }
                    Spacer(Modifier.width(4.dp))
                }
                CardAction(
                    label = confirmLabel,
                    tint = if (confirmIsDangerous) MaterialTheme.colorScheme.error else AccentColor,
                ) {
                    if (confirmIsDangerous) haptics.confirm() else haptics.tap()
                    onConfirm()
                }
            }
        }
    }
}

/**
 * A field inside [ModalCard] — the same shape the prompt dialog's own field
 * has, factored out once a card needed two of them.
 */
@Composable
private fun CardField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
    masked: Boolean = false,
    onDone: () -> Unit = {},
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(specialCorner(14.dp))
            .background(FieldBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.text.isEmpty()) {
            Text(
                text = placeholder,
                color = InkMuted,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = InkStrong),
            cursorBrush = SolidColor(InkStrong),
            visualTransformation =
                if (masked) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                // The password half asks for the keyboard that doesn't
                // autocorrect or capitalise what is typed into it.
                keyboardType = if (masked) KeyboardType.Password else KeyboardType.Text,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CardAction(label: String, tint: Color, onClick: () -> Unit) {
    Text(
        text = label,
        color = tint,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(specialCorner(12.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

/**
 * The file, its size when the server said, and where it is coming from — the
 * host of the FILE, not of the page, since a page can link anywhere and the
 * file's own host is the one that will be trusted with the download.
 */
private fun downloadSummary(context: Context, request: FileDownloads.Request): String = buildString {
    append(request.fileName)
    val source = runCatching { Uri.parse(request.url).host }.getOrNull()
        ?.removePrefix("www.")
        ?.takeIf { it.isNotBlank() }
    val details = listOfNotNull(
        request.contentLength.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) },
        source?.let { "from $it" },
    )
    if (details.isNotEmpty()) {
        append('\n')
        append(details.joinToString(" · "))
    }
}

/** What the certificate is actually wrong about, in the user's words. */
private fun sslReason(error: SslError): String = when (error.primaryError) {
    SslError.SSL_EXPIRED -> "its certificate has expired"
    SslError.SSL_IDMISMATCH -> "its certificate is for a different site"
    SslError.SSL_NOTYETVALID -> "its certificate isn't valid yet"
    SslError.SSL_UNTRUSTED -> "its certificate isn't from a trusted authority"
    SslError.SSL_DATE_INVALID -> "its certificate has an invalid date"
    SslError.SSL_INVALID -> "its certificate is malformed"
    else -> "its certificate can't be verified"
}

private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
