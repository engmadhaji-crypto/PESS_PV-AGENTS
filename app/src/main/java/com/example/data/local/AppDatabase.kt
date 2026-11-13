package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.CustomerDao
import com.example.data.local.dao.InstallmentDao
import com.example.data.local.dao.InstallmentPaymentDao
import com.example.data.local.dao.InstallmentPlanDao
import com.example.data.local.dao.SyncMetadataDao
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.InstallmentPaymentEntity
import com.example.data.local.entity.InstallmentPlanEntity
import com.example.data.local.entity.SyncMetadataEntity

@Database(
    entities = [
        CustomerEntity::class,
        InstallmentPlanEntity::class,
        InstallmentPaymentEntity::class,
        SyncMetadataEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun installmentDao(): InstallmentDao
    abstract fun customerDao(): CustomerDao
    abstract fun installmentPlanDao(): InstallmentPlanDao
    abstract fun installmentPaymentDao(): InstallmentPaymentDao
    abstract fun syncMetadataDao(): SyncMetadataDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "installment_manager.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
