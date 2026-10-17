package app.kharcha.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class BackupPayload(
    val version: Int = 2,
    val createdAtMillis: Long,
    val transactions: List<Transaction>,
    /** Merchant key -> category the user chose. */
    val merchantRules: Map<String, Category> = emptyMap(),
    val monthlyLimitPaise: Long? = null,
    val customCategories: List<Category> = emptyList(),
)

class WrongPassphraseException : Exception("The passphrase does not match this backup")

class NotABackupException : Exception("This file is not a Kharcha backup")

/**
 * Backup file format: "KHB1" | salt (16) | iv (12) | AES-256-GCM(gzip(JSON)).
 * The key comes from the user's passphrase via PBKDF2, so the file is unreadable without it,
 * including by Google Drive or wherever the file is stored.
 */
object BackupCodec {
    private val MAGIC = "KHB1".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val ITERATIONS = 210_000

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val random = SecureRandom()

    fun encode(payload: BackupPayload, passphrase: CharArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        val sealed = cipher.doFinal(gzip(json.encodeToString(payload).toByteArray(Charsets.UTF_8)))
        return ByteBuffer.allocate(MAGIC.size + SALT_BYTES + IV_BYTES + sealed.size)
            .put(MAGIC).put(salt).put(iv).put(sealed).array()
    }

    fun decode(bytes: ByteArray, passphrase: CharArray): BackupPayload {
        if (bytes.size < MAGIC.size + SALT_BYTES + IV_BYTES + TAG_BITS / 8) throw NotABackupException()
        if (!bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw NotABackupException()
        val salt = bytes.copyOfRange(MAGIC.size, MAGIC.size + SALT_BYTES)
        val iv = bytes.copyOfRange(MAGIC.size + SALT_BYTES, MAGIC.size + SALT_BYTES + IV_BYTES)
        val sealed = bytes.copyOfRange(MAGIC.size + SALT_BYTES + IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        val plain = try {
            cipher.doFinal(sealed)
        } catch (e: AEADBadTagException) {
            throw WrongPassphraseException()
        }
        return json.decodeFromString(BackupPayload.serializer(), gunzip(plain).toString(Charsets.UTF_8))
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(key, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun gzip(data: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(data) } }.toByteArray()

    private fun gunzip(data: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
}

object CsvExporter {
    private val HEADER = listOf("Date", "Time", "Amount", "Type", "Merchant", "Group", "Category", "Bank", "Account", "UPI ref", "Note")

    fun export(transactions: List<Transaction>, zone: java.time.ZoneId = java.time.ZoneId.of("Asia/Kolkata")): String {
        val rows = transactions.sortedBy { it.timestampMillis }.map { t ->
            val time = java.time.Instant.ofEpochMilli(t.timestampMillis).atZone(zone)
            listOf(
                time.toLocalDate().toString(),
                time.toLocalTime().withNano(0).toString(),
                java.math.BigDecimal.valueOf(t.amountPaise).movePointLeft(2).toPlainString(),
                if (t.direction == Direction.DEBIT) "Debit" else "Credit",
                t.merchant,
                Category.groupOf(t.category).label,
                t.category.label,
                t.bank.orEmpty(),
                t.accountLast4.orEmpty(),
                t.reference.orEmpty(),
                t.note.orEmpty(),
            )
        }
        return (listOf(HEADER) + rows).joinToString("\r\n", postfix = "\r\n") { row -> row.joinToString(",") { escape(it) } }
    }

    private fun escape(value: String): String {
        // A leading =, +, - or @ would be run as a formula by Excel or Sheets.
        val safe = if (value.firstOrNull() in setOf('=', '+', '-', '@')) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
