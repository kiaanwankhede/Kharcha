package app.kharcha.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.kharcha.core.Budget
import app.kharcha.core.BudgetKind
import app.kharcha.core.CardBill
import app.kharcha.core.Category
import app.kharcha.core.CategoryCatalog
import app.kharcha.core.Confidence
import app.kharcha.core.Direction
import app.kharcha.core.Event
import app.kharcha.core.Goal
import app.kharcha.core.Kind
import app.kharcha.core.MessageRule
import app.kharcha.core.MessageRules
import app.kharcha.core.Reimburse
import app.kharcha.core.ReviewStatus
import app.kharcha.core.Source
import app.kharcha.core.SplitCodec
import app.kharcha.core.Tags
import app.kharcha.core.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "transactions",
    indices = [Index("reference"), Index("timestampMillis"), Index("merchantKey")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val direction: String,
    val timestampMillis: Long,
    val merchant: String,
    val merchantKey: String,
    val category: String,
    val source: String,
    val rawText: String,
    val accountLast4: String?,
    val bank: String?,
    val vpa: String?,
    val reference: String?,
    val balancePaise: Long?,
    val note: String?,
    @ColumnInfo(defaultValue = "REVIEWED") val status: String,
    val possibleDuplicateOf: Long?,
    val splitsJson: String?,
    val confidence: String?,
    val tags: String? = null,
    val reimburse: String? = null,
)

/** What happened to a bank message. */
object MessageState {
    const val PAYMENT = "PAYMENT"
    const val UNREAD = "UNREAD"
    const val SKIPPED = "SKIPPED"
    const val BILL = "BILL"
    const val NOT_MONEY = "NOT_MONEY"
    const val HIDDEN = "HIDDEN"
    const val ADDED = "ADDED"
}

/**
 * Every message from a bank sender (never personal chats, never OTPs) and what the app did with it.
 * The table name is from when it only held messages the parser couldn't read.
 */
@Entity(tableName = "unread_messages", indices = [Index(value = ["sender", "receivedAt"], unique = true)])
data class UnreadMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val amountPaise: Long?,
    val dismissed: Boolean,
    @ColumnInfo(defaultValue = "UNREAD") val state: String = MessageState.UNREAD,
    val transactionId: Long? = null,
)

/** A pattern the user taught: messages like this aren't money. */
@Entity(tableName = "message_rules")
data class MessageRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderCode: String,
    val words: String,
    val coversPayments: Boolean,
    val sample: String,
    val createdAt: Long,
)

fun MessageRuleEntity.toCore() = MessageRule(id, senderCode, MessageRules.decode(words), coversPayments, sample, createdAt)

fun MessageRule.toEntity() = MessageRuleEntity(id, senderCode, MessageRules.encode(words), coversPayments, sample, createdAtMillis)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val targetId: String,
    val label: String,
    val emoji: String,
    val limitPaise: Long,
)

fun BudgetEntity.toCore() = Budget(id, runCatching { BudgetKind.valueOf(kind) }.getOrDefault(BudgetKind.CATEGORY), targetId, label, emoji, limitPaise)

fun Budget.toEntity() = BudgetEntity(id, kind.name, targetId, label, emoji, limitPaise)

/** A credit card statement or due reminder; a newer SMS for the same card and due date replaces it. */
@Entity(tableName = "card_bills", indices = [Index(value = ["last4", "dueDate"], unique = true)])
data class CardBillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bank: String?,
    val last4: String?,
    val totalDuePaise: Long,
    val minDuePaise: Long?,
    val dueDate: String,
    val receivedAt: Long,
    val rawText: String,
    val paidManually: Boolean,
)

fun CardBillEntity.toCore() = CardBill(id, bank, last4, totalDuePaise, minDuePaise, java.time.LocalDate.parse(dueDate), receivedAt, rawText, paidManually)

fun CardBill.toEntity() = CardBillEntity(id, bank, last4, totalDuePaise, minDuePaise, dueDate.toString(), receivedAtMillis, rawText, paidManually)

/** A trip or occasion; its payments carry its name as a tag. Dates are ISO strings. */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val start: String,
    val end: String?,
)

fun EventEntity.toCore() = Event(id, name, emoji, java.time.LocalDate.parse(start), end?.let(java.time.LocalDate::parse))

fun Event.toEntity() = EventEntity(id, name, emoji, start.toString(), end?.toString())

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val targetPaise: Long,
    val deadline: String?,
    val addedPaise: Long,
    val linkedCategoryId: String?,
    val createdOn: String,
)

