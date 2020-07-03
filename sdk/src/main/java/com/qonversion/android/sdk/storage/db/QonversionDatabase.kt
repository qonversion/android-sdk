package com.qonversion.android.sdk.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.qonversion.android.sdk.storage.db.dao.PurchaseInfoDao
import com.qonversion.android.sdk.storage.db.entity.PurchaseInfoEntity

@Database(
    entities = [
        PurchaseInfoEntity::class
    ],
    exportSchema = true,
    version = 1
)
abstract class QonversionDatabase : RoomDatabase() {

    abstract fun purchaseInfo(): PurchaseInfoDao

    companion object {
        const val DATABASE_NAME = "qonversion_database.db"
    }
}