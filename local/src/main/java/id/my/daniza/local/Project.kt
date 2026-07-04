package id.my.daniza.local

import java.time.LocalDateTime

data class Project(
    val id: Long,
    val name: String,
    val templateType: String,
    val createdAt: LocalDateTime,
    val thumbnailUri: String? = null,
)