fun GoalEntity.toCore() = Goal(
    id, name, emoji, targetPaise, deadline?.let(java.time.LocalDate::parse), addedPaise, linkedCategoryId, java.time.LocalDate.parse(createdOn),
)

fun Goal.toEntity() = GoalEntity(id, name, emoji, targetPaise, deadline?.toString(), addedPaise, linkedCategoryId, createdOn.toString())

/** The name the user gave a payee; future payments to the same payee use it too. */
@Entity(tableName = "payee_names")
data class PayeeNameEntity(
    @PrimaryKey val merchantKey: String,
    val name: String,
)

@Entity(tableName = "custom_categories")
data class CustomCategoryEntity(
    @PrimaryKey val id: String,
    val label: String,
    val emoji: String,
    val countsAsSpend: Boolean,
    val parentId: String?,
    val kind: String?,
)

/** Raw form: [CategoryCatalog] works out each subcategory's group (built-in or the user's own). */
fun CustomCategoryEntity.toCore(): Category {
    val kind = kind?.let { runCatching { Kind.valueOf(it) }.getOrNull() } ?: Kind.SPENDING
    return Category(id, label, emoji, kind == Kind.SPENDING, builtIn = false, parentId = parentId, kind = kind)
}

fun Category.toEntity() = CustomCategoryEntity(id, label, emoji, countsAsSpend, parentId, kind.name)

@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey val merchantKey: String,
    val category: String,
)

/**
 * Payments carry tags (first = main). The category column only says what kind of money it is: a
 * built-in non-spending type, or Uncategorised for spending. A tagged spend reads as its main tag.
 */
