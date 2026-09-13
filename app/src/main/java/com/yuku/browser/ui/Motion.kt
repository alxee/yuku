package com.yuku.browser.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/**
 * The app's motion vocabulary: one place that decides what "arriving",
 * "settling" and "leaving" feel like, so a sheet, a full-screen destination, a
 * tab card springing back from a swipe and a menu popping open are all the
 * same gesture at different sizes.
 *
 * The shape of it is inertia. Everything that ARRIVES somewhere — a sheet
 * opening, a destination sliding up, a card returning to its slot — carries a
 * little past where it is going and settles back onto it, the way something
 * with mass does. Everything that LEAVES accelerates away instead: an exit has
 * nothing to settle onto, and overshooting one either happens off-screen where
 * nobody sees it or reads as the thing hesitating on its way out.
 *
 * Deliberately easing curves on ordinary [tween]s rather than springs. Nearly
 * every animation in this app is a duration that something else is timed
 * against — a cover held for the length of a slide, a page inset that steps in
 * one go while the bar takes [TOOLBAR_SLIDE_MS] to get there, a capture taken
 * once a settle has landed. A spring has no duration to quote, so adopting one
 * would mean every one of those relationships becoming a guess. The curve
 * gives the same overshoot-and-settle with the finish still known exactly.
 */

/**
 * The ordinary arrival: ~1.2% past the target at ~60% of the duration, then
 * back onto it over the remaining 40%.
 *
 * Deliberately far under a canonical `easeOutBack` (0.34, 1.56, 0.64, 1),
 * which overshoots ~10%. At the travel distances here — a sheet crossing two
 * thirds of the screen, a page shrinking to a card — 10% is a bounce and even
 * 5% is a wobble you can point at. At 1.2% it is not something the eye
 * separates from the movement at all; it just stops reading as though the
 * animation had been cut off at its mark.
 *
 * The percentage is of the TRAVEL, not of the element, which is what keeps it
 * honest across sizes: a sheet moving 900px carries ~11px past, a menu
 * scaling 0.9 -> 1 lands at 1.001.
 */
val Overshoot: Easing = CubicBezierEasing(0.30f, 1.1964f, 0.58f, 1f)

/**
 * ~0.5% past. For motion whose travel is the whole screen, or that is carrying
 * something the eye is reading (a turning deck of pages, an expanding card):
 * the same percentage is a much bigger movement there.
 */
val OvershootSoft: Easing = CubicBezierEasing(0.34f, 1.1352f, 0.64f, 1f)

/**
 * ~5% past. For an arrival whose TRAVEL is small next to the thing that is
 * travelling — where [Overshoot]'s 1.2% of travel comes to a couple of pixels
 * and there is nothing left for the eye to read as weight.
 *
 * The percentage is of the travel everywhere in this file, which is what keeps
 * the settle honest across sizes; the flip side is that it only reads as a
 * settle while the travel is large. The tab switcher's expand carries 1.2% of
 * a page-sized journey — some eight pixels of scale plus the position going
 * with it — and that is the amount of carry this curve exists to reproduce
 * when the journey is a card growing by a seventh of itself rather than a page
 * shrinking to a thumbnail. Same felt inertia, a bigger fraction of a smaller
 * distance.
 *
 * Not a licence to raise the app's overshoot generally: anything crossing real
 * ground still takes [Overshoot] or [OvershootSoft], where 5% would be the
 * wobble those two were tuned down to avoid.
 */
val OvershootPop: Easing = CubicBezierEasing(0.30f, 1.39f, 0.58f, 1f)

/**
 * Leaving the way [OvershootPop] arrives: the exact mirror of it, so the value
 * gathers ~5% the WRONG way (peaking at ~41% of the duration) before
 * accelerating out.
 *
 * This is an anticipation, not an overshoot, and the difference is what makes
 * it allowed here at all: the note on [Accelerate] is about carrying PAST the
 * end of an exit, which happens off-screen or reads as hesitation. Gathering
 * before the off is the other end of the movement, and for a surface that pops
 * IN, an exit that simply accelerates reads as a different object leaving than
 * the one that arrived. Reserved for exactly those surfaces; everything else
 * still leaves on [Accelerate].
 */
val Anticipate: Easing = CubicBezierEasing(0.42f, 0f, 0.70f, -0.39f)

/**
 * Leaving. Accelerates out and never comes back — the mirror of [Overshoot],
 * not its reverse (a reversed overshoot curve starts by moving the WRONG way,
 * which reads as the element flinching before it goes).
 */
