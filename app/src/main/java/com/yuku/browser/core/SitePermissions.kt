package com.yuku.browser.core

import android.Manifest
import android.os.Build
import android.webkit.PermissionRequest

/**
 * The capabilities a page can ask this browser for that belong to the device
 * rather than to the web: where it is, what its camera sees, what its
 * microphone hears.
 *
 * Deliberately a small closed set, and it is closed by what a page can
 * actually raise rather than by what the web platform defines. Everything
 * here arrives through one of three doors — [PermissionRequest] for the
 * media captures, `onGeolocationPermissionsShowPrompt` for location, and our
 * own [WebNotifications] polyfill for notifications, which WebView does not
 * have at all. There is no Bluetooth, no USB and no FedCM, so there is
 * nothing on the other side of a row for any of them.
 *
 * [androidPermissions] is the other half of every one of these: a page's
 * permission and the app's are two different questions, and the page's is
 * worth nothing without the app's. The user answering "Allow" here is
 * answering about the SITE; whether this app may reach the camera at all is
 * the system's own dialog, asked after (see BrowserViewModel's
 * `permissionOsRequest`).
 */
enum class SitePermission(
    val id: String,
    val label: String,
    val androidPermissions: List<String>,
) {
    Location(
        id = "location",
        label = "Location",
        // Both, in one request: WebView asks the platform for whichever it
        // can get, and a device where the user granted only the coarse one
        // still positions a page to the neighbourhood it is in.
        androidPermissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
    ),
    Camera(
        id = "camera",
        label = "Camera",
        androidPermissions = listOf(Manifest.permission.CAMERA),
    ),
    Microphone(
        id = "microphone",
        label = "Microphone",
        androidPermissions = listOf(Manifest.permission.RECORD_AUDIO),
    ),
    Notifications(
        id = "notifications",
        label = "Notifications",
        // A runtime permission only from API 33. Below it there is nothing to
        // ask for, and whether the app may post is the user's switch in
        // system settings — see BrowserViewModel.heldBySystem.
        androidPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        },
    );

    /** What a page asks for, in the page's own words. */
    val request: String
        get() = when (this) {
            Location -> "know your location"
            Camera -> "use your camera"
            Microphone -> "use your microphone"
            Notifications -> "show notifications"
        }

    /**
     * What an unanswered site gets while the user has not picked a rule in
     * Settings. Notifications are OFF: a notification is the one capability
     * that reaches the user outside the page, and "allow notifications?" on
     * arrival is the web's most refused question. A user who wants them turns
     * the rule to Ask.
     */
    val defaultRule: PermissionRule
        get() = if (this == Notifications) PermissionRule.Block else PermissionRule.Ask

    /** The [PermissionRequest] resource string this is, where there is one. */
    val resource: String?
        get() = when (this) {
            Camera -> PermissionRequest.RESOURCE_VIDEO_CAPTURE
            Microphone -> PermissionRequest.RESOURCE_AUDIO_CAPTURE
            // Geolocation does not come through PermissionRequest at all, and
            // notifications do not exist in WebView to come through anything.
            Location, Notifications -> null
        }

    companion object {
        fun byId(id: String?): SitePermission? = entries.firstOrNull { it.id == id }

        /**
         * Which of ours a WebView resource string is, or null.
         *
         * Null is the honest answer for `RESOURCE_PROTECTED_MEDIA_ID` and
         * `RESOURCE_MIDI_SYSEX`, and an unrecognised resource is refused
         * rather than folded into the nearest row: granting a resource the
         * user was never shown is exactly the failure this whole file exists
         * to prevent.
         */
        fun ofResource(resource: String): SitePermission? =
            entries.firstOrNull { it.resource == resource }
    }
}

/** What happens when a site asks, before that site has been answered about. */
enum class PermissionRule {
    /** Put the question on screen. */
    Ask,

    /** Granted without asking — the system's own dialog may still appear. */
    Allow,

    /** Refused without asking, and without telling the page why. */
    Block;

    val label: String
        get() = when (this) {
            Ask -> "Ask"
            Allow -> "Allow"
            Block -> "Block"
        }
}

/**
 * One site's standing answer for one capability.
 *
 * Keyed by ORIGIN, not by registrable domain, which is the one place this
 * store is stricter than the rest of the app (history, bookmarks and the
 * password vault are all keyed by domain). A permission is a capability
 * handed to whatever is running on the page, and `https://maps.example.com`
 * having the camera is not a reason for `http://ads.example.com` to have it.
 */
data class SitePermissionGrant(
    val origin: String,
    val permission: SitePermission,
    val allowed: Boolean,
) {
    val key: String get() = key(origin, permission)

    companion object {
        fun key(origin: String, permission: SitePermission) = "$origin|${permission.id}"
    }
}

/**
 * The origin as the user should read it: the scheme dropped where it is the
 * ordinary one, kept where it is not, since `http://` on a page asking for
 * the microphone is itself the interesting part.
 */
fun displayOrigin(origin: String): String = when {
    origin.startsWith("https://") -> origin.removePrefix("https://")
    origin.isBlank() -> "This page"
    else -> origin
}
