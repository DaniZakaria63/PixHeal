package id.my.daniza.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import id.my.daniza.local.data.FileNameObj
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectHandler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var projectId: Long = 0L

    val currentProjectId: Long get() = projectId

    fun openProject(id: Long) {
        if (projectId != 0L && projectId != id) {
            throw IllegalStateException(
                "Cannot open project $id while $projectId is active. Call closeProject() first."
            )
        }
        projectId = id
    }

    fun closeProject() {
        projectId = 0L
    }

    fun projectDir(): File =
        File(context.filesDir, "${FileNameObj.ProjectFolder}/$projectId")

    fun imageFile(): File =
        File(projectDir(), FileNameObj.Image)

    fun thumbnailFile(): File =
        File(projectDir(), FileNameObj.Thumbnail)

    fun editStateFile(): File =
        File(projectDir(), FileNameObj.EditState)

    fun snapshotDir(): File =
        File(projectDir(), FileNameObj.SnapshotDir)

    fun snapshotFile(index: Int): File =
        File(snapshotDir(), "step_$index.jpg")

    fun modelDir(): File =
        File(context.filesDir, FileNameObj.ModelFolder)
}
