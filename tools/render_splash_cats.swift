// Regenerates everything about the splash-screen cats:
//
//   swift tools/render_splash_cats.swift app/src/main/res
//
// — the baked PNG per face (drawable-nodpi/splash_cat_N.png), the tinted
// wrapper per frame (drawable/splash_cat_frame_N.xml), one <animation-list>
// per launch ORDER (drawable/splash_cats_N.xml) and the themes that name them
// (values-v31/splash_orders.xml). All of it is derived from `faces` below, so
// the set is changed here and nowhere else.
//
// There are TWO splashes, because there are two kinds of launch (see
// MainActivity.followThemeOnSplash). A launch the app could register a theme
// for gets the one above: the accent's colours and twenty cats cycling. A
// launch into a task record that outlived the process cannot be registered
// for at all and gets the manifest's Theme.Browser, so that one is a single
// still face — `fallbackFace` below, rendered to splash_cat_fallback.png —
// grey on plain white or black. It is not one of the twenty and never cycles:
// it is the splash for the launch this app has no say over, and it is drawn
// as the plain thing rather than as a worse copy of the other one.
//
// Rendered here rather than drawn on-device because these faces span Hangul,
// Sinhala, Kannada, Ethiopic, Thai, Yi and more — no single font has them, so
// they need per-glyph fallback, and CoreText does it properly. See CLAUDE.md.
import AppKit

// Twenty faces, chosen on two axes.
//
// WIDTH first: every face is scaled by the same point size (see `fontSize`
// below), which is set by whichever face is tightest against the splash's safe
// circle — so one narrow face doesn't make itself small, it makes every other
// face small with it. These sit between 750 and 895 units wide at the
// reference size, and the ones that used to be narrower than that band were
// dropped rather than carried.
//
// Then the MOUTH, which is the one glyph in a face like this that a viewer
// reads as the expression: fifteen different ones across the twenty, at most
// three of any single mouth. Eyes are allowed to repeat where a mouth needs a
// particular width around it — two faces with the same eyes and different
// mouths look like two cats, two with the same mouth look like one cat twice.
let faces = [
    "(=◉ᆽ◉=)", "(=ↀωↀ=)", "(=ㅍᴥㅍ=)", "(=ㅇﻌㅇ=)", "(=ʘㅅʘ=)",
    "(≈චᆽච≈)", "(^๏ω๏^)", "(=✖ᴥ✖=)", "(๑✪ﻌ✪๑)", "(ﾐⓛ⩊ⓛﾐ)",
    "(=ಠᆽಠ=)", "(^・x・^)", "(=^ ◡ ^=)", "(=^･ｪ･^=)", "(=ↀ‥ↀ=)",
    "(₌♥﹏♥₌)", "(=✪ᴗ✪=)", "(=＞ᆺ＜=)", "(=🝦 ༝ 🝦=)", "(=ↀꞈↀ=)",
]

// The twenty-first face, and the only one that is never in the rotation: the
// splash for a launch the app could not choose a theme for. Fitted on its own
// rather than to the set's shared point size — nothing is going to appear
// beside it or after it, so there is nothing for it to match.
let fallbackFace = "ฅ^•ﻌ•^ฅ"
let resDir = CommandLine.arguments[1]
let outDir = "\(resDir)/drawable-nodpi"

// Square, because the splash screen scales the icon into a square slot: a
// wide canvas is stretched vertically to fill it. The face keeps its real
// proportions by being drawn inside a square instead.
let side = 1024

// ...and the slot is masked to a circle two thirds of that square across —
// the documented splash-icon safe zone (a 240dp icon with 160dp visible).
let safeRadius = CGFloat(side) / 3

let gray = NSColor(red: 0x8B/255.0, green: 0x89/255.0, blue: 0x81/255.0, alpha: 1)

func attributes(_ size: CGFloat) -> [NSAttributedString.Key: Any] {
    [.font: NSFont.systemFont(ofSize: size), .foregroundColor: gray]
}

