package com.yuku.browser.core

/**
 * A whole-look override, on top of the ordinary light/dark + accent theming:
 * where [AccentTheme] recolours the same interface, a special theme changes
 * what the interface IS — its shapes, its type and its words.
 *
 * Kept apart from [AccentTheme] rather than added to it as another swatch:
 * the accent row draws its entries as coloured dots, and "the whole UI turns
 * into a terminal" is not a colour. Free of any Compose dependency for the
 * same reason [AccentTheme] is; ui/theme maps these to shapes and palettes.
 */
enum class SpecialTheme(val label: String) {
    /**
     * The app's own look: Material shapes and type over whichever
     * [AccentTheme] is picked. Deliberately NOT offered in the special-theme
     * picker — an "off" target sitting beside two looks is a third look that
     * isn't one. It is reached by picking an accent instead (see
     * `setAccentTheme`), which is the same gesture that chooses what this
     * look IS.
     */
    Default("Default"),

    /**
     * A text-user-interface look: square corners everywhere, monospace type,
     * lowercase labels, gruvbox's two ends (a milk paper and a dark grey,
     * green and orange the only colours on either), and characters where the
     * chrome would otherwise draw glyphs — words on the toolbar's three
     * buttons, single keys everywhere else. It reaches every screen the app
     * draws, Settings included — a look the settings screen is exempt from
     * is a look the user cannot see themselves choosing.
     */
    Tui("TUI"),

    /**
     * Nothing's look: a monochrome canvas (warm off-white one way, OLED
     * black the other) with a single red kept for the accent, a dot-matrix
     * face on the headings and titles, a mono face on the labels — which are
     * cased UP, where the TUI's are cased down — and corners capped rather
     * than squared. Applied app-wide, exactly as the TUI is.
     */
    Nothing("Nothing"),

    /**
     * Windows 98's look: a teal desktop under silver windows, one navy blue
     * spent on selection and title bars, square corners everywhere, and
     * every edge drawn as a two-tone BEVEL — a light source fixed at the
     * top left, so a raised control catches the light on its top and left
     * and casts on its bottom and right, and a sunken one does the reverse.
     *
     * That bevel is the whole language. The other looks here differ from
     * the app's own by their palette and their type; this one differs by
     * having no flat edges at all — depth is not a shadow under a card, it
     * is drawn into the four sides of every button, field and window as
     * hard one-pixel lines. Applied app-wide, exactly as the other two are.
     */
    Ninety8("98"),

    /**
     * Aero: the glass decade — Frutiger Aero by way of Windows 7, Vista and
     * Aqua. A sky behind the app, chrome made of tinted glass over it, and
     * corners blown out into lozenges rather than cut or capped.
     *
     * The one look here whose surfaces are TRANSLUCENT, which is what makes
     * it different in kind from the other three rather than in palette: the
     * toolbar and the sheets are drawn over the live page already, so under
     * this theme the page is genuinely visible through them, and depth is
     * carried by how light behaves in a thick wet object — a specular gloss
     * ending in a hard line, a refraction band inside the rim, and a bounce
     * off whatever is underneath.
     *
     * The glass is TINTED, and deliberately: Windows 7 shipped a colour
     * picker for its chrome, and colour in the material is the whole
     * difference between this and the near-clear glass interfaces have gone
     * back to. It is tintable by the accent for the same reason — the
     * swatch is the colorization slider.
     */
    Aero("Aero"),
}
