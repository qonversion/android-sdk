package com.qonversion.android.sdk.storage.db.dao

import androidx.room.*
import com.qonversion.android.sdk.entity.Purchase
import com.qonversion.android.sdk.storage.db.converters.PurchaseToJsonConverter
import com.qonversion.android.sdk.storage.db.entity.PurchaseInfoEntity
import com.qonversion.android.sdk.storage.db.entity.PurchaseInfoEntity.Companion.COLUMN_ID
import com.qonversion.android.sdk.storage.db.entity.PurchaseInfoEntity.Companion.COLUMN_INFO
import com.qonversion.android.sdk.storage.db.entity.PurchaseInfoEntity.Companion.TABLE_NAME

@Dao
@TypeConverters(PurchaseToJsonConverter::class)
interface PurchaseInfoDao {

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(@TypeConverters(PurchaseToJsonConverter::class) purchase: PurchaseInfoEntity): Long

    @Transaction
    @Query("""
        SELECT COUNT($COLUMN_ID) > 0 FROM $TABLE_NAME 
        WHERE $COLUMN_INFO = :purchase 
    """)
    fun exist(@TypeConverters(PurchaseToJsonConverter::class) purchase: Purchase): Boolean

    @Transaction
    @Query("""
        SELECT $COLUMN_ID FROM $TABLE_NAME 
        WHERE $COLUMN_INFO = :purchase 
    """)
    fun getId(@TypeConverters(PurchaseToJsonConverter::class) purchase: Purchase): Int


    @Transaction
    @Query("""
        SELECT COUNT($COLUMN_ID) FROM $TABLE_NAME
    """)
    fun count(): Int

    @Transaction
    fun insertOrUpdate(@TypeConverters(PurchaseToJsonConverter::class) purchase: PurchaseInfoEntity): Long {
        val exist = exist(purchase = purchase.info!!)
        if (!exist) {
            return insertOrReplace(purchase)
        } else {
            return getId(purchase.info!!).toLong()
        }
    }
}