/// Draws `face` into a fresh `side`x`side` bitmap with the text box's top-left
/// at `at`, and reports where the *ink* actually landed. The two differ by
/// more than a little: a line box carries the font's full ascent and descent,
/// while these faces are short and sit wherever their own glyphs sit, so
/// centering the box leaves the cat visibly off-centre.
func draw(_ face: String, size: CGFloat, at: NSPoint) -> (rep: NSBitmapImageRep, ink: NSRect) {
    let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: side, pixelsHigh: side,
                               bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
                               colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    NSColor.clear.setFill()
    NSRect(x: 0, y: 0, width: side, height: side).fill()
    NSAttributedString(string: face, attributes: attributes(size)).draw(at: at)
    NSGraphicsContext.restoreGraphicsState()
    return (rep, inkBounds(rep))
}

/// The alpha bounding box, in the same bottom-left origin AppKit draws in.
func inkBounds(_ rep: NSBitmapImageRep) -> NSRect {
    guard let data = rep.bitmapData else { return .zero }
    let rowBytes = rep.bytesPerRow
    let samples = rep.samplesPerPixel
    var minX = side, minY = side, maxX = -1, maxY = -1
    for y in 0..<side {
        let row = data + y * rowBytes
        for x in 0..<side where row[x * samples + 3] != 0 {
            if x < minX { minX = x }
            if x > maxX { maxX = x }
            if y < minY { minY = y }
            if y > maxY { maxY = y }
        }
    }
    if maxX < 0 { return .zero }
    // Bitmap rows run top-down; AppKit's drawing origin is bottom-left.
    return NSRect(x: CGFloat(minX), y: CGFloat(side - 1 - maxY),
                  width: CGFloat(maxX - minX + 1), height: CGFloat(maxY - minY + 1))
}

// One point size for the whole set, so the cats don't resize as the animation
// cycles: measured at a reference size, then scaled by whichever face is
// tightest against the safe circle. What binds is the ink box's *corner*
// touching that circle, not its width — these faces are wide and short, so
// the diagonal is what runs out first.
let reference: CGFloat = 200
let inks = faces.map { draw($0, size: reference, at: NSPoint(x: 20, y: CGFloat(side) / 2)).ink }
let fit = inks.map { 2 * safeRadius / sqrt($0.width * $0.width + $0.height * $0.height) }.min()!
let fontSize = (reference * fit).rounded(.down)

/// Draws one face centred by its INK and writes it to `name`.png.
///
/// Draw once to find out where the ink lands, then again shifted by exactly
/// how far that was from the middle. Cheaper than deriving it from font
/// metrics, and correct for every face without special cases.
@discardableResult
func bake(_ face: String, size: CGFloat, as name: String, label: String) -> NSRect {
    let start = NSPoint(x: 20, y: CGFloat(side) / 2)
    let probe = draw(face, size: size, at: start).ink
    let centered = NSPoint(x: start.x + (CGFloat(side) - probe.width) / 2 - probe.minX,
                           y: start.y + (CGFloat(side) - probe.height) / 2 - probe.minY)
    let final = draw(face, size: size, at: centered)
    try! final.rep.representation(using: .png, properties: [:])!
        .write(to: URL(fileURLWithPath: "\(outDir)/\(name).png"))
    print("\(label) '\(face)' pt=\(size) ink=\(Int(final.ink.width))x\(Int(final.ink.height))"
        + " centre=(\(Int(final.ink.midX)), \(Int(final.ink.midY)))")
    return final.ink
}

for (i, face) in faces.enumerated() {
    bake(face, size: fontSize, as: "splash_cat_\(i)", label: "frame \(i)")
}

// The fallback face gets its own fit against the same safe circle: it is alone
// on its splash, so the set's shared point size — which is only there to stop
// the cats resizing as the animation cycles — would just make it small for a
// reason that does not apply to it.
let fallbackInk = draw(fallbackFace, size: reference, at: NSPoint(x: 20, y: CGFloat(side) / 2)).ink
let fallbackSize = (reference * 2 * safeRadius
    / sqrt(fallbackInk.width * fallbackInk.width + fallbackInk.height * fallbackInk.height))
    .rounded(.down)
bake(fallbackFace, size: fallbackSize, as: "splash_cat_fallback", label: "fallback")

