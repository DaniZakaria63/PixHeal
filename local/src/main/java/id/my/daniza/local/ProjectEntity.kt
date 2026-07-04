package id.my.daniza.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "template_type")
    val templateType: String,
    @ColumnInfo(name = "thumbnail_uri")
    val thumbnailUri: String? = null,
    @ColumnInfo(name = "source_image_uri")
    val sourceImageUri: String? = null,
    @ColumnInfo(name = "result_image_uri")
    val resultImageUri: String? = null,
    @ColumnInfo(name = "editing_state_json")
    val editingStateJson: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_edited_at")
    val lastEditedAt: Long = System.currentTimeMillis(),
)