fun TransactionEntity.toCore(catalog: CategoryCatalog): Transaction {
    val tagList = Tags.decode(tags)
    val stored = catalog.byId(category)
    val main = if (stored.countsAsSpend && tagList.isNotEmpty()) catalog.tag(tagList.first()) else stored
    return Transaction(
        id = id,
        amountPaise = amountPaise,
        direction = Direction.valueOf(direction),
        timestampMillis = timestampMillis,
        merchant = merchant,
        merchantKey = merchantKey,
        category = main,
        source = Source.valueOf(source),
        rawText = rawText,
        accountLast4 = accountLast4,
        bank = bank,
        vpa = vpa,
        reference = reference,
        balancePaise = balancePaise,
        note = note,
        status = runCatching { ReviewStatus.valueOf(status) }.getOrDefault(ReviewStatus.PENDING),
        possibleDuplicateOf = possibleDuplicateOf,
        splits = SplitCodec.decode(splitsJson, catalog::byId),
        // An untagged spend still on an old built-in category needs a look.
        confidence = if (tagList.isEmpty() && !catalog.isKnown(category)) {
            Confidence.GUESS
        } else {
            confidence?.let { runCatching { Confidence.valueOf(it) }.getOrNull() } ?: Confidence.SURE
        },
        tags = tagList,
        reimburse = reimburse?.let { runCatching { Reimburse.valueOf(it) }.getOrNull() },
        hint = if (tagList.isEmpty()) catalog.hintFor(category) else null,
    )
}

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    amountPaise = amountPaise,
    direction = direction.name,
    timestampMillis = timestampMillis,
    merchant = merchant,
    merchantKey = merchantKey,
    category = if (category.isTag) Category.OTHER.id else category.id,
    source = source.name,
    rawText = rawText,
    accountLast4 = accountLast4,
    bank = bank,
    vpa = vpa,
    reference = reference,
    balancePaise = balancePaise,
    note = note,
    status = status.name,
    possibleDuplicateOf = possibleDuplicateOf,
    splitsJson = SplitCodec.encode(splits),
    confidence = confidence.name,
    tags = Tags.encode(listOfNotNull(category.tagName) + tags),
    reimburse = reimburse?.name,
)

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestampMillis DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observe(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE reference = :reference")
    suspend fun findByReference(reference: String): List<TransactionEntity>

    @Query(
        "SELECT * FROM transactions WHERE amountPaise = :amountPaise AND direction = :direction " +
            "AND timestampMillis BETWEEN :from AND :to",
    )
    suspend fun findNear(amountPaise: Long, direction: String, from: Long, to: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE timestampMillis >= :since ORDER BY timestampMillis DESC")
    suspend fun findSince(since: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE merchantKey = :key")
    suspend fun findByMerchantKey(key: String): List<TransactionEntity>

    @Insert
    suspend fun insert(entity: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<TransactionEntity>)

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("UPDATE transactions SET category = :category, confidence = 'SURE' WHERE merchantKey = :merchantKey")
    suspend fun setCategoryForMerchant(merchantKey: String, category: String)

    @Query("UPDATE transactions SET category = :to WHERE category = :from")
    suspend fun moveCategory(from: String, to: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE transactions SET merchant = :name, merchantKey = :newKey WHERE merchantKey = :oldKey")
    suspend fun renamePayee(oldKey: String, newKey: String, name: String)

    @Query("UPDATE transactions SET possibleDuplicateOf = NULL WHERE id = :id")
    suspend fun clearDuplicateFlag(id: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("UPDATE transactions SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query("UPDATE transactions SET status = 'REVIEWED' WHERE status = 'PENDING' AND timestampMillis < :before")
    suspend fun reviewAllBefore(before: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'PENDING' AND timestampMillis >= :since")
    suspend fun pendingCountSince(since: Long): Int
}

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules")
    suspend fun getAll(): List<MerchantRuleEntity>

    @Upsert
    suspend fun upsert(rule: MerchantRuleEntity)

    @Query("DELETE FROM merchant_rules")
    suspend fun deleteAll()

    @Query("DELETE FROM merchant_rules WHERE category = :category")
    suspend fun deleteForCategory(category: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<MerchantRuleEntity>)
}

@Dao
interface CustomCategoryDao {
    @Query("SELECT * FROM custom_categories ORDER BY label")
    fun observeAll(): Flow<List<CustomCategoryEntity>>

    @Query("SELECT * FROM custom_categories ORDER BY label")
    suspend fun getAll(): List<CustomCategoryEntity>

    @Upsert
    suspend fun upsert(category: CustomCategoryEntity)

    @Query("DELETE FROM custom_categories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM custom_categories")
    suspend fun deleteAll()
}

@Dao
interface UnreadMessageDao {
    @Query("SELECT * FROM unread_messages WHERE state = 'UNREAD' ORDER BY receivedAt DESC")
    fun observeOpen(): Flow<List<UnreadMessageEntity>>

    @Query("SELECT * FROM unread_messages ORDER BY receivedAt DESC")
    fun observeAll(): Flow<List<UnreadMessageEntity>>

    @Query("SELECT * FROM unread_messages ORDER BY receivedAt DESC")
    suspend fun getAll(): List<UnreadMessageEntity>

    @Query("SELECT * FROM unread_messages WHERE id = :id")
    suspend fun get(id: Long): UnreadMessageEntity?

    @Query("SELECT * FROM unread_messages WHERE transactionId = :transactionId LIMIT 1")
    suspend fun findByTransaction(transactionId: Long): UnreadMessageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: UnreadMessageEntity): Long

    @Query("UPDATE unread_messages SET state = :state, transactionId = :transactionId, dismissed = :dismissed WHERE id = :id")
    suspend fun setState(id: Long, state: String, transactionId: Long?, dismissed: Boolean)
}

@Dao
interface MessageRuleDao {
    @Query("SELECT * FROM message_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MessageRuleEntity>>

    @Query("SELECT * FROM message_rules")
    suspend fun getAll(): List<MessageRuleEntity>

    @Insert
    suspend fun insert(rule: MessageRuleEntity): Long

    @Query("DELETE FROM message_rules WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PayeeNameDao {
    @Query("SELECT * FROM payee_names")
    suspend fun getAll(): List<PayeeNameEntity>

    @Upsert
    suspend fun upsert(name: PayeeNameEntity)

    @Query("DELETE FROM payee_names")
    suspend fun deleteAll()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY label")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun getAll(): List<BudgetEntity>

    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}

@Dao
interface CardBillDao {
    @Query("SELECT * FROM card_bills ORDER BY dueDate DESC")
    fun observeAll(): Flow<List<CardBillEntity>>

    @Query("SELECT * FROM card_bills ORDER BY dueDate DESC")
    suspend fun getAll(): List<CardBillEntity>

    @Query("SELECT * FROM card_bills WHERE (last4 = :last4 OR (last4 IS NULL AND :last4 IS NULL)) AND dueDate = :dueDate LIMIT 1")
    suspend fun find(last4: String?, dueDate: String): CardBillEntity?

    @Insert
    suspend fun insert(bill: CardBillEntity): Long

    @Update
    suspend fun update(bill: CardBillEntity)

    @Query("UPDATE card_bills SET paidManually = :paid WHERE id = :id")
    suspend fun setPaid(id: Long, paid: Boolean)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY start DESC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events")
    suspend fun getAll(): List<EventEntity>

    @Upsert
    suspend fun upsert(event: EventEntity): Long

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM events")
    suspend fun deleteAll()
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY createdOn")
    fun observeAll(): Flow<List<GoalEntity>>

    @Upsert
    suspend fun upsert(goal: GoalEntity): Long

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM goals")
    suspend fun getAll(): List<GoalEntity>

    @Query("DELETE FROM goals")
    suspend fun deleteAll()
}

@Database(
    entities = [
        TransactionEntity::class, MerchantRuleEntity::class, CustomCategoryEntity::class,
        UnreadMessageEntity::class, PayeeNameEntity::class, BudgetEntity::class, CardBillEntity::class,
        MessageRuleEntity::class, EventEntity::class, GoalEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class KharchaDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun rules(): MerchantRuleDao
    abstract fun customCategories(): CustomCategoryDao
    abstract fun unreadMessages(): UnreadMessageDao
    abstract fun payeeNames(): PayeeNameDao
    abstract fun budgets(): BudgetDao
    abstract fun cardBills(): CardBillDao
    abstract fun messageRules(): MessageRuleDao
    abstract fun events(): EventDao
    abstract fun goals(): GoalDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_categories` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                        "`emoji` TEXT NOT NULL, `countsAsSpend` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        // Payments already in the app were seen before review existed, so they start as reviewed.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'REVIEWED'")
            }
        }

        // Custom categories now sit under a group.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `custom_categories` ADD COLUMN `parentId` TEXT")
            }
        }

        // Users can make their own groups, which carry a kind (spending, saving, …).
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `custom_categories` ADD COLUMN `kind` TEXT")
            }
        }

        // Possible duplicates, split payments, unreadable bank messages and payee names.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `possibleDuplicateOf` INTEGER")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `splitsJson` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `unread_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sender` TEXT NOT NULL, `body` TEXT NOT NULL, `receivedAt` INTEGER NOT NULL, `amountPaise` INTEGER, " +
                        "`dismissed` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_unread_messages_sender_receivedAt` ON `unread_messages` (`sender`, `receivedAt`)",
                )
                db.execSQL("CREATE TABLE IF NOT EXISTS `payee_names` (`merchantKey` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`merchantKey`))")
            }
        }

        // How sure the app was about each category.
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `confidence` TEXT")
            }
        }

        // Budgets and credit card bills.
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`targetId` TEXT NOT NULL, `label` TEXT NOT NULL, `emoji` TEXT NOT NULL, `limitPaise` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `card_bills` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bank` TEXT, `last4` TEXT, " +
                        "`totalDuePaise` INTEGER NOT NULL, `minDuePaise` INTEGER, `dueDate` TEXT NOT NULL, `receivedAt` INTEGER NOT NULL, " +
                        "`rawText` TEXT NOT NULL, `paidManually` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_card_bills_last4_dueDate` ON `card_bills` (`last4`, `dueDate`)")
            }
        }

        // Every bank message with what happened to it, and the patterns the user taught.
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `unread_messages` ADD COLUMN `state` TEXT NOT NULL DEFAULT 'UNREAD'")
                db.execSQL("ALTER TABLE `unread_messages` ADD COLUMN `transactionId` INTEGER")
                db.execSQL("UPDATE `unread_messages` SET `state` = 'NOT_MONEY' WHERE `dismissed` = 1")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `message_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `senderCode` TEXT NOT NULL, " +
                        "`words` TEXT NOT NULL, `coversPayments` INTEGER NOT NULL, `sample` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
                )
            }
        }

        // Tags and reimbursements on payments; trips and savings goals.
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `tags` TEXT")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `reimburse` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`emoji` TEXT NOT NULL, `start` TEXT NOT NULL, `end` TEXT)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`emoji` TEXT NOT NULL, `targetPaise` INTEGER NOT NULL, `deadline` TEXT, `addedPaise` INTEGER NOT NULL, " +
                        "`linkedCategoryId` TEXT, `createdOn` TEXT NOT NULL)",
                )
            }
        }

        fun create(context: Context): KharchaDatabase =
            Room.databaseBuilder(context, KharchaDatabase::class.java, "kharcha.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .build()
    }
}
