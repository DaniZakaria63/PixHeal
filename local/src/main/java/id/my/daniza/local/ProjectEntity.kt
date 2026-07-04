package id.my.daniza.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "source_image_uri")
    val sourceImageUri: String,
    @ColumnInfo(name = "output_image_uri")
    val outputImageUri: String? = null,
    @ColumnInfo(name = "step_count")
    val stepCount: Int = 0,
    val status: String = "draft",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_edited_at")
    val lastEditedAt: Long = System.currentTimeMillis(),
)
