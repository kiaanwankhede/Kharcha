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
import app.kharcha.core.Category
import app.kharcha.core.CategoryCatalog
import app.kharcha.core.Confidence
import app.kharcha.core.Direction
import app.kharcha.core.Kind
import app.kharcha.core.ReviewStatus
import app.kharcha.core.Source
import app.kharcha.core.SplitCodec
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
)

/** A bank message the parser could not read, kept so the user can add it by hand. */
@Entity(tableName = "unread_messages", indices = [Index(value = ["sender", "receivedAt"], unique = true)])
data class UnreadMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val amountPaise: Long?,
    val dismissed: Boolean,
)

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

fun TransactionEntity.toCore(catalog: CategoryCatalog) = Transaction(
    id = id,
    amountPaise = amountPaise,
    direction = Direction.valueOf(direction),
    timestampMillis = timestampMillis,
    merchant = merchant,
    merchantKey = merchantKey,
    category = catalog.byId(category),
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
    confidence = confidence?.let { runCatching { Confidence.valueOf(it) }.getOrNull() } ?: Confidence.SURE,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    amountPaise = amountPaise,
    direction = direction.name,
    timestampMillis = timestampMillis,
    merchant = merchant,
    merchantKey = merchantKey,
    category = category.id,
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
    @Query("SELECT * FROM unread_messages WHERE dismissed = 0 ORDER BY receivedAt DESC")
    fun observeOpen(): Flow<List<UnreadMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: UnreadMessageEntity): Long

    @Query("UPDATE unread_messages SET dismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: Long)
}

@Dao
interface PayeeNameDao {
    @Query("SELECT * FROM payee_names")
    suspend fun getAll(): List<PayeeNameEntity>

    @Upsert
    suspend fun upsert(name: PayeeNameEntity)
}

@Database(
    entities = [
        TransactionEntity::class, MerchantRuleEntity::class, CustomCategoryEntity::class,
        UnreadMessageEntity::class, PayeeNameEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class KharchaDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun rules(): MerchantRuleDao
    abstract fun customCategories(): CustomCategoryDao
    abstract fun unreadMessages(): UnreadMessageDao
    abstract fun payeeNames(): PayeeNameDao

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

        fun create(context: Context): KharchaDatabase =
            Room.databaseBuilder(context, KharchaDatabase::class.java, "kharcha.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
    }
}
