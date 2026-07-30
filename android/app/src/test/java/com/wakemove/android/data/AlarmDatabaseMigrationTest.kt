package com.wakemove.android.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.wakemove.android.domain.VibrationIntensity
import com.wakemove.android.domain.VibrationPattern
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class AlarmDatabaseMigrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `migration 1 to 2 preserves alarms and adds vibration defaults`() = runBlocking {
        createVersionOneDatabase()

        val database = Room.databaseBuilder(
            context,
            AlarmDatabase::class.java,
            DATABASE_NAME,
        )
            .addMigrations(AlarmDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val alarm = RoomAlarmRepository(database.alarmDao()).getAlarm("legacy-alarm")

            assertNotNull(alarm)
            assertEquals("default", alarm?.soundId)
            assertEquals(VibrationPattern.GENTLE, alarm?.vibrationPattern)
            assertEquals(VibrationIntensity.MEDIUM, alarm?.vibrationIntensity)
        } finally {
            database.close()
        }
    }

    private fun createVersionOneDatabase() {
        val path = context.getDatabasePath(DATABASE_NAME)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { database ->
            VERSION_ONE_SCHEMA.forEach(database::execSQL)
            database.execSQL(
                """
                INSERT INTO alarms (
                    id, time_nano_of_day, label, enabled, repeat_days, sound_id,
                    vibration_enabled, snooze_minutes, snooze_limit, challenge_type,
                    target_count, created_at_epoch_second, created_at_nano,
                    updated_at_epoch_second, updated_at_nano
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "legacy-alarm",
                    LocalTime.of(7, 30).toNanoOfDay(),
                    "旧闹钟",
                    1,
                    0,
                    "default",
                    1,
                    5,
                    3,
                    "SQUAT",
                    10,
                    1_700_000_000L,
                    0,
                    1_700_000_000L,
                    0,
                ),
            )
            database.version = 1
        }
    }

    companion object {
        private const val DATABASE_NAME = "migration-test.db"
        private val VERSION_ONE_SCHEMA = listOf(
            """
            CREATE TABLE IF NOT EXISTS `alarms` (
                `id` TEXT NOT NULL, `time_nano_of_day` INTEGER NOT NULL,
                `label` TEXT NOT NULL, `enabled` INTEGER NOT NULL,
                `repeat_days` INTEGER NOT NULL, `sound_id` TEXT NOT NULL,
                `vibration_enabled` INTEGER NOT NULL,
                `snooze_minutes` INTEGER NOT NULL, `snooze_limit` INTEGER NOT NULL,
                `challenge_type` TEXT NOT NULL, `target_count` INTEGER NOT NULL,
                `created_at_epoch_second` INTEGER NOT NULL,
                `created_at_nano` INTEGER NOT NULL,
                `updated_at_epoch_second` INTEGER NOT NULL,
                `updated_at_nano` INTEGER NOT NULL, PRIMARY KEY(`id`)
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS `ringing_sessions` (
                `id` TEXT NOT NULL, `alarm_id` TEXT NOT NULL,
                `scheduled_at_epoch_second` INTEGER NOT NULL,
                `scheduled_at_nano` INTEGER NOT NULL,
                `started_at_epoch_second` INTEGER NOT NULL,
                `started_at_nano` INTEGER NOT NULL, `snooze_count` INTEGER NOT NULL,
                `challenge_type` TEXT NOT NULL, `target_count` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `pending_schedule_at_epoch_second` INTEGER,
                `pending_schedule_at_nano` INTEGER, PRIMARY KEY(`id`)
            )
            """.trimIndent(),
            """
            CREATE INDEX IF NOT EXISTS `index_ringing_sessions_alarm_id`
            ON `ringing_sessions` (`alarm_id`)
            """.trimIndent(),
            """
            CREATE INDEX IF NOT EXISTS
            `index_ringing_sessions_status_started_at_epoch_second_started_at_nano`
            ON `ringing_sessions`
            (`status`, `started_at_epoch_second`, `started_at_nano`)
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS `alarm_events` (
                `id` TEXT NOT NULL, `alarm_id` TEXT NOT NULL,
                `scheduled_at_epoch_second` INTEGER NOT NULL,
                `scheduled_at_nano` INTEGER NOT NULL,
                `started_at_epoch_second` INTEGER, `started_at_nano` INTEGER,
                `finished_at_epoch_second` INTEGER, `finished_at_nano` INTEGER,
                `challenge_type` TEXT NOT NULL, `snooze_count` INTEGER NOT NULL,
                `result` TEXT NOT NULL, PRIMARY KEY(`id`)
            )
            """.trimIndent(),
            """
            CREATE INDEX IF NOT EXISTS `index_alarm_events_alarm_id`
            ON `alarm_events` (`alarm_id`)
            """.trimIndent(),
            """
            CREATE INDEX IF NOT EXISTS
            `index_alarm_events_scheduled_at_epoch_second_scheduled_at_nano`
            ON `alarm_events` (`scheduled_at_epoch_second`, `scheduled_at_nano`)
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS `app_settings` (
                `key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`)
            )
            """.trimIndent(),
        )
    }
}
