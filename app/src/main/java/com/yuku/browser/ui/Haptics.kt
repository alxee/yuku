package com.yuku.browser.ui

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * The app's haptic vocabulary, in one place.
 *
 * Compose's own `LocalHapticFeedback` only knows two effects in this Compose
 * version (`LongPress`, `TextHandleMove`) — everything expressive (the tick a
 * picker makes as it passes a stop, the two-part thump of a committed
 * gesture, the distinct on/off of a toggle) only exists as a platform
 * [HapticFeedbackConstants], so this goes through the View instead. Effects
 * added after API 26 fall back to the closest older constant rather than
 * going silent, so every device feels the same *events*, just at whatever
 * fidelity it has.
 *
 * Nothing here overrides the user's system haptics setting: a plain
 * `performHapticFeedback` is silent when they've turned touch feedback off,
 * which is the correct behavior and the reason no call site passes
 * `FLAG_IGNORE_GLOBAL_SETTING`.
 */
@Immutable
class Haptics(private val view: View) {
    private fun perform(constant: Int) {
        view.performHapticFeedback(constant)
    }

    /**
     * A release effect landing this soon after [gestureStart] is the tail of
     * that same gesture — a swipe-up-to-close flicked off in two frames
     * crosses the drag's dead zone and its commit threshold almost at once,
     * and firing both is two buzzes for one movement. The release is dropped
     * rather than the start: the start has already played by the time the
     * release is known, so it's the only one that can still be suppressed.
     *
     * Shared across instances (hence the companion) because the two halves of
     * one gesture routinely come from different composables — a card's own
     * drag detector fires the start, the grid that owns the close threshold
     * fires the commit — and there is only one actuator either way.
     */
    private fun releaseFollowsGestureStart(): Boolean {
        val at = gestureStartAt
        gestureStartAt = 0L
        return at != 0L && SystemClock.uptimeMillis() - at < GESTURE_MERGE_WINDOW_MS
    }

    /** An ordinary control did its ordinary thing: a button, a row, a tab. */
    fun tap() = perform(HapticFeedbackConstants.VIRTUAL_KEY)

    /** Something now has a state it didn't before — a switch, a quick tile. */
    fun toggle(on: Boolean) = perform(
        when {
            Build.VERSION.SDK_INT >= 34 ->
                if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            else -> HapticFeedbackConstants.VIRTUAL_KEY
        },
    )

    /** A press that opened something other than what a tap would have. */
    fun longPress() = perform(HapticFeedbackConstants.LONG_PRESS)

    /**
     * One notch passed: the switcher moving from one tab to the next, a
     * picker landing on a new value. Deliberately the lightest effect there
     * is — these fire in a stream while a finger is moving, so anything
     * heavier turns a scroll into a rattle.
     */
    fun tick() = perform(
        if (Build.VERSION.SDK_INT >= 34) {
            HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        },
    )

    /** A drag has taken hold and the thing under the finger is now moving. */
    fun gestureStart() {
        gestureStartAt = SystemClock.uptimeMillis()
        perform(
            if (Build.VERSION.SDK_INT >= 30) {
                HapticFeedbackConstants.GESTURE_START
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            },
        )
    }

    /** That drag let go and settled somewhere. */
    fun gestureEnd() {
        if (releaseFollowsGestureStart()) return
        perform(
            if (Build.VERSION.SDK_INT >= 30) {
                HapticFeedbackConstants.GESTURE_END
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            },
        )
    }

    /** A gesture crossed the line and committed: a tab closed, a page opened. */
    fun confirm() {
        if (releaseFollowsGestureStart()) return
        perform(
            if (Build.VERSION.SDK_INT >= 30) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.LONG_PRESS
            },
        )
    }

    /** A gesture was released without committing, or wasn't allowed. */
    fun reject() {
        if (releaseFollowsGestureStart()) return
        perform(
            if (Build.VERSION.SDK_INT >= 30) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.LONG_PRESS
            },
        )
    }

    private companion object {
        /**
         * Long enough to cover a flick that clears a dead zone and a commit
         * threshold in the same handful of frames, short enough that an
         * ordinary drag — where start and release are separate events the
         * user made separately — still gets both.
         */
        const val GESTURE_MERGE_WINDOW_MS = 200L

        /** When the last [gestureStart] fired; 0 once it has been consumed. */
        var gestureStartAt = 0L
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
