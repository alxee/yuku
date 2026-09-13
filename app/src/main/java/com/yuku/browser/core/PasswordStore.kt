package com.yuku.browser.core

import android.app.Application
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * One saved login. Keyed by [host] + [username] — the same pair the site's own
 * form fills in, and the same pair a second account on one site differs by.
 *
 * [host] is a registrable domain ([UrlUtils.registrableDomain]), not an
 * origin: it is the key every other list in this app already uses for a site
 * (tabs, history, bookmarks, favicons), and it is what makes one save cover
 * `accounts.example.com` and `www.example.com` both. That is looser than a
 * real browser's origin-scoped password store, and deliberately so here —
 * but it is the reason nothing is ever filled without the page having a
 * password field the user is actually looking at.
 */
data class SavedPassword(
    val host: String,
    val username: String,
    val password: String,
    val updatedAt: Long = 0L,
)

/**
 * The saved-password vault: the one piece of this app's state that is a
 * secret, and so the one piece that does not go in [BrowserStore]'s plain
 * JSON blob.
 *
 * It is a single file, `filesDir/passwords/vault.bin`, encrypted with
 * AES-256-GCM under a key that lives in the AndroidKeyStore and never leaves
 * it. GCM rather than CBC because the file needs to be tamper-evident as well
 * as unreadable: a vault someone has edited a byte of fails to authenticate
 * and is discarded, rather than decrypting to something plausible. A fresh
 * 12-byte IV is generated per write (the cipher's own, read back out of it
 * rather than supplied) and stored in front of the ciphertext — reusing an IV
 * under one GCM key is the one mistake that actually breaks it.
 *
 * The key is not backed up, and neither should the file be: a vault restored
 * onto a device whose keystore never held that key is undecryptable, so both
 * are excluded from backup in the manifest's backup rules. A vault that fails
 * to decrypt anyway is dropped rather than retried — there is nothing to
 * recover, and the alternative is an app that cannot save a password because
 * of a file it can no longer read.
 *
 * Writes go through a single background thread: mutations are made in memory
 * (so the UI updates on the same frame it asked) and the file is rewritten
 * whole behind them, temp-then-rename like the other stores here. The vault is
 * a few KB even for a heavy user, so there is no incremental format.
 */
class PasswordStore(app: Application) {

    private val dir = File(app.filesDir, "passwords")
    private val file = File(dir, "vault.bin")
    private val io = Executors.newSingleThreadExecutor()

    private val _entries = MutableStateFlow<List<SavedPassword>>(emptyList())
    val entries: StateFlow<List<SavedPassword>> = _entries.asStateFlow()

    /**
     * Sites the user answered "Never" for. Kept in the vault rather than in
     * settings because it is a list of sites the user has a login on, which
     * is the same kind of fact as the logins themselves.
     */
    private val _neverSaved = MutableStateFlow<Set<String>>(emptySet())
    val neverSaved: StateFlow<Set<String>> = _neverSaved.asStateFlow()

    /**
     * The other two things a form asks for that are worth keeping — see
     * [SavedAddress] and [SavedCard]. In this file rather than in a second
     * one because they are the same kind of secret under the same key, and a
     * second encrypted file would be a second thing to keep in step with the
     * keystore, the backup rules and the device lock.
     */
    private val _addresses = MutableStateFlow<List<SavedAddress>>(emptyList())
    val addresses: StateFlow<List<SavedAddress>> = _addresses.asStateFlow()

    private val _cards = MutableStateFlow<List<SavedCard>>(emptyList())
    val cards: StateFlow<List<SavedCard>> = _cards.asStateFlow()

    init {
        // Read on the calling thread, like BrowserStore's blob and for the
        // same reason: the first page can be loaded — and a login form on it
        // focused — before an IO job would have landed, and a vault that
        // arrives late looks exactly like an empty one.
        load()
    }

    // ------------------------------------------------------------- reading

    /** Every saved login for [host], most recently updated first. */
    fun forHost(host: String): List<SavedPassword> {
        if (host.isBlank()) return emptyList()
        return _entries.value.filter { it.host.equals(host, ignoreCase = true) }
            .sortedByDescending { it.updatedAt }
    }

