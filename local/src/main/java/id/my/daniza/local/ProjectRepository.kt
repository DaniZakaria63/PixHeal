package id.my.daniza.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val projectDao: ProjectDao,
) {

    fun getAllProjects(): Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    suspend fun deleteProject(id: Long) {
        projectDao.deleteProject(id)
        deleteProjectFiles(id)
    }

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
            val dir = projectDir(projectId)
            dir.mkdirs()

            val imageFile = File(dir, "image.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(imageFile).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Failed to open source image")

            val thumbnailFile = File(dir, "thumbnail.jpg")
            generateThumbnail(imageFile, thumbnailFile)

            File(dir, "edit_state.json").writeText(
                """{"projectId":$projectId,"history":[],"redoStack":[]}"""
            )

            projectDao.updateProject(
                projectDao.getProjectById(projectId)!!.copy(
                    thumbnailUri = File(projectDir(projectId), "thumbnail.jpg").absolutePath,
                )
            )

            return projectId
        } catch (e: Exception) {
            projectDao.deleteProject(projectId)
            deleteProjectFiles(projectId)
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

        val hasStaleFile = File(projectDir(projectId), "edit_state.json").exists()
        if (!hasStaleFile) {
            reasons.add("Editing state file is missing")
        }

        return reasons
    }

    fun deleteProjectFiles(projectId: Long) {
        val dir = projectDir(projectId)
        if (dir.exists()) dir.deleteRecursively()
    }

    private fun projectDir(projectId: Long): File {
        return File(context.filesDir, "projects/$projectId")
    }

    fun regenerateThumbnail(projectId: Long) {
        val imageFile = File(projectDir(projectId), "image.jpg")
        val thumbnailFile = File(projectDir(projectId), "thumbnail.jpg")
        generateThumbnail(imageFile, thumbnailFile)
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
        val scaled = Bitmap.createScaledBitmap(cropped, 300, 300, true)
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
