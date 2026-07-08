package id.my.daniza.pixheal.ui.screens.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.my.daniza.local.ProjectEntity
import id.my.daniza.local.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.core.net.toUri

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    val projects: StateFlow<List<ProjectEntity>> = projectRepository.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun createNewProject(uri: Uri): Long = withContext(Dispatchers.IO) {
        val name = "Edit_${System.currentTimeMillis()}"
        projectRepository.createProject(name = name, sourceUri = uri)
    }

    fun openProject(projectId: Long, onResult: (IntegrityResult) -> Unit) {
        viewModelScope.launch {
            val staleReason = projectRepository.checkProjectIntegrity(projectId)
            onResult(
                if (staleReason.isEmpty()) IntegrityResult.Valid(projectId)
                else IntegrityResult.Corrupt(projectId, staleReason)
            )
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId)
        }
    }
}

sealed class IntegrityResult {
    data class Valid(val projectId: Long) : IntegrityResult()
    data class Corrupt(val projectId: Long, val reasons: List<String>) : IntegrityResult()
}
