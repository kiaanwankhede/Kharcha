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
import app.kharcha.core.Event
import app.kharcha.core.Events
import app.kharcha.core.Goal
import app.kharcha.core.People
import app.kharcha.core.Reimburse
import app.kharcha.core.Tags
import app.kharcha.core.CardBills
import app.kharcha.core.Merchants
import app.kharcha.core.MessageRule
import app.kharcha.core.MessageRules
import app.kharcha.core.SmsParser
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
/** Merchant rules that tag rather than set a built-in type are stored as "tags:food,kids". */
private const val TAG_RULE = "tags:"

class TransactionRepository(
    private val db: KharchaDatabase,
    private val prefs: Prefs,
    private val contactName: (String) -> String? = { null },
) {
    private val dao = db.transactions()
    private val rules = db.rules()
    private val customCategories = db.customCategories()
    private val unreadMessages = db.unreadMessages()
    private val messageRules = db.messageRules()
    private val payeeNames = db.payeeNames()
    private val budgetDao = db.budgets()
    private val cardBillDao = db.cardBills()
    private val eventDao = db.events()
    private val goalDao = db.goals()

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

    /** Every message (newest first, one-time codes left out) with what happened to it. */
    val messages: Flow<List<UnreadMessageEntity>> = unreadMessages.observeAll()

    /** Messages waiting in Review for "payment" or "not a payment". */
    val toDecide: Flow<List<UnreadMessageEntity>> = unreadMessages.observeToDecide()

    /** Sets many messages aside as not money at once, without learning a pattern from each. */
    suspend fun setAsideMessages(ids: Collection<Long>) = ids.forEach { unreadMessages.setState(it, MessageState.NOT_MONEY, null, dismissed = true) }

    /** Puts messages set aside by [setAsideMessages] back as they were (id to old state). */
    suspend fun restoreMessages(states: List<Pair<Long, String>>) = states.forEach { (id, state) -> unreadMessages.setState(id, state, null, dismissed = false) }

    val learnedRules: Flow<List<MessageRule>> = messageRules.observeAll().map { list -> list.map { it.toCore() } }

    /** What capturing one SMS produced: a new payment to announce, and the parsed message (for balance checks). */
    data class Captured(val newTransaction: Transaction?, val parsed: ParsedTransaction?)

    /**
     * The single path for every incoming or scanned SMS. Every message is kept (except one-time codes)
     * with what happened to it: a payment, a card bill, unreadable, not a payment, or hidden by a
     * pattern the user taught. Only bank senders are read for payments.
     */
    suspend fun capture(sender: String, body: String, receivedAt: Long): Captured {
        val earlyReading = SmsParser.inspect(sender, body, receivedAt)
        if (earlyReading !is SmsParser.Reading.Read && SmsParser.isOtp(body)) return Captured(null, null)
        fun record(state: String, amount: Long? = null, transactionId: Long? = null) = UnreadMessageEntity(
            sender = sender, body = body, receivedAt = receivedAt, amountPaise = amount, dismissed = false,
            state = state, transactionId = transactionId,
        )
        CardBills.parse(sender, body, receivedAt)?.let { bill ->
            saveCardBill(bill)
            unreadMessages.insert(record(MessageState.BILL, bill.totalDuePaise))
            return Captured(null, null)
        }
        val reading = earlyReading
        val rules = messageRules.getAll().map { it.toCore() }
        if (rules.any { MessageRules.matches(it, sender, body, paymentLike = reading is SmsParser.Reading.Read) }) {
            unreadMessages.insert(record(MessageState.HIDDEN))
            return Captured(null, null)
        }
        return when (reading) {
            is SmsParser.Reading.Read -> {
                val created = ingest(reading.parsed)
                val linkedId = created?.id ?: reading.parsed.reference?.let { ref -> dao.findByReference(ref).firstOrNull()?.id }
                unreadMessages.insert(record(MessageState.PAYMENT, reading.parsed.amountPaise, linkedId))
                Captured(created, reading.parsed)
            }
            is SmsParser.Reading.Unreadable -> {
                unreadMessages.insert(record(MessageState.UNREAD, reading.amountPaise))
                Captured(null, null)
            }
            SmsParser.Reading.Skipped -> {
                unreadMessages.insert(record(MessageState.SKIPPED))
                Captured(null, null)
            }
        }
    }

    /** What "not money" changed, so it can be undone. */
    data class NotMoneyResult(
        val ruleId: Long,
        val messages: List<Pair<Long, String>>,
        val transactions: List<Pair<Long, String>>,
    ) {
        val similarCount: Int get() = (messages.size - 1).coerceAtLeast(0)
    }

    /** "Not a payment" only ever affects the one message now, so nothing else would be hidden. */
    suspend fun previewNotMoney(id: Long): Int = 0

    /**
     * The user says a message isn't a payment: that message alone is set aside (and its payment, if
     * one was read from it and not yet reviewed). Nothing is learned and no other message changes.
     */
    suspend fun markNotMoney(id: Long): NotMoneyResult? {
        val message = unreadMessages.get(id) ?: return null
        val changedTransactions = mutableListOf<Pair<Long, String>>()
        unreadMessages.setState(message.id, MessageState.NOT_MONEY, message.transactionId, dismissed = true)
        message.transactionId?.let { txId ->
            dao.get(txId)?.takeIf { it.status == ReviewStatus.PENDING.name }?.let { tx ->
                changedTransactions += tx.id to tx.status
                dao.setStatus(tx.id, ReviewStatus.IGNORED.name)
            }
        }
        return NotMoneyResult(ruleId = 0, messages = listOf(message.id to message.state), transactions = changedTransactions)
    }

    /**
     * One-time: earlier builds learned patterns from "not money" and hid similar messages. Forget them
     * all, so every hidden message (and its payment) comes back for review.
     */
    suspend fun forgetLearnedPatterns() {
        if (prefs.patternsForgotten) return
        messageRules.getAll().forEach { messageRules.delete(it.id) }
        // Only what a pattern hid comes back; messages you marked yourself stay set aside.
        unreadMessages.getAll().filter { it.state == MessageState.HIDDEN }.forEach { rereadMessage(it) }
        prefs.patternsForgotten = true
    }

    suspend fun undoNotMoney(result: NotMoneyResult) {
        if (result.ruleId > 0) messageRules.delete(result.ruleId)
        result.messages.forEach { (id, state) ->
            val m = unreadMessages.get(id) ?: return@forEach
            unreadMessages.setState(id, state, m.transactionId, dismissed = false)
        }
        result.transactions.forEach { (id, status) -> dao.setStatus(id, status) }
    }

    /** "Not a payment" on a captured payment: learns from its SMS when there is one. */
    suspend fun markTransactionNotMoney(transactionId: Long): NotMoneyResult? {
        unreadMessages.findByTransaction(transactionId)?.let { return markNotMoney(it.id) }
        val previous = dao.get(transactionId)?.status ?: return null
        dao.setStatus(transactionId, ReviewStatus.IGNORED.name)
        return NotMoneyResult(ruleId = 0, messages = emptyList(), transactions = listOf(transactionId to previous))
    }

    /** Forgets a learned pattern; messages it hid are read again (their payments go back to review). */
    suspend fun deleteRule(ruleId: Long) {
        val rule = messageRules.getAll().firstOrNull { it.id == ruleId }?.toCore() ?: return
        messageRules.delete(ruleId)
        val remaining = messageRules.getAll().map { it.toCore() }
        unreadMessages.getAll()
            .filter { it.state == MessageState.HIDDEN || it.state == MessageState.NOT_MONEY }
            .filter { m -> MessageRules.words(m.body).let { MessageRules.similarity(it, rule.words) >= MessageRules.SIMILARITY } }
            .filter { m -> remaining.none { MessageRules.matches(it, m.sender, m.body, paymentLike = true) } }
            .forEach { m -> rereadMessage(m) }
    }

    /** Puts a set-aside message back: its payment returns to review, or the message is read again. */
    suspend fun rereadMessage(m: UnreadMessageEntity) {
        val txId = m.transactionId
        if (txId != null && dao.get(txId) != null) {
            dao.setStatus(txId, ReviewStatus.PENDING.name)
            unreadMessages.setState(m.id, MessageState.PAYMENT, txId, dismissed = false)
            return
        }
        when (val reading = SmsParser.inspect(m.sender, m.body, m.receivedAt)) {
            is SmsParser.Reading.Read -> {
                val created = ingest(reading.parsed)
                unreadMessages.setState(m.id, MessageState.PAYMENT, created?.id, dismissed = false)
            }
            is SmsParser.Reading.Unreadable -> unreadMessages.setState(m.id, MessageState.UNREAD, null, dismissed = false)
            SmsParser.Reading.Skipped -> unreadMessages.setState(m.id, MessageState.SKIPPED, null, dismissed = false)
        }
    }

    private suspend fun candidatesFor(rule: MessageRule, exclude: Long): List<UnreadMessageEntity> =
        unreadMessages.getAll().filter { other ->
            other.id != exclude &&
                other.state in setOf(MessageState.UNREAD, MessageState.SKIPPED, MessageState.PAYMENT) &&
                MessageRules.matches(rule, other.sender, other.body, paymentLike = other.state == MessageState.PAYMENT) &&
                // Never hide a payment the user already reviewed.
                (other.transactionId == null || dao.get(other.transactionId)?.status != ReviewStatus.REVIEWED.name)
        }

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
        val refundChecked = if (classified.category.id == Category.REFUNDS.id) {
            val history = dao.findSince(classified.timestampMillis - Refunds.LOOKBACK_MILLIS).map { it.toCore(catalog) }
            Refunds.categorise(classified, history)
        } else {
            classified
        }
        val incoming = withPeopleAndTrips(refundChecked, catalog)
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

    /**
     * Money from someone who owes you becomes "Paid back to me" (and money to someone you owe, "Paying
     * back"), and payments during a trip get the trip's tag.
     */
    private suspend fun withPeopleAndTrips(t: Transaction, catalog: CategoryCatalog): Transaction {
        val ledger = People.ledger(dao.findByMerchantKey(t.merchantKey).map { it.toCore(catalog) })
        val settled = People.suggest(t, ledger)?.let { t.copy(category = it, confidence = Confidence.LIKELY) } ?: t
        val date = java.time.Instant.ofEpochMilli(t.timestampMillis).atZone(java.time.ZoneId.of("Asia/Kolkata")).toLocalDate()
        val tripTags = if (t.direction == Direction.DEBIT) Events.autoTags(eventDao.getAll().map { it.toCore() }, date) else emptyList()
        // "Always tag Swiggy with food": the merchant's tags come first, so the main tag is right.
        val ruleTags = rules.getAll().firstOrNull { it.merchantKey == t.merchantKey && it.category.startsWith(TAG_RULE) }
            ?.let { Tags.decode(it.category.removePrefix(TAG_RULE)) }
            ?.takeIf { settled.category.countsAsSpend }
            .orEmpty()
        val tagged = if (ruleTags.isEmpty()) settled else settled.copy(category = Category.OTHER, confidence = Confidence.SURE)
        val allTags = (ruleTags + settled.tags + tripTags).distinct()
        return if (allTags == settled.tags) tagged else tagged.copy(tags = allTags)
    }

    val events: Flow<List<Event>> = eventDao.observeAll().map { list -> list.map { it.toCore() } }

    val goals: Flow<List<Goal>> = goalDao.observeAll().map { list -> list.map { it.toCore() } }

    /** Saves a trip and, when [tagExisting], tags the payments already made in its dates. */
    suspend fun saveEvent(event: Event, tagExisting: Boolean) {
        val previous = event.id.takeIf { it > 0 }?.let { id -> eventDao.getAll().firstOrNull { it.id == id }?.toCore() }
        eventDao.upsert(event.toEntity())
        ensureTag(event.tag)
        // Renamed: move the old tag over to the new name.
        if (previous != null && previous.tag != event.tag) {
            allTransactions().filter { previous.tag in it.tags }.forEach { t ->
                dao.update(t.copy(tags = t.tags.map { if (it == previous.tag) event.tag else it }).toEntity())
            }
        }
        if (tagExisting) {
            val today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"))
            Events.untaggedInRange(event, allTransactions(), today).forEach { t -> dao.update(t.copy(tags = t.tags + event.tag).toEntity()) }
        }
    }

    suspend fun deleteEvent(event: Event) {
        eventDao.delete(event.id)
        allTransactions().filter { event.tag in it.tags }.forEach { t -> dao.update(t.copy(tags = t.tags - event.tag).toEntity()) }
    }

    suspend fun setTags(id: Long, tags: List<String>) {
        val existing = dao.get(id) ?: return
        tags.forEach { ensureTag(it) }
        dao.update(existing.copy(tags = Tags.encode(tags)))
    }

    /** Marks a payment as one the office will pay back (or not), taking it out of (or back into) spending. */
    suspend fun setReimburse(id: Long, reimburse: Reimburse?) {
        val existing = dao.get(id) ?: return
        dao.update(existing.copy(reimburse = reimburse?.name))
    }

    suspend fun markReimbursed(ids: Collection<Long>) = ids.forEach { setReimburse(it, Reimburse.DONE) }

    /** Records settling up in cash or outside the bank: they paid you back, or you paid them back. */
    suspend fun addSettlement(personKey: String, name: String, amountPaise: Long, theyPaidMe: Boolean) {
        dao.insert(
            Transaction(
                amountPaise = amountPaise,
                direction = if (theyPaidMe) Direction.CREDIT else Direction.DEBIT,
                timestampMillis = System.currentTimeMillis(),
                merchant = name,
                merchantKey = personKey,
                category = if (theyPaidMe) Category.PAID_BACK else Category.REPAYING,
                source = Source.MANUAL,
                rawText = "Settled up, added by hand",
                status = ReviewStatus.REVIEWED,
            ).toEntity(),
        )
    }

    suspend fun saveGoal(goal: Goal) = goalDao.upsert(goal.toEntity())

    suspend fun deleteGoal(id: Long) = goalDao.delete(id)

    /** Keeps a bank message the parser could not read, so the user can add it by hand. */
    suspend fun recordUnread(sender: String, body: String, receivedAt: Long, amountPaise: Long?) {
        unreadMessages.insert(
            UnreadMessageEntity(sender = sender, body = body, receivedAt = receivedAt, amountPaise = amountPaise, dismissed = false, state = MessageState.UNREAD),
        )
    }

    suspend fun dismissUnread(id: Long) = markNotMoney(id)

    /** Adds a payment the user read out of a message the parser could not understand. */
    suspend fun addFromMessage(
        message: UnreadMessageEntity,
        amountPaise: Long,
        direction: Direction,
        payee: String,
        category: Category,
    ) {
        val name = Merchants.tidy(payee).ifEmpty { "Unknown" }
        val id = dao.insert(
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
        unreadMessages.setState(message.id, MessageState.ADDED, id, dismissed = true)
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

    /** Confirms a payment with its tags (first = main) and, for money that isn't spending, its [type]. */
    suspend fun reviewTagged(id: Long, type: Category?, tags: List<String>, rememberForMerchant: Boolean, note: String? = null) {
        setLabels(id, type, tags, rememberForMerchant)
        if (note != null) setNote(id, note)
        dao.setStatus(id, ReviewStatus.REVIEWED.name)
        checkBudgets()
    }

    /**
     * Sets what a payment is: its [tags] in order (the first counts in totals) and, when it isn't
     * spending, its built-in [type] (null means spending). With [rememberForMerchant] every payment to
     * the same merchant gets the same, now and later; those keep their trip tags.
     */
    suspend fun setLabels(id: Long, type: Category?, tags: List<String>, rememberForMerchant: Boolean) {
        val existing = dao.get(id) ?: return
        val clean = tags.map(Tags::clean).filter { it.isNotEmpty() }.distinct()
        val typeId = type?.takeIf { !it.countsAsSpend }?.id ?: Category.OTHER.id
        clean.forEach { name -> ensureTag(name) }
        dao.update(existing.copy(category = typeId, tags = Tags.encode(clean), confidence = Confidence.SURE.name))
        if (rememberForMerchant) {
            rules.upsert(MerchantRuleEntity(existing.merchantKey, if (typeId != Category.OTHER.id) typeId else TAG_RULE + Tags.encode(clean).orEmpty()))
            val tripTags = eventDao.getAll().map { it.toCore().tag }.toSet()
            dao.findByMerchantKey(existing.merchantKey).filter { it.id != id }.forEach { other ->
                val trips = Tags.decode(other.tags).filter { it in tripTags && it !in clean }
                dao.update(other.copy(category = typeId, tags = Tags.encode(clean + trips), confidence = Confidence.SURE.name))
            }
        }
    }

    /** Saves a tag (with a guessed emoji) the first time it's used. */
    private suspend fun ensureTag(name: String) {
        val tag = Category.forTag(name)
        if (customCategories.getAll().none { it.id == tag.id }) customCategories.upsert(tag.toEntity())
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

    /** Picking one category: a tag becomes the main tag (others kept), a built-in type keeps the tags. */
    suspend fun setCategory(id: Long, category: Category, rememberForMerchant: Boolean) {
        val existing = dao.get(id) ?: return
        val current = Tags.decode(existing.tags)
        val name = category.tagName
        when {
            name != null -> setLabels(id, null, listOf(name) + (current - name), rememberForMerchant)
            category.countsAsSpend -> setLabels(id, null, current, rememberForMerchant)
            else -> setLabels(id, category, current, rememberForMerchant)
        }
    }

    /** Records a cash payment typed in by the user. */
    suspend fun addCash(amountPaise: Long, whatFor: String, category: Category, timestampMillis: Long, tags: List<String> = emptyList()): Long {
        tags.forEach { ensureTag(it) }
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
                tags = tags,
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
    /** Saves a tag, or another of the user's categories (emoji changes). */
    suspend fun saveCategory(category: Category): Category {
        require(!category.builtIn) { "Built-in categories can't be edited" }
        val flat = category.copy(parentId = null)
        customCategories.upsert(flat.toEntity())
        return flat
    }

    /** Renames a tag everywhere (a new name is a new tag id), keeping its emoji unless [emoji] is given. */
    suspend fun renameTag(tag: Category, newName: String, emoji: String) {
        val renamed = Category.forTag(newName, emoji)
        if (renamed.id == tag.id) {
            saveCategory(tag.copy(emoji = emoji))
            return
        }
        saveCategory(renamed)
        mergeCategory(tag, renamed)
    }

    /** Deletes a tag: it comes off every payment and every merchant rule. */
    suspend fun deleteCategory(category: Category) {
        if (category.builtIn) return
        val name = category.tagName
        if (name != null) {
            replaceTag(name, null)
        } else {
            dao.moveCategory(category.id, Category.OTHER.id)
            rules.deleteForCategory(category.id)
        }
        customCategories.delete(category.id)
    }

    /** Moves every payment and rule from tag [from] to tag [into], then deletes [from]. */
    suspend fun mergeCategory(from: Category, into: Category) {
        if (from.builtIn || from.id == into.id) return
        val fromName = from.tagName ?: return
        replaceTag(fromName, into.tagName)
        customCategories.delete(from.id)
    }

    /** Swaps tag [from] for [to] (or removes it when [to] is null) on payments and merchant rules. */
    private suspend fun replaceTag(from: String, to: String?) {
        fun swap(list: List<String>) = list.map { if (it == from) to else it }.filterNotNull().distinct()
        dao.getAll().filter { from in Tags.decode(it.tags) }.forEach { e ->
            dao.update(e.copy(tags = Tags.encode(swap(Tags.decode(e.tags)))))
        }
        rules.getAll().filter { it.category.startsWith(TAG_RULE) }.forEach { rule ->
            val tags = Tags.decode(rule.category.removePrefix(TAG_RULE))
            if (from in tags) {
                val left = swap(tags)
                if (left.isEmpty()) rules.deleteForCategory(rule.category) else rules.upsert(rule.copy(category = TAG_RULE + Tags.encode(left)))
            }
        }
    }

    /**
     * One-time move from categories to tags: each of the user's own categories becomes a tag of the
     * same name, put first on its payments, and merchant rules follow.
     */
    suspend fun migrateToTags() {
        if (prefs.tagsMigrated) return
        customCategories.getAll().filterNot { it.id.startsWith(Category.TAG_PREFIX) }.forEach { old ->
            val tag = Category.forTag(old.label, old.emoji)
            val name = tag.tagName ?: return@forEach
            customCategories.upsert(tag.toEntity())
            dao.getAll().filter { it.category == old.id }.forEach { e ->
                dao.update(e.copy(category = Category.OTHER.id, tags = Tags.encode(listOf(name) + Tags.decode(e.tags))))
            }
            rules.getAll().filter { it.category == old.id }.forEach { rules.upsert(it.copy(category = TAG_RULE + name)) }
            customCategories.delete(old.id)
        }
        prefs.tagsMigrated = true
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
            customCategories = catalog.mine,
            budgets = budgetDao.getAll().map { app.kharcha.core.BackupBudget.of(it.toCore()) },
            payeeNames = currentPayeeNames(),
            events = eventDao.getAll().map { app.kharcha.core.BackupEvent.of(it.toCore()) },
            goals = goalDao.getAll().map { app.kharcha.core.BackupGoal.of(it.toCore()) },
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
        // Older backups have none of these; keep what's on the phone rather than wiping it.
        if (payload.version >= 3) {
            budgetDao.deleteAll()
            payload.budgets.forEach { budgetDao.upsert(it.toBudget().toEntity()) }
            payeeNames.deleteAll()
            payload.payeeNames.forEach { (key, name) -> payeeNames.upsert(PayeeNameEntity(key, name)) }
            eventDao.deleteAll()
            payload.events.forEach { eventDao.upsert(it.toEvent().toEntity()) }
            goalDao.deleteAll()
            payload.goals.forEach { goalDao.upsert(it.toGoal().toEntity()) }
        }
    }

    suspend fun catalogNow(): CategoryCatalog = currentCatalog()

    private suspend fun currentPayeeNames(): Map<String, String> = payeeNames.getAll().associate { it.merchantKey to it.name }

    private suspend fun currentCatalog() = CategoryCatalog(customCategories.getAll().map { it.toCore() })

    // Rules pointing at the old built-in spending categories no longer apply.
    private suspend fun currentRules(catalog: CategoryCatalog): Map<String, Category> =
        rules.getAll().filter { catalog.isKnown(it.category) }.associate { rule -> rule.merchantKey to catalog.byId(rule.category) }
}
