package com.yuku.browser.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * "Confirm it's you" in front of the saved-password list — the device's own
 * fingerprint, face or PIN, whichever the phone is set up with.
 *
 * **What this does and does not protect.** It is a door on the screen, not on
 * the file: `PasswordStore`'s key is not bound to user authentication, so the
 * vault decrypts for the process whether anyone has confirmed anything. What
 * the lock is actually for is the case it is always for — the phone handed to
 * someone, unlocked, for a minute — and it covers that completely. Binding the
 * key itself (`setUserAuthenticationRequired`) would mean re-authenticating
 * for every read, including the one in `PasswordStore.init` that has to happen
 * before the first page can be filled, so it is deliberately not that.
 *
 * [BIOMETRIC_WEAK] rather than STRONG, with the device credential alongside
 * it: no `CryptoObject` is involved, so the question really is only "is this
 * the person who unlocks this phone", and insisting on a strong sensor would
 * lock out a device whose face unlock is all it has.
 */
internal object DeviceLock {

    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /**
     * Puts the system's prompt up and calls [onSuccess] if it is satisfied.
     *
     * A device with no lock screen at all falls straight through to
     * [onSuccess]: there is nothing to check against, and refusing to show
     * the list would be withholding the user's own passwords from them on a
     * phone anyone can already open. Failure and cancellation are silent —
     * the prompt has already said what happened, and the list simply doesn't
     * open.
     */
    fun confirm(context: Context, subtitle: String, onSuccess: () -> Unit) {
        val activity = context.findFragmentActivity() ?: return onSuccess()
        if (BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS)
            != BiometricManager.BIOMETRIC_SUCCESS
        ) return onSuccess()

        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            },
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Saved passwords")
                .setSubtitle(subtitle)
                // No negative button, and this is not a style choice: the
                // builder rejects one outright when DEVICE_CREDENTIAL is
                // among the allowed authenticators, since the credential
                // screen is itself the way out.
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
        )
    }

    private fun Context.findFragmentActivity(): FragmentActivity? {
        var context: Context? = this
        while (context is ContextWrapper) {
            if (context is FragmentActivity) return context
            context = context.baseContext
        }
        return null
    }
}
