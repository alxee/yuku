package com.yuku.browser.core

/**
 * The two things a checkout form asks for that are not a login: where to send
 * it, and what to charge.
 *
 * They live in the same encrypted vault as the passwords ([PasswordStore]) and
 * not in [BrowserStore]'s plain blob, for the same reason the logins do not:
 * a home address and a card number are secrets in exactly the way a password
 * is, and the vault already has the key, the tamper detection, the backup
 * exclusion and the device lock in front of the list.
 *
 * Unlike a login, neither of these is CAPTURED from a page. There is no
 * "offer to save this card" here and that is deliberate: a login is a secret
 * the browser watched the user type into the site it belongs to, where a card
 * number scraped off a checkout form is a card number this browser took a
 * copy of without being asked, in a vault the user has not opened. Both are
 * typed once in Settings and filled from there afterwards — which is the same
 * amount of typing as the first checkout and none of it after that.
 */
data class SavedAddress(
    /** Stable across edits, so a row being changed is not a row being replaced. */
    val id: String,
    /** What the user calls it — "Home", "Work". Falls back to the street line. */
    val label: String = "",
    val name: String = "",
    val organization: String = "",
    val street: String = "",
    val city: String = "",
    val region: String = "",
    val postalCode: String = "",
    val country: String = "",
    val email: String = "",
    val phone: String = "",
    val updatedAt: Long = 0L,
) {
    /** The one line a chip has room for. */
    val summary: String
        get() = listOf(street, city, postalCode).filter { it.isNotBlank() }.joinToString(", ")

    val title: String
        get() = label.ifBlank { name.ifBlank { street.ifBlank { "Address" } } }

    /**
     * The two halves of a name, for the forms that ask for them separately.
     * Everything before the last space is the given name — wrong for some
     * names and right for most, and the alternative is asking every user for
     * three fields to serve the sites that want two.
     */
    val givenName: String get() = name.substringBeforeLast(' ', name).trim()
    val familyName: String get() = if (' ' in name) name.substringAfterLast(' ').trim() else ""
}

/**
 * One payment card. **The security code is not here and never will be.**
 *
 * It is the one field on a card whose entire purpose is to prove that the
 * person paying has the card in their hand, so a browser that stores it has
 * quietly turned a two-factor number into a one-factor one — and every card
 * network's own rules say the same thing about keeping it. Filling everything
 * else and leaving three digits to type is the arrangement every real browser
 * arrived at, and it is not a limitation.
 */
data class SavedCard(
    val id: String,
    val label: String = "",
    val cardholder: String = "",
    val number: String = "",
    /** 1-12, or 0 for a card whose expiry was left blank. */
    val expiryMonth: Int = 0,
    /** Four digits, or 0. */
    val expiryYear: Int = 0,
    val updatedAt: Long = 0L,
) {
    /** The digits, with whatever spacing the user typed taken out. */
    val digits: String get() = number.filter(Char::isDigit)

    /** All a list is allowed to show: the network's own convention. */
    val masked: String
        get() = digits.takeLast(4).let { if (it.isEmpty()) "No number" else "•••• $it" }

    val expiry: String
        get() = if (expiryMonth in 1..12 && expiryYear > 0) {
            "%02d/%s".format(expiryMonth, expiryYear.toString().takeLast(2))
        } else ""

    val title: String get() = label.ifBlank { network().ifBlank { "Card" } }

    /**
     * Which network the number belongs to, from its own prefix — the same
     * ranges the issuers publish. Only used to name a row; nothing depends
     * on it being right, and an unrecognised prefix is simply unnamed.
     */
    fun network(): String {
        val d = digits
        return when {
            d.startsWith("4") -> "Visa"
            d.length >= 2 && d.take(2).toIntOrNull() in 51..55 -> "Mastercard"
            d.length >= 4 && d.take(4).toIntOrNull() in 2221..2720 -> "Mastercard"
            d.take(2) in listOf("34", "37") -> "American Express"
            d.startsWith("6011") || d.startsWith("65") -> "Discover"
            d.take(2) in listOf("36", "38") || d.take(3).toIntOrNull() in 300..305 -> "Diners Club"
            d.startsWith("35") -> "JCB"
            else -> ""
        }
    }
}
