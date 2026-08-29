package ca.gmode.triprecorder.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationOneToTwoPreservesPointsAndAddsNullableShockAxes() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                "INSERT INTO trips " +
                    "(id,title,tripType,status,startAt,needsSync,updatedAtEpochMs,distanceMeters,pointCount) " +
                    "VALUES ('trip-1','Migration','street','active','2026-08-28T12:00:00Z',1,1,0,1)",
            )
            execSQL(
                "INSERT INTO points " +
                    "(id,tripId,sequence,recordedAt,latitude,longitude,isCharging,networkType,synced) " +
                    "VALUES ('trip-1:0','trip-1',0,'2026-08-28T12:00:01Z',44.0,-79.0,0,'offline',0)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DATABASE, 2, true, AppDatabase.MIGRATION_1_2)
        migrated.query(
            "SELECT id, accelerationPeakXMs2, accelerationPeakYMs2, accelerationPeakZMs2 FROM points",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("trip-1:0", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE = "gmode-migration-test"
    }
}
