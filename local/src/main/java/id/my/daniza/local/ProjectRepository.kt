package id.my.daniza.local

import java.time.LocalDateTime

/**
 * Repository for previous project data.
 * Currently returns in-memory data. Will be backed by Room/DataStore for persistence.
 */
class ProjectRepository {

    fun getProjects(): List<Project> = listOf(
        Project(
            id = 1,
            name = "Wedding Photo",
            templateType = "Super Resolution",
            createdAt = LocalDateTime.now().minusDays(2),
        ),
        Project(
            id = 2,
            name = "Old Family Portrait",
            templateType = "Photo Restore",
            createdAt = LocalDateTime.now().minusDays(5),
        ),
        Project(
            id = 3,
            name = "Vintage Car",
            templateType = "Colorize",
            createdAt = LocalDateTime.now().minusWeeks(1),
        ),
        Project(
            id = 4,
            name = "Street Scene",
            templateType = "Denoise",
            createdAt = LocalDateTime.now().minusWeeks(2),
        ),
    )
}
