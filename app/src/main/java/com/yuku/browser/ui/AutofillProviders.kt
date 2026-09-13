package com.yuku.browser.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.autofill.AutofillManager

/**
 * Which app the user has actually put in charge of their passwords.
 *
 * The browser's own switch decides *whether* the page's fields go to the
 * platform autofill service; this is what lets Settings say who that service
 * is by name. It is a display concern only — nothing here changes anything,
 * and [openPicker] just opens the screen where the choice really lives.
 *
 * There is no public API for "who is the current autofill service", so the
 * setting is read by key. `AutofillManager.isEnabled` is the public question
 * and answers a smaller one — whether anything at all is answering — which is
 * the fallback when the read comes back empty on a device that hides it.
 */
internal object AutofillProviders {

    /**
     * [label] is null when a provider is set but cannot be named: its package
     * is outside what the manifest's `<queries>` makes visible, or it has been
     * uninstalled since it was chosen.
     */
    data class Provider(val label: String?, val packageName: String?)

    /** The chosen provider, or null if the user has none. */
    fun current(context: Context): Provider? {
        val component = runCatching {
            Settings.Secure.getString(context.contentResolver, AUTOFILL_SERVICE)
        }.getOrNull()
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let(ComponentName::unflattenFromString)

        if (component == null) {
            // Either genuinely none, or a device that won't hand over the
            // setting. `isEnabled` tells the two apart; an unnameable
            // provider is still a provider, and reporting "none" would tell
            // the user to go choose one they already have.
            val enabled = runCatching {
                context.getSystemService(AutofillManager::class.java)?.isEnabled == true
            }.getOrDefault(false)
            return if (enabled) Provider(label = null, packageName = null) else null
        }

        val label = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(component.packageName, 0)).toString()
        }.getOrNull()
        return Provider(label = label, packageName = component.packageName)
    }

    /**
     * Opens the system's autofill-service picker.
     *
     * Deliberately with no data URI. `ACTION_REQUEST_SET_AUTOFILL_SERVICE`
     * takes a `package:` URI to mean "ask the user to make THIS app the
     * provider", which is not what is wanted from a browser that isn't one —
     * bare, it is the list of the ones that are.
     */
    fun openPicker(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE))
        }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    private const val AUTOFILL_SERVICE = "autofill_service"
}
