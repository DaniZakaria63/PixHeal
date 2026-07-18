package id.my.daniza.local.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import id.my.daniza.local.project.ProjectDao
import id.my.daniza.local.project.ProjectEntity
import id.my.daniza.local.project.ProjectHandler
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val projectDao: ProjectDao,
    private val projectHandler: ProjectHandler,
) {

    fun getAllProjects(): Flow<List<ProjectEntity>> = projectDao.getAllProjects()


    suspend fun createProject(
        name: String,
        sourceUri: Uri,
    ): Long {
        val projectId = projectDao.insertProject(
            ProjectEntity(
                name = name,
                sourceImageUri = sourceUri.toString(),
                status = "draft",
            )
        )

        try {
            projectHandler.openProject(projectId)
            projectHandler.projectDir().also { it.mkdirs() }

            val imageFile = projectHandler.imageFile()
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(imageFile).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Failed to open source image")

            val thumbnailFile = projectHandler.thumbnailFile()
            generateThumbnail(imageFile, thumbnailFile)

            projectHandler.editStateFile().writeText(
                """{"projectId":$projectId,"history":[],"redoStack":[]}"""
            )

            projectDao.updateProject(
                projectDao.getProjectById(projectId)!!.copy(
                    thumbnailUri = thumbnailFile.absolutePath,
                )
            )

            return projectId
        } catch (e: Exception) {
            projectDao.deleteProject(projectId)
            deleteProject(projectId)
            throw e
        }
    }

    suspend fun checkProjectIntegrity(projectId: Long) : List<String>{

        val project = projectDao.getProjectById(projectId)
        val reasons = mutableListOf<String>()

        if (project == null) {
            reasons.add("Project record not found")
        }else{
            val sourceUri = try {
                project.sourceImageUri.toUri()
            } catch (_: Exception) { null }
            if (sourceUri == null) reasons.add("Source image URI is malformed")
        }

        projectHandler.openProject(projectId)
        val hasStaleFile = projectHandler.editStateFile().exists()
        if (!hasStaleFile) {
            reasons.add("Editing state file is missing")
        }

        return reasons
    }

    suspend fun updateProject(status: String, stepCount: Int): List<String> {
        val project = projectDao.getProjectById(projectHandler.currentProjectId)
        val reason = mutableListOf<String>()

        if(project == null){
            reason.add("Project record not found")
        }else{
            projectDao.updateProject(
                project.copy(
                    status = status,
                    stepCount = stepCount,
                    lastEditedAt = System.currentTimeMillis()
                )
            )
            regenerateThumbnail()
        }
        return reason
    }

    suspend fun deleteProject(id: Long) {
        projectDao.deleteProject(id)
        val dir = File(context.filesDir, "${id.my.daniza.local.model.FileNameObj.ProjectFolder}/$id")
        if (dir.exists()) dir.deleteRecursively()
    }

    fun regenerateThumbnail() {
        generateThumbnail(
            sourceFile = projectHandler.imageFile(),
            destFile = projectHandler.thumbnailFile()
        )
    }

    private fun generateThumbnail(sourceFile: File, destFile: File) {
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(sourceFile.absolutePath, 300, 300)
        }
        val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, options)
            ?: throw IllegalStateException("Failed to decode image for thumbnail")

        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        val cropped = Bitmap.createBitmap(bitmap, x, y, size, size)
        val scaled = cropped.scale(300, 300)
        FileOutputStream(destFile).use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, output)
        }
        scaled.recycle()
        cropped.recycle()
        if (cropped !== bitmap) bitmap.recycle()
    }

    private fun calculateSampleSize(path: String, reqWidth: Int, reqHeight: Int): Int {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        var sampleSize = 1
        while (options.outWidth / sampleSize > reqWidth || options.outHeight / sampleSize > reqHeight) {
            sampleSize *= 2
        }
        return sampleSize
    }
}