    fun isNeverSaved(host: String): Boolean =
        host.isNotBlank() && _neverSaved.value.any { it.equals(host, ignoreCase = true) }

    /**
     * What a captured credential means for what is already stored: nothing to
     * do, a new login, or a changed password for one already here.
     */
    fun classify(host: String, username: String, password: String): Change {
        val existing = forHost(host).firstOrNull { it.username == username }
        return when {
            existing == null -> Change.New
            existing.password == password -> Change.Unchanged
            else -> Change.Updated
        }
    }

    enum class Change { New, Updated, Unchanged }

    // ------------------------------------------------------------- writing

    fun save(host: String, username: String, password: String) {
        if (host.isBlank() || password.isEmpty()) return
        _entries.update { list ->
            val existing = list.firstOrNull {
                it.host.equals(host, ignoreCase = true) && it.username == username
            }
            val entry = SavedPassword(
                host = host,
                username = username,
                password = password,
                updatedAt = System.currentTimeMillis(),
            )
            list.filterNot { it === existing } + entry
        }
        // Saving a site is the clearest possible statement that it is not a
        // site the user never wants saved.
        _neverSaved.update { it.filterNot { h -> h.equals(host, ignoreCase = true) }.toSet() }
        persist()
    }

    fun remove(host: String, username: String) {
        _entries.update { list ->
            list.filterNot { it.host.equals(host, ignoreCase = true) && it.username == username }
        }
        persist()
    }

    fun neverSave(host: String) {
        if (host.isBlank()) return
        _entries.update { list -> list.filterNot { it.host.equals(host, ignoreCase = true) } }
        _neverSaved.update { it + host }
        persist()
    }

    fun allowSaving(host: String) {
        _neverSaved.update { it.filterNot { h -> h.equals(host, ignoreCase = true) }.toSet() }
        persist()
    }

    fun clearAll() {
        _entries.value = emptyList()
        _neverSaved.value = emptySet()
        persist()
    }

    // -------------------------------------------------- addresses and cards

    /** Adds or replaces one address, by [SavedAddress.id]. */
    fun saveAddress(address: SavedAddress) {
        val stamped = address.copy(updatedAt = System.currentTimeMillis())
        _addresses.update { list ->
            if (list.any { it.id == address.id }) list.map { if (it.id == address.id) stamped else it }
            else list + stamped
        }
        persist()
    }

