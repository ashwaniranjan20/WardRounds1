package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AdmissionEntity
import com.example.data.model.AuditLogEntity
import com.example.data.model.ClinicalNoteEntity
import com.example.data.model.DischargeSummaryEntity
import com.example.data.model.FollowUpTaskEntity
import com.example.data.model.HandoverEntryEntity
import com.example.data.model.InvestigationEventEntity
import com.example.data.model.InvestigationOrderEntity
import com.example.data.model.InvestigationResultEntity
import com.example.data.model.PatientEntity
import com.example.data.model.RoundEntryEntity
import com.example.data.model.UserEntity
import com.example.data.model.VitalObservationEntity

@Database(
    entities = [
        UserEntity::class,
        PatientEntity::class,
        AdmissionEntity::class,
        RoundEntryEntity::class,
        VitalObservationEntity::class,
        ClinicalNoteEntity::class,
        InvestigationOrderEntity::class,
        InvestigationEventEntity::class,
        InvestigationResultEntity::class,
        DischargeSummaryEntity::class,
        FollowUpTaskEntity::class,
        AuditLogEntity::class,
        HandoverEntryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun wardRoundDao(): WardRoundDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `handover_entries` (
                        `id` TEXT NOT NULL,
                        `patientId` TEXT NOT NULL,
                        `uhid` TEXT NOT NULL,
                        `patientName` TEXT NOT NULL,
                        `ward` TEXT NOT NULL,
                        `bedNumber` TEXT NOT NULL,
                        `shiftDate` TEXT NOT NULL,
                        `shiftType` TEXT NOT NULL,
                        `illnessSeverity` TEXT NOT NULL,
                        `situation` TEXT NOT NULL,
                        `background` TEXT NOT NULL,
                        `assessment` TEXT NOT NULL,
                        `recommendationAndActions` TEXT NOT NULL,
                        `contingencyPlan` TEXT NOT NULL,
                        `pendingCriticalItems` TEXT NOT NULL,
                        `outgoingClinician` TEXT NOT NULL,
                        `outgoingTime` TEXT NOT NULL,
                        `incomingClinician` TEXT,
                        `acknowledgedAt` TEXT,
                        `synthesisConfirmed` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `lastUpdated` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wardround_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

