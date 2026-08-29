package ca.gmode.triprecorder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TripEntity::class, PointEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "gmode-trip-recorder.db",
            ).addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE points ADD COLUMN accelerationPeakXMs2 REAL")
                database.execSQL("ALTER TABLE points ADD COLUMN accelerationPeakYMs2 REAL")
                database.execSQL("ALTER TABLE points ADD COLUMN accelerationPeakZMs2 REAL")
            }
        }
    }
}
