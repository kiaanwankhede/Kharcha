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
import app.kharcha.core.Direction
import app.kharcha.core.Kind
import app.kharcha.core.ReviewStatus
import app.kharcha.core.Source
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
)

@Entity(tableName = "custom_categories")
data class CustomCategoryEntity(
    @PrimaryKey val id: String,
    val label: String,
    val emoji: String,
    val countsAsSpend: Boolean,
    val parentId: String?,
)

fun CustomCategoryEntity.toCore(): Category {
    val group = Category.GROUPS.firstOrNull { it.id == parentId } ?: Category.G_OTHER
    return Category(id, label, emoji, group.kind == Kind.SPENDING, builtIn = false, parentId = group.id, kind = group.kind)
}

fun Category.toEntity() = CustomCategoryEntity(id, label, emoji, countsAsSpend, parentId)

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

    @Insert
    suspend fun insert(entity: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<TransactionEntity>)

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("UPDATE transactions SET category = :category WHERE merchantKey = :merchantKey")
    suspend fun setCategoryForMerchant(merchantKey: String, category: String)

    @Query("UPDATE transactions SET category = :to WHERE category = :from")
    suspend fun moveCategory(from: String, to: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

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

@Database(
    entities = [TransactionEntity::class, MerchantRuleEntity::class, CustomCategoryEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class KharchaDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun rules(): MerchantRuleDao
    abstract fun customCategories(): CustomCategoryDao

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

        fun create(context: Context): KharchaDatabase =
            Room.databaseBuilder(context, KharchaDatabase::class.java, "kharcha.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
