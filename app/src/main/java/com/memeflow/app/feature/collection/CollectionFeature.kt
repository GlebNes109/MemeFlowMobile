package com.memeflow.app.feature.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.memeflow.app.LocalAppContainer
import com.memeflow.app.core.common.memeflowViewModel
import com.memeflow.app.core.common.readableMessage
import com.memeflow.app.core.common.uiLabel
import com.memeflow.app.core.model.AccessLevel
import com.memeflow.app.core.model.CollectionDetails
import com.memeflow.app.core.model.CollectionDraft
import com.memeflow.app.core.model.CollectionWarning
import com.memeflow.app.core.model.Meme
import com.memeflow.app.core.ui.AccessBadge
import com.memeflow.app.core.ui.CollectionCard
import com.memeflow.app.core.ui.EmptyState
import com.memeflow.app.core.ui.FullScreenError
import com.memeflow.app.core.ui.LoadingState
import com.memeflow.app.core.ui.MemeCard
import com.memeflow.app.core.ui.MemeFlowBackground
import com.memeflow.app.core.ui.SectionTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditCollectionUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val description: String = "",
    val visibility: AccessLevel = AccessLevel.PRIVATE,
    val selectedGroupIds: Set<String> = emptySet(),
    val availableGroups: List<com.memeflow.app.core.model.Group> = emptyList(),
    val availableMemes: List<Meme> = emptyList(),
    val selectedMemeIds: Set<String> = emptySet(),
    val warnings: List<CollectionWarning> = emptyList(),
    val error: String? = null,
    val savedCollectionId: String? = null
)

class CreateEditCollectionViewModel(
    private val createCollection: suspend (CollectionDraft) -> com.memeflow.app.core.model.MemeCollection,
    private val updateCollection: suspend (String, CollectionDraft) -> com.memeflow.app.core.model.MemeCollection,
    private val loadCollectionDetails: suspend (String) -> CollectionDetails,
    private val loadMyProfile: suspend () -> com.memeflow.app.core.model.ProfileBundle
) : ViewModel() {
    private val _state = MutableStateFlow(EditCollectionUiState())
    val state: StateFlow<EditCollectionUiState> = _state.asStateFlow()

    fun load(collectionId: String?) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val profile = loadMyProfile()
                val baseState = EditCollectionUiState(
                    isLoading = false,
                    availableGroups = profile.groups,
                    availableMemes = profile.memes
                )
                if (collectionId == null) {
                    baseState
                } else {
                    val details = loadCollectionDetails(collectionId)
                    baseState.copy(
                        name = details.collection.name,
                        description = details.collection.description,
                        visibility = details.collection.visibility,
                        selectedGroupIds = details.collection.sharedGroupIds.toSet(),
                        selectedMemeIds = details.collection.itemIds.toSet()
                    ).withWarnings()
                }
            }.onSuccess { _state.value = it }
                .onFailure { error -> _state.update { it.copy(isLoading = false, error = error.readableMessage()) } }
        }
    }

    fun updateName(value: String) = _state.update { it.copy(name = value) }
    fun updateDescription(value: String) = _state.update { it.copy(description = value) }
    fun selectVisibility(value: AccessLevel) = _state.update {
        it.copy(visibility = value, selectedGroupIds = if (value == AccessLevel.GROUPS) it.selectedGroupIds else emptySet()).withWarnings()
    }

    fun toggleGroup(groupId: String) = _state.update {
        val next = it.selectedGroupIds.toMutableSet()
        if (!next.add(groupId)) next.remove(groupId)
        it.copy(selectedGroupIds = next).withWarnings()
    }

    fun toggleMeme(memeId: String) = _state.update {
        val next = it.selectedMemeIds.toMutableSet()
        if (!next.add(memeId)) next.remove(memeId)
        it.copy(selectedMemeIds = next).withWarnings()
    }

    fun save(collectionId: String?) {
        val current = _state.value
        viewModelScope.launch {
            runCatching {
                val draft = CollectionDraft(
                    name = current.name,
                    description = current.description,
                    visibility = current.visibility,
                    sharedGroupIds = current.selectedGroupIds.toList(),
                    memeIds = current.selectedMemeIds.toList()
                )
                if (collectionId == null) createCollection(draft) else updateCollection(collectionId, draft)
            }.onSuccess { collection ->
                _state.update { it.copy(savedCollectionId = collection.id) }
            }.onFailure { error ->
                _state.update { it.copy(error = error.readableMessage()) }
            }
        }
    }

    private fun EditCollectionUiState.withWarnings(): EditCollectionUiState {
        val warnings = availableMemes
            .filter { it.id in selectedMemeIds }
            .mapNotNull { meme ->
                val widened = when {
                    meme.directVisibility == AccessLevel.PRIVATE && visibility == AccessLevel.GROUPS -> AccessLevel.GROUPS
                    meme.directVisibility == AccessLevel.PRIVATE && visibility == AccessLevel.PUBLIC -> AccessLevel.PUBLIC
                    meme.directVisibility == AccessLevel.GROUPS && visibility == AccessLevel.PUBLIC -> AccessLevel.PUBLIC
                    else -> null
                }
                widened?.let {
                    CollectionWarning(
                        memeId = meme.id,
                        memeCaption = meme.caption,
                        from = meme.directVisibility,
                        to = it
                    )
                }
            }
        return copy(warnings = warnings)
    }
}