// ---------------------------------------------------------------------------
// The resources around the PNGs.
//
// The system draws the splash before a line of this app has run, so the only
// thing that can vary per launch is WHICH THEME the splash is drawn with —
// MainActivity registers one through SplashScreen.setSplashScreenTheme, and it
// takes effect on the next launch. That is already how the accent reaches the
// splash; the launch ORDER rides along the same wire. Since a theme is a
// static resource, every order that can be picked has to exist as one: the
// orders below are baked here, and the choice at launch is which of them to
// name.
// ---------------------------------------------------------------------------

let drawableDir = "\(resDir)/drawable"
let valuesV31Dir = "\(resDir)/values-v31"

/// The themes that reach the splash, as (array key, style name, parent).
/// Every one of them, Dynamic included, names a SplashTheme.* from
/// values/themes.xml — deliberately not Theme.Browser, whose ink is the
/// FALLBACK the system draws when no theme is registered at all. Only the
/// NAMES are known here: a face style inherits its parent's colours rather
/// than restating them, so adding a swatch means adding a line here and
/// nothing else.
///
/// The three tintable special themes (TUI, Nothing, Aero) appear once per
/// ACCENT, because a swatch tints them rather than being replaced by them: the
/// ink is the accent's and only the ground is the look's, so the pair is what
/// has to be named. 98 replaces the accent instead, and keeps a single entry.
let splashThemes: [(key: String, style: String, parent: String)] = [
    ("dynamic", "SplashTheme.Dynamic", "SplashTheme.Dynamic"),
    ("graphite", "SplashTheme.Graphite", "SplashTheme.Graphite"),
    ("red", "SplashTheme.Red", "SplashTheme.Red"),
    ("orange", "SplashTheme.Orange", "SplashTheme.Orange"),
    ("yellow", "SplashTheme.Yellow", "SplashTheme.Yellow"),
    ("green", "SplashTheme.Green", "SplashTheme.Green"),
    ("teal", "SplashTheme.Teal", "SplashTheme.Teal"),
    ("blue", "SplashTheme.Blue", "SplashTheme.Blue"),
    ("purple", "SplashTheme.Purple", "SplashTheme.Purple"),
    ("pink", "SplashTheme.Pink", "SplashTheme.Pink"),
    ("tui_dynamic", "SplashTheme.Tui.Dynamic", "SplashTheme.Tui.Dynamic"),
    ("tui_graphite", "SplashTheme.Tui.Graphite", "SplashTheme.Tui.Graphite"),
    ("tui_red", "SplashTheme.Tui.Red", "SplashTheme.Tui.Red"),
    ("tui_orange", "SplashTheme.Tui.Orange", "SplashTheme.Tui.Orange"),
    ("tui_yellow", "SplashTheme.Tui.Yellow", "SplashTheme.Tui.Yellow"),
    ("tui_green", "SplashTheme.Tui.Green", "SplashTheme.Tui.Green"),
    ("tui_teal", "SplashTheme.Tui.Teal", "SplashTheme.Tui.Teal"),
    ("tui_blue", "SplashTheme.Tui.Blue", "SplashTheme.Tui.Blue"),
    ("tui_purple", "SplashTheme.Tui.Purple", "SplashTheme.Tui.Purple"),
    ("tui_pink", "SplashTheme.Tui.Pink", "SplashTheme.Tui.Pink"),
    ("nothing_dynamic", "SplashTheme.Nothing.Dynamic", "SplashTheme.Nothing.Dynamic"),
    ("nothing_graphite", "SplashTheme.Nothing.Graphite", "SplashTheme.Nothing.Graphite"),
    ("nothing_red", "SplashTheme.Nothing.Red", "SplashTheme.Nothing.Red"),
    ("nothing_orange", "SplashTheme.Nothing.Orange", "SplashTheme.Nothing.Orange"),
    ("nothing_yellow", "SplashTheme.Nothing.Yellow", "SplashTheme.Nothing.Yellow"),
    ("nothing_green", "SplashTheme.Nothing.Green", "SplashTheme.Nothing.Green"),
    ("nothing_teal", "SplashTheme.Nothing.Teal", "SplashTheme.Nothing.Teal"),
    ("nothing_blue", "SplashTheme.Nothing.Blue", "SplashTheme.Nothing.Blue"),
    ("nothing_purple", "SplashTheme.Nothing.Purple", "SplashTheme.Nothing.Purple"),
    ("nothing_pink", "SplashTheme.Nothing.Pink", "SplashTheme.Nothing.Pink"),
    ("aero_dynamic", "SplashTheme.Aero.Dynamic", "SplashTheme.Aero.Dynamic"),
    ("aero_graphite", "SplashTheme.Aero.Graphite", "SplashTheme.Aero.Graphite"),
    ("aero_red", "SplashTheme.Aero.Red", "SplashTheme.Aero.Red"),
    ("aero_orange", "SplashTheme.Aero.Orange", "SplashTheme.Aero.Orange"),
    ("aero_yellow", "SplashTheme.Aero.Yellow", "SplashTheme.Aero.Yellow"),
    ("aero_green", "SplashTheme.Aero.Green", "SplashTheme.Aero.Green"),
    ("aero_teal", "SplashTheme.Aero.Teal", "SplashTheme.Aero.Teal"),
    ("aero_blue", "SplashTheme.Aero.Blue", "SplashTheme.Aero.Blue"),
    ("aero_purple", "SplashTheme.Aero.Purple", "SplashTheme.Aero.Purple"),
    ("aero_pink", "SplashTheme.Aero.Pink", "SplashTheme.Aero.Pink"),
    ("98", "SplashTheme.Ninety8", "SplashTheme.Ninety8"),
]

