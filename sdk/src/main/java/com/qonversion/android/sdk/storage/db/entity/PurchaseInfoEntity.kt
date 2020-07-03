package com.qonversion.android.sdk.storage.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.qonversion.android.sdk.entity.Purchase
import com.qonversion.android.sdk.storage.db.converters.PurchaseToJsonConverter
import com.qonversion.android.sdk.storage.db.entity.PurchaseInfoEntity.Companion.TABLE_NAME

@Entity(tableName = TABLE_NAME)
@TypeConverters(PurchaseToJsonConverter::class)
data class PurchaseInfoEntity @JvmOverloads constructor(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COLUMN_ID)
    var id: Long = 0,

    @TypeConverters(PurchaseToJsonConverter::class)
    @ColumnInfo(name = COLUMN_INFO)
    var info: Purchase? = null
) {
    companion object {
        const val TABLE_NAME = "purchase_info"
        const val COLUMN_ID = "_id"
        const val COLUMN_INFO = "info"
    }
}