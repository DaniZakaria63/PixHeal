package id.my.daniza.pixheal.viewmodel.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.my.daniza.local.ProjectEntity
import id.my.daniza.local.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    val projects: StateFlow<List<ProjectEntity>> = projectRepository.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _corruptProject = MutableStateFlow<CorruptProjectState?>(null)
    val corruptProject: StateFlow<CorruptProjectState?> = _corruptProject.asStateFlow()

    suspend fun createNewProject(uri: Uri): Long = withContext(Dispatchers.IO) {
        val name = "Edit_${System.currentTimeMillis()}"
        projectRepository.createProject(name = name, sourceUri = uri)
    }

    fun openProject(projectId: Long) {
        viewModelScope.launch {
            val staleReason = projectRepository.checkProjectIntegrity(projectId)
            if (staleReason.isEmpty()) {
                _corruptProject.value = CorruptProjectState.Valid(projectId)
            } else {
                _corruptProject.value = CorruptProjectState.Corrupt(projectId, staleReason)
            }
        }
    }

    fun dismissCorruptDialog() {
        _corruptProject.value = null
    }

    fun deleteCorruptProject() {
        val state = _corruptProject.value as? CorruptProjectState.Corrupt ?: return
        viewModelScope.launch {
            projectRepository.deleteProject(state.projectId)
            _corruptProject.value = null
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId)
        }
    }
}

sealed class CorruptProjectState {
    data class Valid(val projectId: Long) : CorruptProjectState()
    data class Corrupt(val projectId: Long, val reasons: List<String>) : CorruptProjectState()
}