let header = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
let generated = "<!-- Generated by tools/render_splash_cats.swift. Do not edit by hand. -->\n"

for (i, face) in faces.enumerated() {
    try! (header + generated + """
    <!-- Frame \(i) of the splash animation: \(face). The wrapper exists only to
         carry the tint — an <animation-list> item takes a drawable and a
         duration and nothing else, so the colour has to hang off each frame
         individually. The PNG is neutral grey with the shape in its alpha;
         ?attr/splashInk is what's actually seen, a theme attribute rather than
         a plain @color so the accent the splash is launched with decides it
         (see values/attrs.xml). -->
    <bitmap xmlns:android="http://schemas.android.com/apk/res/android"
        android:src="@drawable/splash_cat_\(i)"
        android:tint="?attr/splashInk"
        android:gravity="fill" />
    """).write(toFile: "\(drawableDir)/splash_cat_frame_\(i).xml", atomically: true, encoding: .utf8)
}

/// One shuffle per order, from a fixed seed so a regeneration doesn't rewrite
/// every file for nothing. Order *k* is then made to start with face *k*, so
/// the twenty orders open on twenty different cats — the first frame is the
/// one a fast launch actually shows, and it is the whole point of the shuffle
/// that it isn't always the same face.
func order(_ k: Int) -> [Int] {
    var state = UInt64(k) &* 6364136223846793005 &+ 1442695040888963407
    func next(_ bound: Int) -> Int {
        state = state &* 6364136223846793005 &+ 1442695040888963407
        return Int((state >> 33) % UInt64(bound))
    }
    var idx = Array(faces.indices)
    for i in stride(from: idx.count - 1, to: 0, by: -1) { idx.swapAt(i, next(i + 1)) }
    let at = idx.firstIndex(of: k)!
    idx.swapAt(0, at)
    return idx
}

for k in faces.indices {
    let items = order(k).map {
        "    <item android:drawable=\"@drawable/splash_cat_frame_\($0)\" android:duration=\"280\" />"
    }.joined(separator: "\n")
    try! (header + generated + """
    <!--
        The splash icon on API 31+, in launch order \(k) of \(faces.count). An
        <animation-list> (i.e. AnimationDrawable) rather than an
        AnimatedVectorDrawable because these are glyphs, not shapes — a
        VectorDrawable can't render text, and half of these characters are only
        reachable through per-glyph font fallback anyway, so they're baked to
        PNG (see CLAUDE.md) instead of trusted to whatever fonts a device ships.

        The platform starts the splash icon when it's Animatable, which
        AnimationDrawable is; oneshot="false" keeps it looping for as long as
        MainActivity holds the splash up — which is at least SPLASH_MIN_MS, so
        there is always more than one cat to see.

        The orders are the same twenty frames shuffled, one file each, because
        a drawable cannot be chosen at splash time — only a theme can, and the
        theme naming this one is picked at random by the launch before it (see
        values-v31/splash_orders.xml and MainActivity.followThemeOnSplash).
        Only a launch that could be registered for reaches any of this; the
        other kind gets the still fallback face below.
    -->
    <animation-list xmlns:android="http://schemas.android.com/apk/res/android"
        android:oneshot="false">
    \(items)
    </animation-list>
    """).write(toFile: "\(drawableDir)/splash_cats_\(k).xml", atomically: true, encoding: .utf8)
}

