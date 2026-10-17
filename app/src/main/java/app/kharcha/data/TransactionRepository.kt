package app.kharcha.data

import app.kharcha.core.BackupPayload
import app.kharcha.core.Category
import app.kharcha.core.Classifier
import app.kharcha.core.Deduplicator
import app.kharcha.core.ParsedTransaction
import app.kharcha.core.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TransactionRepository(private val db: KharchaDatabase, private val prefs: Prefs) {
    private val dao = db.transactions()
    private val rules = db.rules()

    // SMS and notifications for the same payment can arrive at the same moment; ingest one at a time
    // so the duplicate check always sees the other record.
    private val ingestLock = Mutex()

    val transactions: Flow<List<Transaction>> = dao.observeAll().map { list -> list.map { it.toCore() } }

    fun transaction(id: Long): Flow<Transaction?> = dao.observe(id).map { it?.toCore() }

    /** Saves a parsed message, merging it into an existing record when it is the same payment. Returns true if new. */
    suspend fun ingest(parsed: ParsedTransaction): Boolean = ingestLock.withLock {
        val classifier = Classifier(currentRules())
        val incoming = classifier.classify(parsed)
        val candidates = buildList {
            incoming.reference?.let { addAll(dao.findByReference(it)) }
            addAll(
                dao.findNear(
                    incoming.amountPaise,
                    incoming.direction.name,
                    incoming.timestampMillis - Deduplicator.MATCH_WINDOW_MILLIS,
                    incoming.timestampMillis + Deduplicator.MATCH_WINDOW_MILLIS,
                ),
            )
        }.map { it.toCore() }

        val match = candidates.firstOrNull { Deduplicator.isSamePayment(it, incoming) }
        if (match != null) {
            val merged = Deduplicator.merge(match, incoming)
            if (merged != match) dao.update(merged.toEntity())
            false
        } else {
            dao.insert(incoming.toEntity())
            true
        }
    }

    suspend fun setCategory(id: Long, category: Category, rememberForMerchant: Boolean) {
        val existing = dao.get(id) ?: return
        if (rememberForMerchant) {
            rules.upsert(MerchantRuleEntity(existing.merchantKey, category.name))
            dao.setCategoryForMerchant(existing.merchantKey, category.name)
        } else {
            dao.update(existing.copy(category = category.name))
        }
    }

    suspend fun setNote(id: Long, note: String) {
        val existing = dao.get(id) ?: return
        dao.update(existing.copy(note = note.trim().ifEmpty { null }))
    }

    suspend fun snapshot(): BackupPayload = BackupPayload(
        createdAtMillis = System.currentTimeMillis(),
        transactions = dao.getAll().map { it.toCore() },
        merchantRules = currentRules(),
        monthlyLimitPaise = prefs.monthlyLimitPaise,
    )

    suspend fun allTransactions(): List<Transaction> = dao.getAll().map { it.toCore() }

    /** Replaces everything on the phone with the contents of a backup. */
    suspend fun restore(payload: BackupPayload) {
        dao.deleteAll()
        rules.deleteAll()
        dao.insertAll(payload.transactions.map { it.toEntity() })
        rules.insertAll(payload.merchantRules.map { (key, category) -> MerchantRuleEntity(key, category.name) })
        prefs.monthlyLimitPaise = payload.monthlyLimitPaise
    }

    private suspend fun currentRules(): Map<String, Category> = rules.getAll().mapNotNull { rule ->
        runCatching { rule.merchantKey to Category.valueOf(rule.category) }.getOrNull()
    }.toMap()
}
