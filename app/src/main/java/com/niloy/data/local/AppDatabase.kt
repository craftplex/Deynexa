package com.niloy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.niloy.data.local.dao.*
import com.niloy.data.local.entity.*

@Database(
    entities = [
        CategoryEntity::class,
        TaskEntity::class,
        TaskOccurrenceEntity::class,
        SettingEntity::class,
        FocentraStudySessionEntity::class,
        TaskTemplateEntity::class,
        GoalEntity::class
    ],
    version = 10,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun taskDao(): TaskDao
    abstract fun taskOccurrenceDao(): TaskOccurrenceDao
    abstract fun settingDao(): SettingDao
    abstract fun focentraStudySessionDao(): FocentraStudySessionDao
    abstract fun taskTemplateDao(): TaskTemplateDao
    abstract fun goalDao(): GoalDao
}
