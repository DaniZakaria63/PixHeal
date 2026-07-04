package id.my.daniza.local

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao
) {

    fun getAllProjects(): Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    suspend fun getProjectById(id: Long): ProjectEntity? = projectDao.getProjectById(id)

    suspend fun createProject(
        name: String,
        sourceImageUri: String,
    ): Long {
        val entity = ProjectEntity(
            name = name,
            sourceImageUri = sourceImageUri,
            status = "draft",
        )
        return projectDao.insertProject(entity)
    }

    suspend fun updateProject(project: ProjectEntity) = projectDao.updateProject(project)

    suspend fun deleteProject(id: Long) = projectDao.deleteProject(id)
}