val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** An arrival: [Overshoot] over [durationMillis]. */
fun <T> arrive(durationMillis: Int): FiniteAnimationSpec<T> =
    tween(durationMillis, easing = Overshoot)

/** An arrival that is crossing a lot of ground: [OvershootSoft]. */
fun <T> arriveSoft(durationMillis: Int): FiniteAnimationSpec<T> =
    tween(durationMillis, easing = OvershootSoft)

/** A departure that gathers itself first: [Anticipate]. */
fun <T> departPop(durationMillis: Int): FiniteAnimationSpec<T> =
    tween(durationMillis, easing = Anticipate)

/** An arrival with little ground to cross: [OvershootPop]. */
fun <T> pop(durationMillis: Int): FiniteAnimationSpec<T> =
    tween(durationMillis, easing = OvershootPop)

/** A departure: [Accelerate] over [durationMillis]. */
fun <T> depart(durationMillis: Int): FiniteAnimationSpec<T> =
    tween(durationMillis, easing = Accelerate)

// ---------------------------------------------------------------------------
// Durations.
//
// An overshoot curve spends its last ~40% settling, so a duration tuned for a
// curve that simply stops (FastOutSlowIn) arrives visibly early and then
// lingers. Everything given an overshoot below is ~15% longer than it was, so
// the moment it FIRST reaches the target is where it used to finish, and the
// settle is the part that is new.
// ---------------------------------------------------------------------------

/** A surface arriving over what you were looking at: sheets, destinations. */
const val SURFACE_ENTER_MS = 300
/** …and its fade, which runs on its own clock, same as AnimatedVisibility does. */
const val SURFACE_ENTER_FADE_MS = 240
/** The same surface leaving. Shorter than its entrance: nothing to settle onto. */
const val SURFACE_EXIT_MS = 200
const val SURFACE_EXIT_FADE_MS = 160

/**
 * A settings pane replacing another: the two share an axis, both travelling the
 * same way while they cross-fade, which is what the system Settings app does
 * (measured on-device — see SettingsSheet's transitionSpec for how, and for
 * the numbers).
 *
 * The travel is 1/[PANE_SLIDE_FRACTION] of the screen, not the whole of it:
 * with the panes fading past each other the slide is only there to say which
 * direction the navigation went. Twice what the system's own panes move —
 * theirs measured about a sixth of the screen — because these panes carry less
 * on them, and the same distance reads as less movement over a shorter list. Arriving takes [PaneDecelerate] — most of the
 * distance early, then a long tail onto the mark — and leaving takes
 * [Accelerate], cut off by its own fade before it finishes.
 *
 * No overshoot on either, unlike the app's other arrivals: a pane is mostly
 * text, and text carrying past its mark and settling back is a wobble you read
 * rather than a weight you feel.
 */
const val PANE_SLIDE_MS = 300
/** The outgoing pane's whole fade, and the delay before the incoming one's. */
const val PANE_FADE_OUT_MS = 90
const val PANE_SLIDE_FRACTION = 3

/**
 * A pane arriving. Front-loaded and then a long settle — a plain decelerate
 * rather than [Overshoot], since it must not carry past its mark.
 */
val PaneDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** A gesture released without committing, returning to where it started. */
const val SNAP_BACK_MS = 230
/** A gesture released past its threshold, carrying on off-screen. */
const val FLY_AWAY_MS = 240

/**
 * How far into a fly-away the row it left may start closing the gap, as a
 * fraction of the flight.
 *
 * The card leaving and the row closing up over it used to start together, so
 * for the first frames of the flight the neighbour was sliding sideways
 * THROUGH a card that had not yet cleared the row — two cards overlapping in
 * the one band, which reads as a collision rather than as one thing leaving
 * and another taking its place. The gap closes once the flight is clear of the
 * row instead.
 *
 * The number is that clearance for the geometry both close gestures share: a
 * card is 0.6 of the screen tall and is thrown ~3 screens, so it is out of the
 * row after a fifth of its travel — which [Accelerate], slow off the mark, is
 * a little past half its duration into. Still an overlap in TIME, deliberately
 * — the row is moving while the card is still visibly flying — just not one in
 * SPACE.
 */
const val FLY_AWAY_CLEAR_FRACTION = 0.56f

/**
 * How far past its target [Overshoot] actually goes, as a fraction. For the
 * one case that has to know: the sheet measures its contents at a fixed height
 * ahead of the animation precisely so no frame of it re-measures, and the
 * overshoot is part of the height it reaches (see BrowserScreen's sheet).
 */
const val OVERSHOOT_PEAK = 1.015f
