/**
 * Baby Tracker — Native Android (Kotlin)
 *
 * A privacy-first baby tracking app with Nostr-based encrypted storage
 * and parent-to-parent notes.
 *
 * Copyright (c) 2026 Turkey
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for full license details.
 */

package com.turkbot.babytracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.turkbot.babytracker.data.dao.*
import com.turkbot.babytracker.data.entities.*

@Database(
    entities = [
        Child::class,
        Feeding::class,
        Sleep::class,
        Weight::class,
        Milestone::class,
        Note::class,
        Diaper::class,
        Pumping::class,
        HealthRecord::class,
        Bath::class,
        Vaccine::class,
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun childDao(): ChildDao
    abstract fun feedingDao(): FeedingDao
    abstract fun sleepDao(): SleepDao
    abstract fun weightDao(): WeightDao
    abstract fun milestoneDao(): MilestoneDao
    abstract fun noteDao(): NoteDao
    abstract fun diaperDao(): DiaperDao
    abstract fun pumpingDao(): PumpingDao
    abstract fun healthRecordDao(): HealthRecordDao
    abstract fun bathDao(): BathDao
    abstract fun vaccineDao(): VaccineDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration 2→3: drop the old chat_messages table (gift-wrap DMs
         * replaced by notes that piggyback on the sync payload) and create
         * the new notes table. All tracking data (feedings, sleeps, weights,
         * etc.) is preserved — only chat history is lost.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS chat_messages")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS notes (
                        id TEXT NOT NULL PRIMARY KEY,
                        authorPubkey TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )"""
                )
            }
        }

        /**
         * Migration 3→4: add the baths table for tracking baby baths.
         * All existing data is preserved.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS baths (
                        id TEXT NOT NULL PRIMARY KEY,
                        childId TEXT NOT NULL,
                        time INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        note TEXT
                    )"""
                )
            }
        }

        /**
         * Migration 4→5: add authorPubkey column to all tracking tables so
         * each entry shows which parent logged it. Existing rows get NULL
         * (unknown author — data pre-dates this feature). No data loss.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE feedings ADD COLUMN authorPubkey TEXT")
                db.execSQL("ALTER TABLE sleeps ADD COLUMN authorPubkey TEXT")
                db.execSQL("ALTER TABLE weights ADD COLUMN authorPubkey TEXT")
                db.execSQL("ALTER TABLE milestones ADD COLUMN authorPubkey TEXT")
                db.execSQL("ALTER TABLE diapers ADD COLUMN authorPubkey TEXT")
                db.execSQL("ALTER TABLE pumpings ADD COLUMN authorPubkey TEXT")
                db.execSQL("ALTER TABLE health_records ADD COLUMN authorPubkey TEXT")
                db.execSQL("ALTER TABLE baths ADD COLUMN authorPubkey TEXT")
            }
        }

        /**
         * Migration 5→6: create the vaccines table for tracking vaccinations.
         * All existing data is preserved.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS vaccines (
                        id TEXT NOT NULL PRIMARY KEY,
                        childId TEXT NOT NULL,
                        vaccineType TEXT NOT NULL,
                        dateAdministered INTEGER NOT NULL,
                        nextDueDate INTEGER,
                        doseNumber TEXT,
                        note TEXT,
                        authorPubkey TEXT
                    )"""
                )
            }
        }

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "baby-tracker"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build().also {
                    INSTANCE = it
                }
            }
        }
    }
}
