package com.yuku.browser.core

/**
 * The three lists the blocker subscribes to, one per switch on the ad blocker
 * screen. All three are feeds OpenWrt's `adblock` package (or its upstream
 * providers) ships, picked for being the most-used and most actively
 * maintained of their kind rather than for breadth of choice — a wall of
 * twenty interchangeable compilations is a worse answer than three lists that
 * are right.
 */
data class BlocklistFeed(
    val id: String,
    val title: String,
    val url: String,
    val rule: FeedRule,
    /** Whether this feed's cosmetic (`##selector`) rules are worth keeping. */
    val cosmetic: Boolean = false,
)

/**
 * How a feed's lines carry their domain. These mirror adblock's `rule` field:
 * `feed 1` is [Domains], `feed 0.0.0.0 2` is [Hosts], `feed || 3 [|^]` is
 * [AdblockSyntax].
 */
enum class FeedRule {
    /** One bare domain per line. */
    Domains,

    /** A hosts file: `0.0.0.0 example.com` — the domain is the second field. */
    Hosts,

    /** AdGuard/uBlock syntax: `||example.com^`, plus `##selector` cosmetics. */
    AdblockSyntax,

    /**
     * Sniff per line. Used for the user's own lists and pasted rules, where
     * the format isn't declared up front — every real list is one of the three
     * above, and they're distinguishable line by line.
     */
    Auto,
}

object Feeds {

    /**
     * The main list. HaGeZi's Pro is the most widely used and most actively
     * maintained general DNS blocklist going (rebuilt daily, ~225k domains,
     * and one of adblock's own feeds); "Pro" is its balanced tier — the one
     * above it starts trading site breakage for coverage.
     *
     * The `wildcard` + `-onlydomains` variant is the one to take: it has been
     * compressed so a parent domain stands in for all its subdomains, which
     * is exactly what [AdBlocker]'s suffix walk does with an entry anyway.
     */
    val ADS = BlocklistFeed(
        id = "ads",
        title = "HaGeZi Pro",
        url = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/pro-onlydomains.txt",
        rule = FeedRule.Domains,
    )

    /**
     * CNAME-cloaked trackers, which are the ones an ordinary blocklist cannot
     * catch: the tracker is served from a subdomain of the site you're on
     * (`metrics.example.com`) that CNAMEs to the tracking company, so it looks
     * first-party from every angle except the DNS record. Only an enumerated
     * list of those disguises works, and AdGuard maintains it — it's adblock's
     * default tracking feed.
     */
    val TRACKERS = BlocklistFeed(
        id = "trackers",
        title = "AdGuard CNAME trackers",
        url = "https://raw.githubusercontent.com/AdguardTeam/cname-trackers/master/data/combined_disguised_trackers_justdomains.txt",
        rule = FeedRule.Domains,
    )

    /**
     * Cookie/consent banners. EasyList's own cookie list, as maintained by
     * fanboy — the list every desktop blocker uses for this.
     *
     * It is mostly *cosmetic* rules (~15k generic `##selector` hides plus ~7k
     * site-specific ones) rather than domains, because most banners are markup
     * the site itself renders rather than a third-party script that can be cut
     * off at the network. Both halves are used: the domains go into the same
     * index as everything else, the selectors into [CosmeticFilters].
     */
    val COOKIES = BlocklistFeed(
        id = "cookies",
        title = "EasyList Cookie List",
        url = "https://secure.fanboy.co.nz/fanboy-cookiemonster.txt",
        rule = FeedRule.AdblockSyntax,
        cosmetic = true,
    )
}
