package id.my.daniza.pixheal.ui.screens.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.my.daniza.local.ProjectEntity
import id.my.daniza.local.ProjectRepository
import id.my.daniza.pixheal.data.editing.EditingStateManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.net.toUri

sealed class IntegrityResult {
    data class Valid(val projectId: Long) : IntegrityResult()
    data class Corrupt(val projectId: Long, val reasons: List<String>) : IntegrityResult()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val editingStateManager: EditingStateManager,
) : ViewModel() {

    val projects: StateFlow<List<ProjectEntity>> = projectRepository.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun createNewProject(uri: Uri): Long {
        val name = "Project ${System.currentTimeMillis()}"
        return projectRepository.createProject(
            name = name,
            sourceImageUri = uri.toString(),
        )
    }

    fun openProject(projectId: Long, onResult: (IntegrityResult) -> Unit) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            val reasons = mutableListOf<String>()

            if (project == null) {
                reasons.add("Project record not found")
            }

            if (project != null) {
                val sourceUri = try {
                    project.sourceImageUri.toUri()
                } catch (_: Exception) { null }
                if (sourceUri == null) reasons.add("Source image URI is malformed")
            }

            if (!editingStateManager.hasStateFile(projectId)) {
                reasons.add("Editing state file is missing")
            }

            onResult(
                if (reasons.isEmpty()) IntegrityResult.Valid(projectId)
                else IntegrityResult.Corrupt(projectId, reasons)
            )
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId)
            editingStateManager.deleteProjectFiles(projectId)
        }
    }
}
