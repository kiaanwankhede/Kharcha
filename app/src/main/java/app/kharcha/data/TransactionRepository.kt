package app.kharcha.data

import app.kharcha.core.BackupPayload
import app.kharcha.core.Category
import app.kharcha.core.CategoryCatalog
import app.kharcha.core.Classifier
import app.kharcha.core.Deduplicator
import app.kharcha.core.ParsedTransaction
import app.kharcha.core.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TransactionRepository(private val db: KharchaDatabase, private val prefs: Prefs) {
    private val dao = db.transactions()
    private val rules = db.rules()
    private val customCategories = db.customCategories()

    // SMS and notifications for the same payment can arrive at the same moment; ingest one at a time
    // so the duplicate check always sees the other record.
    private val ingestLock = Mutex()

    val catalog: Flow<CategoryCatalog> = customCategories.observeAll().map { list -> CategoryCatalog(list.map { it.toCore() }) }

    val transactions: Flow<List<Transaction>> = combine(dao.observeAll(), catalog) { list, catalog ->
        list.map { it.toCore(catalog) }
    }

    fun transaction(id: Long): Flow<Transaction?> = combine(dao.observe(id), catalog) { entity, catalog ->
        entity?.toCore(catalog)
    }

    /** Saves a parsed message, merging it into an existing record when it is the same payment. Returns true if new. */
    suspend fun ingest(parsed: ParsedTransaction): Boolean = ingestLock.withLock {
        val catalog = currentCatalog()
        val incoming = Classifier(currentRules(catalog)).classify(parsed)
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
        }.map { it.toCore(catalog) }

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
            rules.upsert(MerchantRuleEntity(existing.merchantKey, category.id))
            dao.setCategoryForMerchant(existing.merchantKey, category.id)
        } else {
            dao.update(existing.copy(category = category.id))
        }
    }

    suspend fun setNote(id: Long, note: String) {
        val existing = dao.get(id) ?: return
        dao.update(existing.copy(note = note.trim().ifEmpty { null }))
    }

    /** Creates a category, or updates it if it already exists. Returns the saved category. */
    suspend fun saveCategory(category: Category): Category {
        require(!category.builtIn) { "Built-in categories can't be edited" }
        customCategories.upsert(category.toEntity())
        return category
    }

    /** Deletes a custom category; its transactions and merchant rules move to Other. */
    suspend fun deleteCategory(category: Category) {
        if (category.builtIn) return
        dao.moveCategory(category.id, Category.OTHER.id)
        rules.deleteForCategory(category.id)
        customCategories.delete(category.id)
    }

    suspend fun snapshot(): BackupPayload {
        val catalog = currentCatalog()
        return BackupPayload(
            createdAtMillis = System.currentTimeMillis(),
            transactions = dao.getAll().map { it.toCore(catalog) },
            merchantRules = currentRules(catalog),
            monthlyLimitPaise = prefs.monthlyLimitPaise,
            customCategories = catalog.all.filterNot { it.builtIn },
        )
    }

    suspend fun allTransactions(): List<Transaction> {
        val catalog = currentCatalog()
        return dao.getAll().map { it.toCore(catalog) }
    }

    /** Replaces everything on the phone with the contents of a backup. */
    suspend fun restore(payload: BackupPayload) {
        dao.deleteAll()
        rules.deleteAll()
        customCategories.deleteAll()
        payload.customCategories.forEach { customCategories.upsert(it.toEntity()) }
        dao.insertAll(payload.transactions.map { it.toEntity() })
        rules.insertAll(payload.merchantRules.map { (key, category) -> MerchantRuleEntity(key, category.id) })
        prefs.monthlyLimitPaise = payload.monthlyLimitPaise
    }

    private suspend fun currentCatalog() = CategoryCatalog(customCategories.getAll().map { it.toCore() })

    private suspend fun currentRules(catalog: CategoryCatalog): Map<String, Category> =
        rules.getAll().associate { rule -> rule.merchantKey to catalog.byId(rule.category) }
}
