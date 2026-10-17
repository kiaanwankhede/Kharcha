package app.kharcha.data

import app.kharcha.core.BackupPayload
import app.kharcha.core.Budget
import app.kharcha.core.BudgetLevel
import app.kharcha.core.BudgetStatus
import app.kharcha.core.Budgets
import app.kharcha.core.CardBill
import app.kharcha.core.Category
import app.kharcha.core.CategoryCatalog
import app.kharcha.core.Classifier
import app.kharcha.core.Confidence
import app.kharcha.core.Deduplicator
import app.kharcha.core.Direction
import app.kharcha.core.Merchants
import app.kharcha.core.Refunds
import app.kharcha.core.ReviewStatus
import app.kharcha.core.Source
import app.kharcha.core.ParsedTransaction
import app.kharcha.core.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [contactName] looks up a phone number in the user's contacts (null without permission or no match),
 * so "UPI ··3210" can show as "Mom".
 */
class TransactionRepository(
    private val db: KharchaDatabase,
    private val prefs: Prefs,
    private val contactName: (String) -> String? = { null },
) {
    private val dao = db.transactions()
    private val rules = db.rules()
    private val customCategories = db.customCategories()
    private val unreadMessages = db.unreadMessages()
    private val payeeNames = db.payeeNames()
    private val budgetDao = db.budgets()
    private val cardBillDao = db.cardBills()

    /** Called with budgets that just crossed 80% or 100% after a payment was reviewed. */
    var onBudgetStatus: (List<BudgetStatus>) -> Unit = {}

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

    val unread: Flow<List<UnreadMessageEntity>> = unreadMessages.observeOpen()

    val budgets: Flow<List<Budget>> = budgetDao.observeAll().map { list -> list.map { it.toCore() } }

    val cardBills: Flow<List<CardBill>> = cardBillDao.observeAll().map { list -> list.map { it.toCore() } }

    suspend fun saveBudget(budget: Budget) = budgetDao.upsert(budget.toEntity())

    suspend fun deleteBudget(id: Long) = budgetDao.delete(id)

    /** Saves a statement or due reminder; a newer message for the same card and due date updates it. */
    suspend fun saveCardBill(bill: CardBill) {
        val existing = cardBillDao.find(bill.last4, bill.dueDate.toString())
        if (existing == null) {
            cardBillDao.insert(bill.toEntity())
        } else if (bill.receivedAtMillis >= existing.receivedAt) {
            cardBillDao.update(bill.copy(id = existing.id, paidManually = existing.paidManually).toEntity())
        }
    }

    suspend fun setBillPaid(id: Long, paid: Boolean) = cardBillDao.setPaid(id, paid)

    suspend fun cardBillsNow(): List<CardBill> = cardBillDao.getAll().map { it.toCore() }

    /** Re-checks budgets after a review; reports the ones at or past 80%. */
    private suspend fun checkBudgets() {
        val all = budgetDao.getAll().map { it.toCore() }
        if (all.isEmpty()) return
        val catalog = currentCatalog()
        val reviewedNow = dao.findSince(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).withDayOfMonth(1)
            .atStartOfDay(java.time.ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli())
            .map { it.toCore(catalog) }
            .filter { it.status == ReviewStatus.REVIEWED }
        val statuses = Budgets.status(all, reviewedNow, java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")), catalog::groupOf)
        onBudgetStatus(statuses.filter { it.level != BudgetLevel.OK })
    }

    /**
     * Saves a parsed message, merging it into an existing record when it is the same payment, and
     * flagging it when it only looks like one. Returns the new record, or null if it was merged.
     */
    suspend fun ingest(parsed: ParsedTransaction): Transaction? = ingestLock.withLock {
        val catalog = currentCatalog()
        val counterparty = parsed.counterparty
        val named = if (counterparty == null || counterparty.contains('@')) {
            Merchants.phoneFromVpa(parsed.vpa)?.let(contactName)?.let { parsed.copy(counterparty = it) } ?: parsed
        } else {
            parsed
        }
        val classified = Classifier(currentRules(catalog), currentPayeeNames()).classify(named)
        val incoming = if (classified.category.id == Category.REFUNDS.id) {
            val history = dao.findSince(classified.timestampMillis - Refunds.LOOKBACK_MILLIS).map { it.toCore(catalog) }
            Refunds.categorise(classified, history)
        } else {
            classified
        }
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
            val suspects = dao.findNear(
                incoming.amountPaise,
                incoming.direction.name,
                incoming.timestampMillis - Deduplicator.SUSPECT_WINDOW_MILLIS,
                incoming.timestampMillis + Deduplicator.SUSPECT_WINDOW_MILLIS,
            ).map { it.toCore(catalog) }
            val twin = suspects.firstOrNull { it.status != ReviewStatus.IGNORED && Deduplicator.isPossibleDuplicate(it, incoming) }
            val saved = incoming.copy(possibleDuplicateOf = twin?.id)
            saved.copy(id = dao.insert(saved.toEntity()))
        }
    }

    /** Keeps a bank message the parser could not read, so the user can add it by hand. */
    suspend fun recordUnread(sender: String, body: String, receivedAt: Long, amountPaise: Long?) {
        unreadMessages.insert(UnreadMessageEntity(sender = sender, body = body, receivedAt = receivedAt, amountPaise = amountPaise, dismissed = false))
    }

    suspend fun dismissUnread(id: Long) = unreadMessages.dismiss(id)

    /** Adds a payment the user read out of a message the parser could not understand. */
    suspend fun addFromMessage(
        message: UnreadMessageEntity,
        amountPaise: Long,
        direction: Direction,
        payee: String,
        category: Category,
    ) {
        val name = Merchants.tidy(payee).ifEmpty { "Unknown" }
        dao.insert(
            Transaction(
                amountPaise = amountPaise,
                direction = direction,
                timestampMillis = message.receivedAt,
                merchant = name,
                merchantKey = Merchants.keyOf(name),
                category = category,
                source = Source.SMS,
                rawText = message.body,
                status = ReviewStatus.REVIEWED,
            ).toEntity(),
        )
        unreadMessages.dismiss(message.id)
    }

    /** "Yes, it's a copy": the newer record is ignored. */
    suspend fun confirmDuplicate(id: Long) {
        dao.clearDuplicateFlag(id)
        dao.setStatus(id, ReviewStatus.IGNORED.name)
    }

    /** "No, it's a separate payment". */
    suspend fun notDuplicate(id: Long) = dao.clearDuplicateFlag(id)

    /**
     * Renames a payee everywhere, including future payments; renaming to an existing payee's name
     * merges the two. Merchant rules move with the name.
     */
    suspend fun renamePayee(oldKey: String, newName: String) {
        val name = newName.trim().ifEmpty { return }
        val newKey = Merchants.keyOf(name)
        if (newKey.isEmpty() || newKey == oldKey && dao.findByMerchantKey(oldKey).all { it.merchant == name }) return
        payeeNames.upsert(PayeeNameEntity(oldKey, name))
        // Names that pointed at the old one follow it, so renaming twice still works.
        payeeNames.getAll().filter { Merchants.keyOf(it.name) == oldKey }.forEach { payeeNames.upsert(it.copy(name = name)) }
        val rule = rules.getAll().firstOrNull { it.merchantKey == oldKey }
        if (rule != null && rules.getAll().none { it.merchantKey == newKey }) rules.upsert(MerchantRuleEntity(newKey, rule.category))
        dao.renamePayee(oldKey, newKey, name)
    }

    /** Re-labels past UPI transfers to phone numbers with names from the user's contacts. Returns how many changed. */
    suspend fun applyContactNames(): Int {
        var changed = 0
        dao.getAll()
            .filter { it.merchant.startsWith("UPI ··") }
            .groupBy { it.merchantKey }
            .forEach { (key, items) ->
                val phone = items.firstNotNullOfOrNull { Merchants.phoneFromVpa(it.vpa) } ?: return@forEach
                val name = contactName(phone) ?: return@forEach
                renamePayee(key, name)
                changed += items.size
            }
        return changed
    }

    /** Splits a payment across categories; an empty list removes the split. */
    suspend fun setSplits(id: Long, parts: List<app.kharcha.core.SplitPart>) {
        val existing = dao.get(id) ?: return
        dao.update(existing.copy(splitsJson = app.kharcha.core.SplitCodec.encode(parts)))
    }

    /** Fixes the amount or date of a payment. A changed amount removes any split. */
    suspend fun edit(id: Long, amountPaise: Long, timestampMillis: Long) {
        val existing = dao.get(id) ?: return
        dao.update(
            existing.copy(
                amountPaise = amountPaise,
                timestampMillis = timestampMillis,
                splitsJson = if (amountPaise == existing.amountPaise) existing.splitsJson else null,
            ),
        )
    }

    /** Confirms a payment: it now counts everywhere. */
    suspend fun review(id: Long, category: Category, rememberForMerchant: Boolean, note: String? = null) {
        setCategory(id, category, rememberForMerchant)
        if (note != null) setNote(id, note)
        dao.setStatus(id, ReviewStatus.REVIEWED.name)
        checkBudgets()
    }

    /** Accepts a payment with the category it already has. */
    suspend fun accept(id: Long) {
        dao.setStatus(id, ReviewStatus.REVIEWED.name)
        checkBudgets()
    }

    /** Not a real spend (a duplicate, a refund pair, not mine): never counted. */
    suspend fun ignore(id: Long) = dao.setStatus(id, ReviewStatus.IGNORED.name)

    suspend fun sendBackToReview(id: Long) = dao.setStatus(id, ReviewStatus.PENDING.name)

    /** Marks everything older than [before] as reviewed, so the inbox starts from a sensible point. */
    suspend fun reviewAllBefore(before: Long) = dao.reviewAllBefore(before)

    suspend fun pendingCountSince(since: Long): Int = dao.pendingCountSince(since)

    /** Today's payments (reviewed or waiting) and their spend. */
    suspend fun todaySummary(): Pair<Int, Long> {
        val zone = java.time.ZoneId.of("Asia/Kolkata")
        val start = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val catalog = currentCatalog()
        val today = dao.findSince(start).map { it.toCore(catalog) }.filter { it.status != ReviewStatus.IGNORED }
        return today.size to today.filter { it.isSpend }.sumOf { it.amountPaise }
    }

    suspend fun setCategory(id: Long, category: Category, rememberForMerchant: Boolean) {
        val existing = dao.get(id) ?: return
        if (rememberForMerchant) {
            rules.upsert(MerchantRuleEntity(existing.merchantKey, category.id))
            dao.setCategoryForMerchant(existing.merchantKey, category.id)
        } else {
            dao.update(existing.copy(category = category.id, confidence = Confidence.SURE.name))
        }
    }

    /** Records a cash payment typed in by the user. */
    suspend fun addCash(amountPaise: Long, whatFor: String, category: Category, timestampMillis: Long): Long {
        val name = Merchants.tidy(whatFor).ifEmpty { "Cash" }
        val id = dao.insert(
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
        checkBudgets()
        return id
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
            customCategories = catalog.groups.filterNot { it.builtIn } + catalog.all.filterNot { it.builtIn },
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

    private suspend fun currentPayeeNames(): Map<String, String> = payeeNames.getAll().associate { it.merchantKey to it.name }

    private suspend fun currentCatalog() = CategoryCatalog(customCategories.getAll().map { it.toCore() })

    private suspend fun currentRules(catalog: CategoryCatalog): Map<String, Category> =
        rules.getAll().associate { rule -> rule.merchantKey to catalog.byId(rule.category) }
}
