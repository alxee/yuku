package com.yuku.browser.core

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UrlUtils {

    private val LOOKS_LIKE_HOST = Regex("^[\\w-]+(\\.[\\w-]+)+(:\\d+)?([/?#].*)?$")

    // A bare host with an explicit port and no dot — "localhost:8080",
    // "nas:5000". Nobody searches for that shape, and it's how a LAN service
    // is usually addressed.
    private val HOST_WITH_PORT = Regex("^[\\w-]+:\\d+([/?#].*)?$")

    // Bare "localhost", with or without a path — the one single-label name
    // worth taking as an address without a port to disambiguate it.
    private val LOCALHOST = Regex("^localhost([/?#].*)?$", RegexOption.IGNORE_CASE)

    // Bracketed IPv6 literal, optionally with a port: "[::1]", "[fe80::1]:8080".
    private val IPV6_LITERAL = Regex("^\\[[0-9A-Fa-f:.]+](:\\d+)?([/?#].*)?$")

    private val IPV4_LITERAL = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

    // Suffixes reserved for names that only ever resolve on the local network
    // (mDNS and the usual router-assigned search domains).
    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home", ".internal", ".home.arpa")

    /** The host part of omnibox input already known to look like a host. */
    private fun hostOf(text: String): String {
        val authority = text.substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.startsWith("[")) return authority.substringBefore(']').removePrefix("[")
        return authority.substringBeforeLast(':').ifEmpty { authority }
    }

    private fun isIpv4(host: String): Boolean {
        val m = IPV4_LITERAL.matchEntire(host) ?: return false
        return m.groupValues.drop(1).all { it.toInt() <= 255 }
    }

    /**
     * Hosts that can only be reached over the local network. These get http://
     * rather than https:// — a router admin page, a NAS, a dev server or a
     * printer serves plain HTTP (or a self-signed cert that fails anyway), so
     * defaulting to https turns "192.168.1.1" into a connection error, which is
     * indistinguishable from the address being wrong.
     */
    private fun isLocalHost(host: String): Boolean {
        val h = host.lowercase()
        if (h == "localhost" || h == "::1" || h == "0.0.0.0") return true
        if (LOCAL_SUFFIXES.any { h.endsWith(it) }) return true
        if (!h.contains('.')) return true // single-label name, i.e. a LAN name
        if (isIpv4(h)) return true
        // Unique-local (fc00::/7) and link-local (fe80::/10) IPv6.
        if (h.contains(':')) return h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe8") ||
            h.startsWith("fe9") || h.startsWith("fea") || h.startsWith("feb")
        return false
    }

    // Exact query-param names known to carry nothing but tracking/attribution
    // data, safe to drop on ANY domain because the name itself is a branded
    // ad-click/analytics id — no legitimate site would coin a param literally
    // called "fbclid" or "gclid" for something a link needs to work. Covers
    // cross-site ad-click ids, the analytics suites (utm_*, matched by prefix
    // below), and YouTube's per-share "si" token.
    private val GLOBAL_TRACKING_PARAM_NAMES = setOf(
        // YouTube
        "si",
        // Google/DoubleClick ads + Analytics linker params (gad_source and
        // gad_campaignid are Google Ads' newer "aggregate" tags, appended to
        // the landing-page URL of literally any advertiser running Google
        // Ads — not tied to any one site, same as gclid)
        "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "srsltid", "_ga", "_gl",
        "gad_source", "gad_campaignid", "usqp",
        // Google Ads "Hotel/Search Ads" auto-tag block, always tracking-only
        "hsa_acc", "hsa_ad", "hsa_cam", "hsa_grp", "hsa_kw", "hsa_la",
        "hsa_mt", "hsa_net", "hsa_ol", "hsa_src", "hsa_tgt", "hsa_ver",
        // Other ad networks / social
        "fbclid", "fbadid", "fb_action_ids", "fb_action_types", "fb_comment_id",
        "fb_ref", "fb_source", "igshid", "igsh", "msclkid",
        "yclid", "ysclid", "ym_tracking_id", "twclid", "tw_medium", "tw_profile_id", "tw_source",
        "ttclid", "li_fat_id", "ref_src",
        "mc_cid", "mc_eid", "_hsenc", "_hsmi", "__hsfp", "__hssc", "__hstc", "hsCtaTracking",
        "vero_id", "vero_conv", "mkt_tok", "epik", "s_kwcid", "s_cid",
        "adobe_mc_ref", "adobe_mc_sdid", "oly_anon_id", "oly_enc_id",
        "elqTrackId", "elq", "elqaid", "elqat", "elqak", "elqCampaignId", "cmpid",
        // Affiliate networks (each id is a unique per-network branded name,
        // never a param a site would independently need for content)
        "irclickid", "ir_campaignid", "ir_adid", "awc", "sscid", "rb_clickid",
        "admitad_uid", "cjevent", "cjdata", "btag", "a8", "erid",
        "guccounter", "guce_referrer", "guce_referrer_sig",
        // Mobile attribution SDKs (AppsFlyer, Adjust, Blueshift)
        "af_xp", "af_ad", "af_adset", "af_click_lookback", "af_force_deeplink",
        "adj_campaign", "adj_creative", "adj_label",
        "adjust_campaign", "adjust_creative", "adjust_tracker", "adjust_referrer", "adjust_adgroup",
        "gps_adid", "bsft_clkid", "bsft_eid", "bsft_mid", "bsft_uid", "bsft_aaid", "bsft_ek",
        // "int_"/"itm_" mirror utm_'s source/medium/campaign/content/term
        // shape and are, by convention, tracking-only — never a real param
        "int_content", "int_term", "int_source", "int_medium", "int_campaign",
        "itm_source", "itm_medium", "itm_campaign", "itm_content", "itm_term",
        "wt_mc", "xtor", "tduid", "vc_lpp",
    )

    private val GLOBAL_TRACKING_PARAM_PREFIXES = listOf("utm_", "pk_", "mtm_")

    // Everything below is a plain English word, an abbreviation, or a name
    // one company happens to use — "ref", "tag", "content-id", "trk" could
    // just as easily be a REQUIRED param on some other site (a referral code
    // input, a blog's tag filter, a CMS content id). Gate these by the
    // registrable domain they're actually known to come from, so stripping
    // them never touches an unrelated site's real functionality.
    private val DOMAIN_SCOPED_TRACKING_PARAM_NAMES: Map<String, Set<String>> = mapOf(
        "amazon" to setOf(
            "tag", "linkCode", "camp", "creative", "creativeASIN", "ascsubtag",
            "pd_rd_i", "pd_rd_r", "pd_rd_w", "pd_rd_wg",
            "pf_rd_p", "pf_rd_r", "pf_rd_s", "pf_rd_t", "pf_rd_i", "pf_rd_m",
            "ref_", "smid", "content-id", "dib", "dib_tag", "_encoding", "psc",
            "ref", "referrer",
        ),
        "linkedin" to setOf("trk", "trkCampaign"),
        "alibaba" to setOf("spm", "scm"),
        "aliexpress" to setOf("spm", "scm"),
        "taobao" to setOf("spm", "scm"),
        "bing" to setOf("ocid", "cvid"),
        "microsoft" to setOf("ocid"),
        "duckduckgo" to setOf("ia", "iax", "iar"),
    )

    // Registrable-domain match, e.g. "www.amazon.co.uk" / "amazon.de" both
    // key off "amazon" — good enough since these are exact single-word
    // company names, not substrings that could false-positive elsewhere.
    private fun domainKey(host: String): String? =
        DOMAIN_SCOPED_TRACKING_PARAM_NAMES.keys.firstOrNull { host.contains(".$it.") || host.startsWith("$it.") }

    private fun isTrackingParam(name: String, domainKey: String?) =
        name in GLOBAL_TRACKING_PARAM_NAMES ||
            GLOBAL_TRACKING_PARAM_PREFIXES.any { name.startsWith(it) } ||
            (domainKey != null && name in DOMAIN_SCOPED_TRACKING_PARAM_NAMES.getValue(domainKey))

    // Amazon doesn't only tag links via the query string — the referral tag
    // is a whole PATH segment, e.g. .../dp/B0123456789/ref=sr_1_3 — so it
    // survives a query-only strip untouched. Matches "ref=..." and the
    // "ref_=..." variant some Amazon pages use. Scoped to amazon.* for the
    // same reason as the domain-scoped query params above.
    private val TRACKING_PATH_SEGMENT = Regex("^ref_?=.*$")

    private fun isTrackingPathSegment(segment: String, domainKey: String?) =
        domainKey == "amazon" && TRACKING_PATH_SEGMENT.matches(segment)

    /**
     * Drops known tracking/attribution query params (YouTube's `si`,
     * Amazon's affiliate/session params, `utm_*`, ad-click ids, ...) and
     * Amazon's `/ref=...` path segment from a URL, leaving everything else —
     * including params that actually affect what content loads — untouched.
     * Returns the URL unchanged if it doesn't parse or carries neither.
     */
    fun stripTrackingParams(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        val domainKey = domainKey(parsed.host)
        val names = parsed.queryParameterNames
        val pathSegments = parsed.pathSegments
        val hasTrackingQuery = names.any { isTrackingParam(it, domainKey) }
        val hasTrackingPathSegment = pathSegments.any { isTrackingPathSegment(it, domainKey) }
        if (!hasTrackingQuery && !hasTrackingPathSegment) return url

        val builder = parsed.newBuilder()
        if (hasTrackingQuery) {
            builder.query(null)
            for (name in names) {
                if (isTrackingParam(name, domainKey)) continue
                for (value in parsed.queryParameterValues(name)) {
                    builder.addQueryParameter(name, value)
                }
            }
        }
        if (hasTrackingPathSegment) {
            // Removed back-to-front so earlier indices stay valid as later
            // ones are removed.
            pathSegments.indices.reversed().forEach { i ->
                if (isTrackingPathSegment(pathSegments[i], domainKey)) builder.removePathSegment(i)
            }
        }
        return builder.build().toString()
    }

    /** Turn omnibox input into either a URL to load or a search results URL. */
    fun toUrlOrSearch(input: String, engine: SearchEngine = SearchEngine.DuckDuckGo): String {
        val text = input.trim()
        if (text.isEmpty()) return Tab.HOME
        if (text.startsWith("http://") || text.startsWith("https://")) return text
        if (text.startsWith("about:") || text.startsWith("file:")) return text
        if (text.contains(' ')) return engine.searchUrl(text)
        val looksLikeHost = LOOKS_LIKE_HOST.matches(text) ||
            HOST_WITH_PORT.matches(text) ||
            IPV6_LITERAL.matches(text) ||
            LOCALHOST.matches(text)
        if (!looksLikeHost) return engine.searchUrl(text)
        // A dotted-quad is an IP even where it would also parse as a hostname;
        // anything else local-only gets the same treatment.
        val scheme = if (isLocalHost(hostOf(text))) "http://" else "https://"
        return scheme + text
    }

    /**
     * The site a URL belongs to: its REGISTRABLE domain, never the raw host —
     * login.google.com.attacker.tld is attacker.tld, and anything keyed by host
     * (favicons, saved logins, history rows) has to agree with that or a
     * subdomain is a different site to the browser than it is to the user.
     * OkHttp bundles the Public Suffix List, which is why it's a dep.
     * What the address bar DRAWS is [displayHost], which keeps the subdomain.
     */
    fun registrableDomain(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        parsed.topPrivateDomain()?.let { return it }
        // An IP literal or a LAN name has no registrable domain, and there the
        // port is part of the address the user typed — two dev servers on one
        // machine differ by nothing else — so keep it when it isn't the default.
        val host = parsed.host
        val defaultPort = if (parsed.isHttps) 443 else 80
        return if (parsed.port == defaultPort) host else "$host:${parsed.port}"
    }

    /**
     * The host as the address bar draws it: the whole thing, `www.` aside,
     * split at the registrable domain so the two halves can be weighted
     * differently.
     *
     * [registrableDomain] alone is not enough to READ an address by —
     * auto.ria.com and ria.com are different services, and a bar that calls
     * them both "ria.com" is reporting the wrong site. But the raw host on its
     * own is the phishing aid the registrable domain existed to avoid
     * (login.google.com.attacker.tld), so the part that decides WHO the page
     * belongs to is the part drawn in full contrast and the subdomain in front
     * of it is muted — the same answer every other browser arrived at.
     *
     * An IP literal or a LAN name has no registrable domain to split at, so it
     * is all [domain] (and keeps its non-default port, which is part of the
     * address there — two dev servers on one machine differ by nothing else).
     */
    data class DisplayHost(val subdomain: String, val domain: String) {
        val text: String get() = subdomain + domain
        val isEmpty: Boolean get() = domain.isEmpty()
    }

    fun displayHost(url: String): DisplayHost {
        val parsed = url.toHttpUrlOrNull() ?: return DisplayHost("", "")
        val host = parsed.host.removePrefix("www.")
        val defaultPort = if (parsed.isHttps) 443 else 80
        val port = if (parsed.port == defaultPort) "" else ":${parsed.port}"
        val registrable = parsed.topPrivateDomain()
        if (registrable == null || !host.endsWith(registrable)) {
            return DisplayHost("", host + port)
        }
        return DisplayHost(host.dropLast(registrable.length), registrable + port)
    }

    fun isSecure(url: String): Boolean = url.startsWith("https://")

}
