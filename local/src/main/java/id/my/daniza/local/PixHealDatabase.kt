package id.my.daniza.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PixHealDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
