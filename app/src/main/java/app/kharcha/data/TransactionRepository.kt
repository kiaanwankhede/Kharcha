package app.kharcha.data

import app.kharcha.core.BackupPayload
import app.kharcha.core.Category
import app.kharcha.core.CategoryCatalog
import app.kharcha.core.Classifier
import app.kharcha.core.Deduplicator
import app.kharcha.core.Direction
import app.kharcha.core.Merchants
import app.kharcha.core.ReviewStatus
import app.kharcha.core.Source
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

    /** Only reviewed payments count anywhere in the app. */
    val reviewed: Flow<List<Transaction>> = transactions.map { list -> list.filter { it.status == ReviewStatus.REVIEWED } }

    val pending: Flow<List<Transaction>> = transactions.map { list -> list.filter { it.status == ReviewStatus.PENDING } }

    val pendingCount: Flow<Int> = dao.observePendingCount()

    fun transaction(id: Long): Flow<Transaction?> = combine(dao.observe(id), catalog) { entity, catalog ->
        entity?.toCore(catalog)
    }

    /** Saves a parsed message, merging it into an existing record when it is the same payment. Returns the new record, or null if it was merged. */
    suspend fun ingest(parsed: ParsedTransaction): Transaction? = ingestLock.withLock {
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
            null
        } else {
            incoming.copy(id = dao.insert(incoming.toEntity()))
        }
    }

    /** Confirms a payment: it now counts everywhere. */
    suspend fun review(id: Long, category: Category, rememberForMerchant: Boolean, note: String? = null) {
        setCategory(id, category, rememberForMerchant)
        if (note != null) setNote(id, note)
        dao.setStatus(id, ReviewStatus.REVIEWED.name)
    }

    /** Accepts a payment with the category it already has. */
    suspend fun accept(id: Long) = dao.setStatus(id, ReviewStatus.REVIEWED.name)

    /** Not a real spend (a duplicate, a refund pair, not mine): never counted. */
    suspend fun ignore(id: Long) = dao.setStatus(id, ReviewStatus.IGNORED.name)

    suspend fun sendBackToReview(id: Long) = dao.setStatus(id, ReviewStatus.PENDING.name)

    /** Marks everything older than [before] as reviewed, so the inbox starts from a sensible point. */
    suspend fun reviewAllBefore(before: Long) = dao.reviewAllBefore(before)

    suspend fun pendingCountSince(since: Long): Int = dao.pendingCountSince(since)

    suspend fun setCategory(id: Long, category: Category, rememberForMerchant: Boolean) {
        val existing = dao.get(id) ?: return
        if (rememberForMerchant) {
            rules.upsert(MerchantRuleEntity(existing.merchantKey, category.id))
            dao.setCategoryForMerchant(existing.merchantKey, category.id)
        } else {
            dao.update(existing.copy(category = category.id))
        }
    }

    /** Records a cash payment typed in by the user. */
    suspend fun addCash(amountPaise: Long, whatFor: String, category: Category, timestampMillis: Long): Long {
        val name = Merchants.tidy(whatFor).ifEmpty { "Cash" }
        return dao.insert(
            Transaction(
                amountPaise = amountPaise,
                direction = Direction.DEBIT,
                timestampMillis = timestampMillis,
                merchant = name,
                merchantKey = Merchants.keyOf(name),
                category = category,
                source = Source.MANUAL,
                rawText = "Cash, added by hand",
                status = ReviewStatus.REVIEWED,
            ).toEntity(),
        )
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun setNote(id: Long, note: String) {
        val existing = dao.get(id) ?: return
        dao.update(existing.copy(note = note.trim().ifEmpty { null }))
    }

    /** Creates a category or group, or updates it if it already exists. Returns the saved category. */
    suspend fun saveCategory(category: Category): Category {
        require(!category.builtIn) { "Built-in categories can't be edited" }
        customCategories.upsert(category.toEntity())
        if (category.isGroup) {
            // A group's subcategories follow its kind.
            customCategories.getAll().filter { it.parentId == category.id }.forEach {
                customCategories.upsert(it.copy(kind = category.kind.name, countsAsSpend = category.countsAsSpend))
            }
        }
        return category
    }

    /**
     * Deletes a custom category; its transactions and merchant rules move to Uncategorised. Deleting a
     * custom group moves its subcategories to the Other group instead.
     */
    suspend fun deleteCategory(category: Category) {
        if (category.builtIn) return
        if (category.isGroup) {
            customCategories.getAll().filter { it.parentId == category.id }.forEach {
                customCategories.upsert(it.copy(parentId = Category.G_OTHER.id, kind = Category.G_OTHER.kind.name))
            }
            customCategories.delete(category.id)
            return
        }
        dao.moveCategory(category.id, Category.OTHER.id)
        rules.deleteForCategory(category.id)
        customCategories.delete(category.id)
    }

    /**
     * One-time move from the first version's flat categories to the subcategory tree: payments are
     * re-sorted by merchant where possible, and merchant rules follow.
     */
    suspend fun migrateToCategoryTree() {
        if (prefs.categoryTreeMigrated) return
        val catalog = currentCatalog()
        dao.getAll().forEach { entity ->
            if (entity.category in Category.LEGACY_IDS) {
                val moved = Classifier.recategorise(entity.toCore(catalog), entity.category)
                dao.update(moved.toEntity())
            }
        }
        rules.getAll().forEach { rule ->
            val legacy = Category.LEGACY_IDS[rule.category] ?: return@forEach
            val target = Merchants.brandFor(rule.merchantKey)?.category?.takeIf { legacy.countsAsSpend && it.countsAsSpend } ?: legacy
            rules.upsert(rule.copy(category = target.id))
        }
        prefs.categoryTreeMigrated = true
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

    suspend fun catalogNow(): CategoryCatalog = currentCatalog()

    private suspend fun currentCatalog() = CategoryCatalog(customCategories.getAll().map { it.toCore() })

    private suspend fun currentRules(catalog: CategoryCatalog): Map<String, Category> =
        rules.getAll().associate { rule -> rule.merchantKey to catalog.byId(rule.category) }
}
