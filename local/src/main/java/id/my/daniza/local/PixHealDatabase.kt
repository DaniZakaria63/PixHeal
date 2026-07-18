package id.my.daniza.local

import androidx.room.Database
import androidx.room.RoomDatabase
import id.my.daniza.local.project.ProjectDao
import id.my.daniza.local.project.ProjectEntity

@Database(
    entities = [ProjectEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class PixHealDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
