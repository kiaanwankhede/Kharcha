package app.kharcha.data

import android.content.Context
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
import app.kharcha.core.Category
import app.kharcha.core.Direction
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
)

@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey val merchantKey: String,
    val category: String,
)

fun TransactionEntity.toCore() = Transaction(
    id = id,
    amountPaise = amountPaise,
    direction = Direction.valueOf(direction),
    timestampMillis = timestampMillis,
    merchant = merchant,
    merchantKey = merchantKey,
    category = runCatching { Category.valueOf(category) }.getOrDefault(Category.OTHER),
    source = Source.valueOf(source),
    rawText = rawText,
    accountLast4 = accountLast4,
    bank = bank,
    vpa = vpa,
    reference = reference,
    balancePaise = balancePaise,
    note = note,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    amountPaise = amountPaise,
    direction = direction.name,
    timestampMillis = timestampMillis,
    merchant = merchant,
    merchantKey = merchantKey,
    category = category.name,
    source = source.name,
    rawText = rawText,
    accountLast4 = accountLast4,
    bank = bank,
    vpa = vpa,
    reference = reference,
    balancePaise = balancePaise,
    note = note,
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

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules")
    suspend fun getAll(): List<MerchantRuleEntity>

    @Upsert
    suspend fun upsert(rule: MerchantRuleEntity)

    @Query("DELETE FROM merchant_rules")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<MerchantRuleEntity>)
}

@Database(entities = [TransactionEntity::class, MerchantRuleEntity::class], version = 1, exportSchema = true)
abstract class KharchaDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun rules(): MerchantRuleDao

    companion object {
        fun create(context: Context): KharchaDatabase =
            Room.databaseBuilder(context, KharchaDatabase::class.java, "kharcha.db").build()
    }
}
