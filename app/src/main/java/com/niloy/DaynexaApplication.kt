package com.niloy

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.niloy.data.local.AppDatabase
import com.niloy.data.repository.TaskRepositoryImpl
import com.niloy.domain.repository.TaskRepository
import com.niloy.domain.service.BackupService
import com.niloy.domain.service.FocentraIntegrationManager
import com.niloy.domain.service.SchedulingService
import com.niloy.domain.service.TaskReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DaynexaApplication : Application() {
    lateinit var database: AppDatabase
    lateinit var repository: TaskRepository
    lateinit var focentraIntegrationManager: FocentraIntegrationManager
    lateinit var schedulingService: SchedulingService
    lateinit var backupService: BackupService
    lateinit var dataPortabilityManager: com.niloy.domain.service.DataPortabilityManager
    lateinit var reminderScheduler: TaskReminderScheduler

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `app_classifications` (
                    `packageName` TEXT NOT NULL,
                    `appName` TEXT NOT NULL,
                    `categories` TEXT NOT NULL,
                    `qualityRating` TEXT NOT NULL,
                    `customProductivityType` TEXT NOT NULL DEFAULT 'NEUTRAL',
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`packageName`)
                )
            """.trimIndent())
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `reminderEnabled` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `reminderOffsetMinutes` INTEGER")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Deprecated
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `focentra_study_sessions` (
                    `sessionId` TEXT NOT NULL,
                    `subject` TEXT NOT NULL,
                    `topic` TEXT NOT NULL,
                    `startTime` INTEGER NOT NULL,
                    `endTime` INTEGER NOT NULL,
                    `duration` INTEGER NOT NULL,
                    `completionStatus` TEXT NOT NULL,
                    `focusScore` INTEGER NOT NULL,
                    `schemaVersion` INTEGER NOT NULL DEFAULT 1,
                    `importedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`sessionId`)
                )
            """.trimIndent())
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `website_classifications`")
            db.execSQL("DROP TABLE IF EXISTS `website_events`")
            db.execSQL("DROP TABLE IF EXISTS `domain_rules`")
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `isProductive` INTEGER NOT NULL DEFAULT 1")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `app_categories` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `isProductive` INTEGER NOT NULL DEFAULT 1,
                    `createdAt` INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrenceType` TEXT NOT NULL DEFAULT 'DAILY'")
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrenceInterval` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrenceDayOfMonth` INTEGER")
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrenceMonthOfYear` INTEGER")
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrenceEndType` TEXT NOT NULL DEFAULT 'NEVER'")
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrenceEndDate` TEXT")
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrenceCount` INTEGER")
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `seriesId` INTEGER")
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `startDate` TEXT NOT NULL DEFAULT ''")

            db.execSQL("ALTER TABLE `task_occurrences` ADD COLUMN `rescheduledStartTime` INTEGER")
            db.execSQL("ALTER TABLE `task_occurrences` ADD COLUMN `rescheduledEndTime` INTEGER")
            db.execSQL("ALTER TABLE `task_occurrences` ADD COLUMN `rescheduledDate` TEXT")
            db.execSQL("ALTER TABLE `task_occurrences` ADD COLUMN `isException` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `task_occurrences` ADD COLUMN `notes` TEXT")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `task_templates` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `description` TEXT NOT NULL DEFAULT '',
                    `categoryId` INTEGER NOT NULL,
                    `defaultDurationMinutes` INTEGER NOT NULL DEFAULT 45,
                    `startTime` INTEGER,
                    `endTime` INTEGER,
                    `isAllDay` INTEGER NOT NULL DEFAULT 0,
                    `recurrenceType` TEXT NOT NULL DEFAULT 'DAILY',
                    `recurringDays` TEXT NOT NULL DEFAULT '',
                    `recurrenceInterval` INTEGER NOT NULL DEFAULT 1,
                    `reminderEnabled` INTEGER NOT NULL DEFAULT 0,
                    `reminderOffsetMinutes` INTEGER NOT NULL DEFAULT 0,
                    `createdAt` INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `priority` TEXT NOT NULL DEFAULT 'MEDIUM'")
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `deadlineDate` TEXT")
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `deadlineTime` INTEGER")
            db.execSQL("ALTER TABLE `task_templates` ADD COLUMN `priority` TEXT NOT NULL DEFAULT 'MEDIUM'")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `goals` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `targetType` TEXT NOT NULL,
                    `targetPeriod` TEXT NOT NULL,
                    `targetValue` INTEGER NOT NULL,
                    `unit` TEXT NOT NULL DEFAULT 'tasks',
                    `categoryId` INTEGER,
                    `currentStreak` INTEGER NOT NULL DEFAULT 0,
                    `longestStreak` INTEGER NOT NULL DEFAULT 0,
                    `lastCompletedPeriod` TEXT,
                    `isActive` INTEGER NOT NULL DEFAULT 1,
                    `createdAt` INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_categoryId` ON `tasks` (`categoryId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_occurrences_date_state` ON `task_occurrences` (`date`, `state`)")
        }
    }

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "daynexa.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
            .fallbackToDestructiveMigration()
            .build()

        repository = TaskRepositoryImpl(
            database.categoryDao(),
            database.taskDao(),
            database.taskOccurrenceDao(),
            database.settingDao(),
            database.taskTemplateDao(),
            database.goalDao()
        )

        focentraIntegrationManager = FocentraIntegrationManager(
            applicationContext,
            database.focentraStudySessionDao(),
            database.settingDao()
        )

        schedulingService = SchedulingService()
        backupService = BackupService()
        dataPortabilityManager = com.niloy.domain.service.DataPortabilityManager(database, backupService)
        reminderScheduler = TaskReminderScheduler(applicationContext)

        initDemoData()
    }

    private fun initDemoData() {
        kotlinx.coroutines.MainScope().launch {
            try {
                val seededSetting = database.settingDao().getByKey("routine_seeded_v4")
                if (seededSetting?.value != "true") {
                    database.taskOccurrenceDao().deleteAll()
                    database.taskDao().deleteAll()
                    database.categoryDao().deleteAll()

                    val studyId = repository.saveCategory(com.niloy.domain.model.Category(name = "Study", icon = "book", color = 0xFF3B82F6.toInt()))
                    val revisionId = repository.saveCategory(com.niloy.domain.model.Category(name = "Revision", icon = "refresh", color = 0xFF6366F1.toInt()))
                    val mealId = repository.saveCategory(com.niloy.domain.model.Category(name = "Meal", icon = "restaurant", color = 0xFFF59E0B.toInt()))
                    val personalId = repository.saveCategory(com.niloy.domain.model.Category(name = "Personal", icon = "person", color = 0xFF10B981.toInt()))
                    val spiritualId = repository.saveCategory(com.niloy.domain.model.Category(name = "Spiritual", icon = "favorite", color = 0xFF8B5CF6.toInt()))
                    val breakId = repository.saveCategory(com.niloy.domain.model.Category(name = "Break", icon = "timer", color = 0xFF06B6D4.toInt()))
                    val napId = repository.saveCategory(com.niloy.domain.model.Category(name = "Nap", icon = "bed", color = 0xFF818CF8.toInt()))
                    val othersId = repository.saveCategory(com.niloy.domain.model.Category(name = "Others", icon = "more_horiz", color = 0xFF14B8A6.toInt()))

                    val sat = java.time.DayOfWeek.SATURDAY
                    val sun = java.time.DayOfWeek.SUNDAY
                    val mon = java.time.DayOfWeek.MONDAY
                    val tue = java.time.DayOfWeek.TUESDAY
                    val wed = java.time.DayOfWeek.WEDNESDAY
                    val thu = java.time.DayOfWeek.THURSDAY
                    val fri = java.time.DayOfWeek.FRIDAY

                    data class SeedTask(val name: String, val categoryId: Long, val start: Long, val end: Long, val days: Set<java.time.DayOfWeek>)

                    val seedTasks = listOf(
                        // Daily "ঘুম থেকে উঠা" at 07:00 AM (Others)
                        SeedTask("ঘুম থেকে উঠা", othersId, 420L, 420L, setOf(sat, sun, mon, tue, wed, thu, fri)),

                        // Saturday (শনিবার)
                        SeedTask("নাস্তা ও Fresh Air", othersId, 420L, 450L, setOf(sat)),
                        SeedTask("হাঁটাহাঁটি", othersId, 450L, 460L, setOf(sat)),
                        SeedTask("ওয়াশরুম ও স্নান", personalId, 460L, 490L, setOf(sat)),
                        SeedTask("প্রার্থনা", spiritualId, 490L, 530L, setOf(sat)),
                        SeedTask("বিরতি & Prepare for study", breakId, 530L, 540L, setOf(sat)),
                        SeedTask("◆ Study - || Physics || -", studyId, 540L, 810L, setOf(sat)),
                        SeedTask("ছোট বিরতি", breakId, 810L, 820L, setOf(sat)),
                        SeedTask("দুপুরের ভাত", mealId, 820L, 850L, setOf(sat)),
                        SeedTask("বিরতি", breakId, 850L, 860L, setOf(sat)),
                        SeedTask("Nap", napId, 860L, 880L, setOf(sat)),
                        SeedTask("◆ Study - || English 1st Paper || -", studyId, 880L, 1060L, setOf(sat)),
                        SeedTask("প্রার্থনা, নাস্তা & ছোট বিরতি", othersId, 1060L, 1110L, setOf(sat)),
                        SeedTask("◆ Study - || Higher Math || -", studyId, 1110L, 1320L, setOf(sat)),
                        SeedTask("রাতের ভাত", mealId, 1320L, 1340L, setOf(sat)),
                        SeedTask("বিরতি", breakId, 1340L, 1350L, setOf(sat)),
                        SeedTask("◆ Revision - || Physics || -", revisionId, 1350L, 1410L, setOf(sat)),
                        SeedTask("◆ Revision - || Higher Math || -", revisionId, 1410L, 1470L, setOf(sat)),
                        SeedTask("Get Ready & Go To Sleep", othersId, 1470L, 1500L, setOf(sat)),

                        // Sunday (রবিবার)
                        SeedTask("নাস্তা ও Fresh Air", othersId, 420L, 450L, setOf(sun)),
                        SeedTask("হাঁটাহাঁটি", othersId, 450L, 460L, setOf(sun)),
                        SeedTask("ওয়াশরুম ও স্নান", personalId, 460L, 490L, setOf(sun)),
                        SeedTask("প্রার্থনা", spiritualId, 490L, 530L, setOf(sun)),
                        SeedTask("বিরতি & Prepare for study", breakId, 530L, 540L, setOf(sun)),
                        SeedTask("◆ Study - || Physics || -", studyId, 540L, 810L, setOf(sun)),
                        SeedTask("ছোট বিরতি", breakId, 810L, 820L, setOf(sun)),
                        SeedTask("দুপুরের ভাত", mealId, 820L, 850L, setOf(sun)),
                        SeedTask("বিরতি", breakId, 850L, 860L, setOf(sun)),
                        SeedTask("Nap", napId, 860L, 880L, setOf(sun)),
                        SeedTask("◆ Study - || ICT || -", studyId, 880L, 1060L, setOf(sun)),
                        SeedTask("প্রার্থনা, নাস্তা & ছোট বিরতি", othersId, 1060L, 1110L, setOf(sun)),
                        SeedTask("Shopping", othersId, 1110L, 1190L, setOf(sun)),
                        SeedTask("◆ Study - || Biology || -", studyId, 1190L, 1320L, setOf(sun)),
                        SeedTask("রাতের ভাত", mealId, 1320L, 1340L, setOf(sun)),
                        SeedTask("বিরতি", breakId, 1340L, 1350L, setOf(sun)),
                        SeedTask("◆ Revision - || Chemistry || -", revisionId, 1350L, 1410L, setOf(sun)),
                        SeedTask("◆ Revision - || Biology || -", revisionId, 1410L, 1470L, setOf(sun)),
                        SeedTask("Get Ready & Go To Sleep", othersId, 1470L, 1500L, setOf(sun)),

                        // Monday (সোমবার)
                        SeedTask("নাস্তা ও Fresh Air", othersId, 420L, 440L, setOf(mon)),
                        SeedTask("হাঁটাহাঁটি", othersId, 440L, 450L, setOf(mon)),
                        SeedTask("ওয়াশরুম ও স্নান", personalId, 450L, 480L, setOf(mon)),
                        SeedTask("প্রার্থনা", spiritualId, 480L, 500L, setOf(mon)),
                        SeedTask("কলেজ যাওয়া-আসা ও ক্লাস", othersId, 500L, 930L, setOf(mon)),
                        SeedTask("Get Fresh", personalId, 930L, 940L, setOf(mon)),
                        SeedTask("দুপুরের ভাত", mealId, 940L, 960L, setOf(mon)),
                        SeedTask("Nap", napId, 960L, 1060L, setOf(mon)),
                        SeedTask("প্রার্থনা, নাস্তা & ছোট বিরতি", othersId, 1060L, 1110L, setOf(mon)),
                        SeedTask("◆ Study - || Physics|| -", studyId, 1110L, 1320L, setOf(mon)),
                        SeedTask("রাতের ভাত", mealId, 1320L, 1340L, setOf(mon)),
                        SeedTask("বিরতি", breakId, 1340L, 1350L, setOf(mon)),
                        SeedTask("◆ Revision - || Chemistry || -", revisionId, 1350L, 1410L, setOf(mon)),
                        SeedTask("◆ Study - || Bangla 1st Paper|| -", studyId, 1410L, 1470L, setOf(mon)),
                        SeedTask("Get Ready & Go To Sleep", othersId, 1470L, 1500L, setOf(mon)),

                        // Tuesday (মঙ্গলবার)
                        SeedTask("নাস্তা ও Fresh Air", othersId, 420L, 450L, setOf(tue)),
                        SeedTask("হাঁটাহাঁটি", othersId, 450L, 460L, setOf(tue)),
                        SeedTask("ওয়াশরুম ও স্নান", personalId, 460L, 490L, setOf(tue)),
                        SeedTask("প্রার্থনা", spiritualId, 490L, 530L, setOf(tue)),
                        SeedTask("বিরতি & Prepare for study", breakId, 530L, 540L, setOf(tue)),
                        SeedTask("◆ Study - || Higher Math || -", studyId, 540L, 810L, setOf(tue)),
                        SeedTask("ছোট বিরতি", breakId, 810L, 820L, setOf(tue)),
                        SeedTask("দুপুরের ভাত", mealId, 820L, 850L, setOf(tue)),
                        SeedTask("বিরতি", breakId, 850L, 860L, setOf(tue)),
                        SeedTask("Nap", napId, 860L, 880L, setOf(tue)),
                        SeedTask("◆ Study - || English 2nd Paper || -", studyId, 880L, 1060L, setOf(tue)),
                        SeedTask("প্রার্থনা, নাস্তা & ছোট বিরতি", othersId, 1060L, 1110L, setOf(tue)),
                        SeedTask("◆ Study - || Biology || -", studyId, 1110L, 1320L, setOf(tue)),
                        SeedTask("রাতের ভাত", mealId, 1320L, 1340L, setOf(tue)),
                        SeedTask("বিরতি", breakId, 1340L, 1350L, setOf(tue)),
                        SeedTask("◆ Revision - || Higher Math || -", revisionId, 1350L, 1410L, setOf(tue)),
                        SeedTask("◆ Revision - || Biology || -", revisionId, 1410L, 1470L, setOf(tue)),
                        SeedTask("Get Ready & Go To Sleep", othersId, 1470L, 1500L, setOf(tue)),

                        // Wednesday (বুধবার)
                        SeedTask("নাস্তা ও Fresh Air", othersId, 420L, 450L, setOf(wed)),
                        SeedTask("হাঁটাহাঁটি", othersId, 450L, 460L, setOf(wed)),
                        SeedTask("ওয়াশরুম ও স্নান", personalId, 460L, 490L, setOf(wed)),
                        SeedTask("প্রার্থনা", spiritualId, 490L, 530L, setOf(wed)),
                        SeedTask("বিরতি & Prepare for study", breakId, 530L, 540L, setOf(wed)),
                        SeedTask("◆ Study - || Physics || -", studyId, 540L, 810L, setOf(wed)),
                        SeedTask("ছোট বিরতি", breakId, 810L, 820L, setOf(wed)),
                        SeedTask("দুপুরের ভাত", mealId, 820L, 850L, setOf(wed)),
                        SeedTask("বিরতি", breakId, 850L, 860L, setOf(wed)),
                        SeedTask("Nap", napId, 860L, 880L, setOf(wed)),
                        SeedTask("◆ Study - || English 1st Paper || -", studyId, 880L, 1035L, setOf(wed)),
                        SeedTask("Shopping", othersId, 1035L, 1110L, setOf(wed)),
                        SeedTask("ছোট বিরতি", breakId, 1110L, 1140L, setOf(wed)),
                        SeedTask("◆ Study - || Biology || -", studyId, 1140L, 1320L, setOf(wed)),
                        SeedTask("রাতের ভাত", mealId, 1320L, 1340L, setOf(wed)),
                        SeedTask("বিরতি", breakId, 1340L, 1350L, setOf(wed)),
                        SeedTask("◆ Revision - || ICT || -", revisionId, 1350L, 1410L, setOf(wed)),
                        SeedTask("◆ Revision - || Biology || -", revisionId, 1410L, 1470L, setOf(wed)),
                        SeedTask("Get Ready & Go To Sleep", othersId, 1470L, 1500L, setOf(wed)),

                        // Thursday (বৃহস্পতিবার)
                        SeedTask("নাস্তা ও Fresh Air", othersId, 420L, 450L, setOf(thu)),
                        SeedTask("হাঁটাহাঁটি", othersId, 450L, 460L, setOf(thu)),
                        SeedTask("ওয়াশরুম ও স্নান", personalId, 460L, 490L, setOf(thu)),
                        SeedTask("প্রার্থনা", spiritualId, 490L, 530L, setOf(thu)),
                        SeedTask("বিরতি & Prepare for study", breakId, 530L, 540L, setOf(thu)),
                        SeedTask("◆ Study - || Chemistry|| -", studyId, 540L, 810L, setOf(thu)),
                        SeedTask("ছোট বিরতি", breakId, 810L, 820L, setOf(thu)),
                        SeedTask("দুপুরের ভাত", mealId, 820L, 850L, setOf(thu)),
                        SeedTask("বিরতি", breakId, 850L, 860L, setOf(thu)),
                        SeedTask("Nap", napId, 860L, 880L, setOf(thu)),
                        SeedTask("◆ Study - || English 1st Paper || -", studyId, 880L, 1035L, setOf(thu)),
                        SeedTask("Shopping", othersId, 1035L, 1110L, setOf(thu)),
                        SeedTask("ছোট বিরতি", breakId, 1110L, 1140L, setOf(thu)),
                        SeedTask("◆ Study - || Biology || -", studyId, 1140L, 1320L, setOf(thu)),
                        SeedTask("রাতের ভাত", mealId, 1320L, 1340L, setOf(thu)),
                        SeedTask("বিরতি", breakId, 1340L, 1350L, setOf(thu)),
                        SeedTask("◆ Revision - || English 1st Paper|| -", revisionId, 1350L, 1410L, setOf(thu)),
                        SeedTask("◆ Revision - || Biology || -", revisionId, 1410L, 1470L, setOf(thu)),
                        SeedTask("Get Ready & Go To Sleep", othersId, 1470L, 1500L, setOf(thu)),

                        // Friday (শুক্রবার)
                        SeedTask("নাস্তা ও Fresh Air", othersId, 420L, 450L, setOf(fri)),
                        SeedTask("হাঁটাহাঁটি", othersId, 450L, 460L, setOf(fri)),
                        SeedTask("ওয়াশরুম ও স্নান", personalId, 460L, 490L, setOf(fri)),
                        SeedTask("প্রার্থনা", spiritualId, 490L, 530L, setOf(fri)),
                        SeedTask("বিরতি & Prepare for study", breakId, 530L, 540L, setOf(fri)),
                        SeedTask("◆ Study - || Physics || -", studyId, 540L, 810L, setOf(fri)),
                        SeedTask("ছোট বিরতি", breakId, 810L, 820L, setOf(fri)),
                        SeedTask("দুপুরের ভাত", mealId, 820L, 850L, setOf(fri)),
                        SeedTask("বিরতি", breakId, 850L, 860L, setOf(fri)),
                        SeedTask("Nap", napId, 860L, 880L, setOf(fri)),
                        SeedTask("◆ Study - || English 2nd Paper || -", studyId, 880L, 1035L, setOf(fri)),
                        SeedTask("Shopping", othersId, 1035L, 1110L, setOf(fri)),
                        SeedTask("ছোট বিরতি", breakId, 1110L, 1140L, setOf(fri)),
                        SeedTask("◆ Study - || Chemistry || -", studyId, 1140L, 1320L, setOf(fri)),
                        SeedTask("রাতের ভাত", mealId, 1320L, 1340L, setOf(fri)),
                        SeedTask("বিরতি", breakId, 1340L, 1350L, setOf(fri)),
                        SeedTask("◆ Revision - || English 2nd Paper|| -", revisionId, 1350L, 1410L, setOf(fri)),
                        SeedTask("◆ Revision - || Chemistry || -", revisionId, 1410L, 1470L, setOf(fri)),
                        SeedTask("Get Ready & Go To Sleep", othersId, 1470L, 1500L, setOf(fri))
                    )

                    seedTasks.forEach { task ->
                        repository.saveTask(com.niloy.domain.model.Task(
                            name = task.name,
                            categoryId = task.categoryId,
                            startTime = task.start,
                            endTime = task.end,
                            isRecurring = true,
                            recurringDays = task.days
                        ))
                    }

                    database.settingDao().insert(com.niloy.data.local.entity.SettingEntity("routine_seeded_v4", "true"))
                }
            } catch (e: Exception) {
                // Ignore seed errors on launch
            }
        }
    }
}
