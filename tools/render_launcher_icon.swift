// Regenerates the launcher icon's foreground glyph:
//
//   swift tools/render_launcher_icon.swift app/src/main/res/drawable-nodpi
//
// Same reason as the splash frames (tools/render_splash_cats.swift): this
// face mixes Latin punctuation with katakana (マ), so it only comes out
// right through per-glyph font fallback, which CoreText does properly. Drawn in solid black on transparent — the alpha is
// the shape, and the ink colour is applied as a tint at use, so light/dark is
// one resource swap rather than a second PNG.
import AppKit

let face = "(•˕ •マ"
let outDir = CommandLine.arguments[1]

let side = 1024

// An adaptive icon's layers are 108dp with only the inner 72dp guaranteed to
// survive the launcher's mask, and that guarantee is a CIRCLE — so what the
// face has to fit inside is a circle two thirds of the canvas across, and
// what binds is its ink box's *corner* touching that circle rather than its
// width. These faces are wide and short, so the diagonal runs out first.
let safeRadius = CGFloat(side) / 3

// ...and it does not fill that circle. The safe zone is the guarantee of what
// SURVIVES the mask, not a target — a face drawn out to it touches the edge of
// a round icon on both sides, which reads as cramped rather than as large.
// This is the breathing room, as a fraction of the safe circle.
let fill: CGFloat = 0.82
let ink = NSColor.black

func attributes(_ size: CGFloat) -> [NSAttributedString.Key: Any] {
    [.font: NSFont.systemFont(ofSize: size), .foregroundColor: ink]
}

/// Draws `face` into a fresh `side`x`side` bitmap with the text box's top-left
/// at `at`, and reports where the *ink* actually landed. The two differ by
/// more than a little: a line box carries the font's full ascent and descent,
/// while these faces are short and sit wherever their own glyphs sit, so
/// centering the box leaves the cat visibly high.
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

// Measure at a reference size, then scale so the ink's diagonal lands at
// `fill` of the safe circle.
let reference: CGFloat = 200
let start = NSPoint(x: 20, y: CGFloat(side) / 2)
let probeInk = draw(face, size: reference, at: start).ink
let fit = 2 * safeRadius * fill / sqrt(probeInk.width * probeInk.width + probeInk.height * probeInk.height)
let fontSize = (reference * fit).rounded(.down)

// Draw once to find out where the ink lands, then again shifted by exactly
// how far that was from the middle.
let placed = draw(face, size: fontSize, at: start).ink
let centered = NSPoint(x: start.x + (CGFloat(side) - placed.width) / 2 - placed.minX,
                       y: start.y + (CGFloat(side) - placed.height) / 2 - placed.minY)
let final = draw(face, size: fontSize, at: centered)
try! final.rep.representation(using: .png, properties: [:])!
    .write(to: URL(fileURLWithPath: "\(outDir)/ic_launcher_cat.png"))
print("'\(face)' pt=\(fontSize) canvas=\(side) ink=\(Int(final.ink.width))x\(Int(final.ink.height))"
    + " centre=(\(Int(final.ink.midX)), \(Int(final.ink.midY)))")
