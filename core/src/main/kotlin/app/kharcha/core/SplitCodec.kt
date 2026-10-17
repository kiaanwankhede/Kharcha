package app.kharcha.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Stores split parts compactly by category id, for a database column. */
object SplitCodec {
    @Serializable
    private data class Stored(val a: Long, val c: String, val n: String? = null)

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(parts: List<SplitPart>): String? =
        if (parts.isEmpty()) null else json.encodeToString(parts.map { Stored(it.amountPaise, it.category.id, it.note) })

    fun decode(text: String?, categoryById: (String) -> Category): List<SplitPart> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<Stored>>(text) }.getOrDefault(emptyList())
            .map { SplitPart(it.a, categoryById(it.c), it.n) }
    }
}