class CollectionDetailsViewModel(
    private val loadDetails: suspend (String) -> CollectionDetails
) : ViewModel() {
    private val _state = MutableStateFlow<Pair<Boolean, CollectionDetails?>>(true to null)
    val state: StateFlow<Pair<Boolean, CollectionDetails?>> = _state.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load(collectionId: String) {
        viewModelScope.launch {
            runCatching { loadDetails(collectionId) }
                .onSuccess { _state.value = false to it }
                .onFailure { _error.value = it.readableMessage(); _state.value = false to null }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditCollectionScreen(
    collectionId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel = memeflowViewModel(key = collectionId ?: "new-collection") {
        CreateEditCollectionViewModel(
            createCollection = { draft -> container.createCollectionUseCase(draft) },
            updateCollection = { id, draft -> container.updateCollectionUseCase(id, draft) },
            loadCollectionDetails = { id -> container.loadCollectionDetailsUseCase(id) },
            loadMyProfile = { container.loadMyProfileUseCase() }
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(collectionId) { viewModel.load(collectionId) }
    LaunchedEffect(state.savedCollectionId) { state.savedCollectionId?.let(onSaved) }

    MemeFlowBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(title = { Text(if (collectionId == null) "Создать подборку" else "Редактировать подборку") })
            when {
                state.isLoading -> LoadingState()
                state.error != null -> FullScreenError(state.error ?: "")
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = state.name,
                                onValueChange = viewModel::updateName,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Название") }
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = state.description,
                                onValueChange = viewModel::updateDescription,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Описание") }
                            )
                        }
                        item { SectionTitle("Доступ") }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AccessLevel.entries.forEach { access ->
                                    FilterChip(
                                        selected = state.visibility == access,
                                        onClick = { viewModel.selectVisibility(access) },
                                        label = { Text(access.uiLabel()) }
                                    )
                                }
                            }
                        }
                        if (state.visibility == AccessLevel.GROUPS) {
                            items(state.availableGroups, key = { it.id }) { group ->
                                Button(onClick = { viewModel.toggleGroup(group.id) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (group.id in state.selectedGroupIds) "Убрать ${group.name}" else "Добавить ${group.name}")
                                }
                            }
                        }
                        item { SectionTitle("Мемы в подборке") }
                        items(state.availableMemes, key = { it.id }) { meme ->
                            Button(onClick = { viewModel.toggleMeme(meme.id) }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (meme.id in state.selectedMemeIds) "Убрать: ${meme.caption}" else "Добавить: ${meme.caption}")
                            }
                        }
                        if (state.warnings.isNotEmpty()) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Внимание: эта подборка расширит доступ к мемам.", color = MaterialTheme.colorScheme.error)
                                    state.warnings.forEach { warning ->
                                        Text("• ${warning.memeCaption}: ${warning.from.uiLabel()} -> ${warning.to.uiLabel()}")
                                    }
                                }
                            }
                        }
                        item {
                            Button(onClick = { viewModel.save(collectionId) }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (collectionId == null) "Создать подборку" else "Сохранить подборку")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailsScreen(
    collectionId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenMeme: (String) -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel = memeflowViewModel(key = collectionId) {
        CollectionDetailsViewModel(loadDetails = { id -> container.loadCollectionDetailsUseCase(id) })
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(collectionId) { viewModel.load(collectionId) }

    MemeFlowBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(title = { Text("Collection Details") })
            when {
                state.first -> LoadingState()
                error != null -> FullScreenError(error ?: "")
                state.second == null -> EmptyState("Подборка недоступна", "Проверьте права доступа.")
                else -> {
                    val details = state.second ?: return@Column
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CollectionCard(collection = details.collection, onClick = {})
                                AccessBadge(details.collection.visibility)
                            }
                        }
                        items(details.items, key = { it.id }) { meme ->
                            MemeCard(meme = meme, onClick = { onOpenMeme(meme.id) })
                        }
                        if (details.canEdit) {
                            item {
                                Button(onClick = { onEdit(details.collection.id) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Редактировать подборку")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