    fun removeAddress(id: String) {
        _addresses.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    fun saveCard(card: SavedCard) {
        val stamped = card.copy(updatedAt = System.currentTimeMillis())
        _cards.update { list ->
            if (list.any { it.id == card.id }) list.map { if (it.id == card.id) stamped else it }
            else list + stamped
        }
        persist()
    }

    fun removeCard(id: String) {
        _cards.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    fun clearAddresses() {
        _addresses.value = emptyList()
        persist()
    }

    fun clearCards() {
        _cards.value = emptyList()
        persist()
    }

    // ----------------------------------------------------------- the file

    private fun persist() {
        val json = serialize().toString()
        io.execute {
            runCatching {
                dir.mkdirs()
                val sealed = encrypt(json.toByteArray(Charsets.UTF_8))
                val tmp = File(dir, "vault.tmp")
                tmp.writeBytes(sealed)
                if (!tmp.renameTo(file)) {
                    file.writeBytes(sealed)
                    tmp.delete()
                }
            }
        }
    }

    private fun load() {
        val raw = runCatching { if (file.isFile) file.readBytes() else null }.getOrNull() ?: return
        val plain = runCatching { decrypt(raw) }.getOrNull()
        if (plain == null) {
            // Unreadable: a restored backup, a reinstall, a keystore reset.
            // There is nothing behind this file any more, so it goes.
            runCatching { file.delete() }
            return
        }
        runCatching {
            val json = JSONObject(String(plain, Charsets.UTF_8))
            val array = json.optJSONArray("entries")
            _entries.value = (0 until (array?.length() ?: 0)).map { i ->
                val o = array!!.getJSONObject(i)
                SavedPassword(
                    host = o.getString("host"),
                    username = o.optString("username"),
                    password = o.optString("password"),
                    updatedAt = o.optLong("updatedAt", 0L),
                )
            }
            val never = json.optJSONArray("never")
            _neverSaved.value = (0 until (never?.length() ?: 0)).map { never!!.getString(it) }.toSet()
            val addresses = json.optJSONArray("addresses")
            _addresses.value = (0 until (addresses?.length() ?: 0)).mapNotNull { i ->
                val o = addresses!!.optJSONObject(i) ?: return@mapNotNull null
                SavedAddress(
                    id = o.optString("id").ifEmpty { return@mapNotNull null },
                    label = o.optString("label"),
                    name = o.optString("name"),
                    organization = o.optString("organization"),
                    street = o.optString("street"),
                    city = o.optString("city"),
                    region = o.optString("region"),
                    postalCode = o.optString("postalCode"),
                    country = o.optString("country"),
                    email = o.optString("email"),
                    phone = o.optString("phone"),
                    updatedAt = o.optLong("updatedAt", 0L),
                )
            }
            val cards = json.optJSONArray("cards")
            _cards.value = (0 until (cards?.length() ?: 0)).mapNotNull { i ->
                val o = cards!!.optJSONObject(i) ?: return@mapNotNull null
                SavedCard(
                    id = o.optString("id").ifEmpty { return@mapNotNull null },
                    label = o.optString("label"),
                    cardholder = o.optString("cardholder"),
                    number = o.optString("number"),
                    expiryMonth = o.optInt("expiryMonth", 0),
                    expiryYear = o.optInt("expiryYear", 0),
                    updatedAt = o.optLong("updatedAt", 0L),
                )
            }
        }
    }

    private fun serialize(): JSONObject = JSONObject().apply {
        put("entries", JSONArray().apply {
            _entries.value.forEach { entry ->
                put(JSONObject().apply {
                    put("host", entry.host)
                    put("username", entry.username)
                    put("password", entry.password)
                    put("updatedAt", entry.updatedAt)
                })
            }
        })
        put("never", JSONArray().apply { _neverSaved.value.forEach { put(it) } })
        put("addresses", JSONArray().apply {
            _addresses.value.forEach { entry ->
                put(JSONObject().apply {
                    put("id", entry.id)
                    put("label", entry.label)
                    put("name", entry.name)
                    put("organization", entry.organization)
                    put("street", entry.street)
                    put("city", entry.city)
                    put("region", entry.region)
                    put("postalCode", entry.postalCode)
                    put("country", entry.country)
                    put("email", entry.email)
                    put("phone", entry.phone)
                    put("updatedAt", entry.updatedAt)
                })
            }
        })
        put("cards", JSONArray().apply {
            _cards.value.forEach { entry ->
                put(JSONObject().apply {
                    put("id", entry.id)
                    put("label", entry.label)
                    put("cardholder", entry.cardholder)
                    put("number", entry.number)
                    put("expiryMonth", entry.expiryMonth)
                    put("expiryYear", entry.expiryYear)
                    put("updatedAt", entry.updatedAt)
                })
            }
        })
    }

    // ------------------------------------------------------------- crypto

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val body = cipher.doFinal(plain)
        return ByteArray(1 + iv.size + body.size).also { out ->
            out[0] = FORMAT_VERSION
            iv.copyInto(out, 1)
            body.copyInto(out, 1 + iv.size)
        }
    }

    private fun decrypt(sealed: ByteArray): ByteArray {
        require(sealed.isNotEmpty() && sealed[0] == FORMAT_VERSION) { "unknown vault format" }
        require(sealed.size > 1 + IV_BYTES) { "truncated vault" }
        val iv = sealed.copyOfRange(1, 1 + IV_BYTES)
        val body = sealed.copyOfRange(1 + IV_BYTES, sealed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(body)
    }

    /** The vault key, created on first use and never leaving the keystore. */
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // Deliberately no setUserAuthenticationRequired: filling a
                // form happens while the page is in front of the user, and a
                // lock-screen challenge per keystroke is not a thing anyone
                // would keep switched on. Revealing a stored password in
                // Settings is the place a challenge would belong.
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "browser_password_vault"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val FORMAT_VERSION: Byte = 1
    }
}