// The fallback splash's icon: one still face, tinted the same way. Named by
// Theme.Browser in values-v31/themes.xml and by nothing else — it is
// deliberately outside the orders, so no cold start can draw it and no
// fallback launch can draw anything else.
try! (header + generated + """
<!-- The tint wrapper for the fallback face, \(fallbackFace) — the same job
     splash_cat_frame_N does for the animation's frames: a PNG cannot carry
     ?attr/splashInk itself. Resolved against Theme.Browser here, so the app's
     muted grey, on plain white or black. -->
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@drawable/splash_cat_fallback"
    android:tint="?attr/splashInk"
    android:gravity="fill" />
""").write(toFile: "\(drawableDir)/splash_cat_fallback_frame.xml", atomically: true, encoding: .utf8)

try! (header + generated + """
<!--
    The splash icon a launch gets when no theme was registered for it: one
    still face, and the ONLY icon that is not part of an order.

    Named splash_cats_fallback, like the orders it stands outside of, and
    NOT splash_cat_fallback: that is the baked PNG's own name, and a
    drawable resource that names itself is a resource definition CYCLE —
    lint refuses to assemble a release over it, and which of the two an
    ordinary reference resolves to is a coin toss between configurations.

    An <animation-list> of exactly one frame, and it has to be: the platform
    draws windowSplashScreenAnimatedIcon only when the drawable is Animatable,
    and silently draws NOTHING for a plain <bitmap> — verified on device, where
    the fallback splash came up as a bare white screen with the ground correct
    and no cat in it. AnimationDrawable is the cheapest Animatable there is, so
    the still face is a cycle of length one.

    oneshot="true" and a duration long enough that the single frame is never
    re-scheduled: there is nothing to advance to.
-->
<animation-list xmlns:android="http://schemas.android.com/apk/res/android"
    android:oneshot="true">
    <item android:drawable="@drawable/splash_cat_fallback_frame" android:duration="10000" />
</animation-list>
""").write(toFile: "\(drawableDir)/splash_cats_fallback.xml", atomically: true, encoding: .utf8)

var orders = header + generated + """
<!--
    A theme per (accent, launch order) pair, plus the arrays MainActivity
    picks one out of.

    The cross product is unavoidable: setSplashScreenTheme takes ONE theme id,
    and both the ink and the frame order have to be in it. Each style adds the
    order to a colour that already exists, by inheriting the swatch's own
    theme from values/themes.xml — so the colours live in exactly one place
    and this file only ever names them. API 31+ only, because the animated
    splash icon is; below that the splash is the window background.

    Everything here is the COLD-START splash. The other one is Theme.Browser's
    own, in values-v31/themes.xml, which no theme registered from the app can
    reach.
-->
<resources>

"""
for theme in splashThemes {
    for k in faces.indices {
        orders += """
            <style name="\(theme.style).Order\(k)" parent="\(theme.parent)">
                <item name="android:windowSplashScreenAnimatedIcon">@drawable/splash_cats_\(k)</item>
                <item name="android:windowSplashScreenAnimationDuration">1000</item>
            </style>

        """
    }
}
orders += "\n"
for theme in splashThemes {
    let items = faces.indices.map { "        <item>@style/\(theme.style).Order\($0)</item>" }
        .joined(separator: "\n")
    orders += """
        <array name="splash_orders_\(theme.key)">
    \(items)
        </array>

    """
}
orders += "</resources>\n"
try! orders.write(toFile: "\(valuesV31Dir)/splash_orders.xml", atomically: true, encoding: .utf8)
print("wrote \(faces.count) frames + 1 fallback, \(faces.count) orders, "
    + "\(splashThemes.count * faces.count) themes")
