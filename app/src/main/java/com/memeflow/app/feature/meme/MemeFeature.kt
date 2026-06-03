package com.memeflow.app.feature.meme

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
import com.memeflow.app.core.model.Group
import com.memeflow.app.core.model.MediaAsset
import com.memeflow.app.core.model.MemeDetails
import com.memeflow.app.core.model.MemeDraft
import com.memeflow.app.core.ui.AccessBadge
import com.memeflow.app.core.ui.CollectionCard
import com.memeflow.app.core.ui.EmptyState
import com.memeflow.app.core.ui.FullScreenError
import com.memeflow.app.core.ui.LoadingState
import com.memeflow.app.core.ui.MemeCard
import com.memeflow.app.core.ui.MemeFlowBackground
import com.memeflow.app.core.ui.MemeMedia
import com.memeflow.app.core.ui.SectionTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditMemeUiState(
    val isLoading: Boolean = true,
    val media: MediaAsset? = null,
    val caption: String = "",
    val tags: String = "",
    val visibility: AccessLevel = AccessLevel.PRIVATE,
    val selectedGroupIds: Set<String> = emptySet(),
    val availableGroups: List<Group> = emptyList(),
    val effectiveVisibility: AccessLevel? = null,
    val inheritedCollections: List<String> = emptyList(),
    val error: String? = null,
    val savedMemeId: String? = null
)

class CreateEditMemeViewModel(
    private val createMeme: suspend (MemeDraft) -> com.memeflow.app.core.model.Meme,
    private val updateMeme: suspend (String, MemeDraft) -> com.memeflow.app.core.model.Meme,
    private val loadMemeDetails: suspend (String) -> MemeDetails,
    private val loadMyProfile: suspend () -> com.memeflow.app.core.model.ProfileBundle,
    private val loadMedia: suspend (String) -> MediaAsset
) : ViewModel() {
    private val _state = MutableStateFlow(EditMemeUiState())
    val state: StateFlow<EditMemeUiState> = _state.asStateFlow()

    fun load(mediaId: String?, memeId: String?) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val profile = loadMyProfile()
                val groups = profile.groups
                when {
                    memeId != null -> {
                        val details = loadMemeDetails(memeId)
                        EditMemeUiState(
                            isLoading = false,
                            media = details.meme.media,
                            caption = details.meme.caption,
                            tags = details.meme.tags.joinToString(", "),
                            visibility = details.meme.directVisibility,
                            selectedGroupIds = details.meme.directSharedGroupIds.toSet(),
                            availableGroups = groups,
                            effectiveVisibility = details.meme.effectiveVisibility,
                            inheritedCollections = details.inheritedAccessFromCollections.map { it.collectionName }
                        )
                    }
                    mediaId != null -> {
                        val media = loadMedia(mediaId)
                        EditMemeUiState(
                            isLoading = false,
                            media = media,
                            availableGroups = groups,
                            effectiveVisibility = AccessLevel.PRIVATE
                        )
                    }
                    else -> throw IllegalArgumentException("mediaId or memeId required")
                }
            }.onSuccess { loadedState ->
                _state.value = loadedState
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.readableMessage()) }
            }
        }
    }

    fun updateCaption(value: String) = _state.update { it.copy(caption = value) }
    fun updateTags(value: String) = _state.update { it.copy(tags = value) }
    fun selectVisibility(value: AccessLevel) = _state.update {
        it.copy(visibility = value, selectedGroupIds = if (value == AccessLevel.GROUPS) it.selectedGroupIds else emptySet())
    }

    fun toggleGroup(groupId: String) {
        _state.update {
            val next = it.selectedGroupIds.toMutableSet()
            if (!next.add(groupId)) next.remove(groupId)
            it.copy(selectedGroupIds = next)
        }
    }

    fun save(memeId: String?) {
        val current = _state.value
        val mediaId = current.media?.id ?: return
        viewModelScope.launch {
            runCatching {
                val draft = MemeDraft(
                    mediaAssetId = mediaId,
                    caption = current.caption,
                    tags = current.tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
                    visibility = current.visibility,
                    sharedGroupIds = current.selectedGroupIds.toList()
                )
                if (memeId == null) createMeme(draft) else updateMeme(memeId, draft)
            }.onSuccess { meme ->
                _state.update { it.copy(savedMemeId = meme.id) }
            }.onFailure { error ->
                _state.update { it.copy(error = error.readableMessage()) }
            }
        }
    }
}

class MemeDetailsViewModel(
    private val loadDetails: suspend (String) -> MemeDetails
) : ViewModel() {
    private val _state = MutableStateFlow<Pair<Boolean, MemeDetails?>>(true to null)
    val state: StateFlow<Pair<Boolean, MemeDetails?>> = _state.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load(memeId: String) {
        viewModelScope.launch {
            _state.value = true to null
            runCatching { loadDetails(memeId) }
                .onSuccess { _state.value = false to it }
                .onFailure { _error.value = it.readableMessage(); _state.value = false to null }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditMemeScreen(
    mediaId: String?,
    memeId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel = memeflowViewModel(key = memeId ?: mediaId ?: "new-meme") {
        CreateEditMemeViewModel(
            createMeme = { draft -> container.createMemeUseCase(draft) },
            updateMeme = { id, draft -> container.updateMemeUseCase(id, draft) },
            loadMemeDetails = { id -> container.loadMemeDetailsUseCase(id) },
            loadMyProfile = { container.loadMyProfileUseCase() },
            loadMedia = { id -> container.pollMediaStatusUseCase(id) }
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(mediaId, memeId) { viewModel.load(mediaId, memeId) }
    LaunchedEffect(state.savedMemeId) { state.savedMemeId?.let(onSaved) }

    MemeFlowBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(title = { Text(if (memeId == null) "Создать мем" else "Редактировать мем") })
            when {
                state.isLoading -> LoadingState()
                state.error != null -> FullScreenError(state.error ?: "")
                state.media == null -> EmptyState("Нет media asset", "Вернитесь в upload flow.")
                else -> {
                    val media = state.media ?: return@Column
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            MemeMedia(
                                mediaUrl = media.thumbnailUrl ?: media.storageUrl ?: media.originalUrl,
                                kind = media.kind
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = state.caption,
                                onValueChange = viewModel::updateCaption,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Caption") }
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = state.tags,
                                onValueChange = viewModel::updateTags,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Tags через запятую") }
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
                        state.effectiveVisibility?.let {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Effective access")
                                    AccessBadge(it)
                                    if (state.inheritedCollections.isNotEmpty()) {
                                        Text("Доступ расширен через подборки: ${state.inheritedCollections.joinToString()}")
                                    }
                                }
                            }
                        }
                        item {
                            Button(onClick = { viewModel.save(memeId) }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (memeId == null) "Создать мем" else "Сохранить изменения")
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
fun MemeDetailsScreen(
    memeId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel = memeflowViewModel(key = memeId) {
        MemeDetailsViewModel(loadDetails = { id -> container.loadMemeDetailsUseCase(id) })
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(memeId) { viewModel.load(memeId) }

    MemeFlowBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(title = { Text("Meme Details") })
            when {
                state.first -> LoadingState()
                error != null -> FullScreenError(error ?: "")
                state.second == null -> EmptyState("Мем не найден", "Возможно, у вас нет доступа.")
                else -> {
                    val details = state.second ?: return@Column
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { MemeCard(meme = details.meme, onClick = {}) }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Direct access")
                                AccessBadge(details.meme.directVisibility)
                                Text("Effective access")
                                AccessBadge(details.meme.effectiveVisibility)
                                if (details.inheritedAccessFromCollections.isNotEmpty()) {
                                    Text("Доступ расширен через подборки.")
                                }
                            }
                        }
                        if (details.collections.isNotEmpty()) {
                            item { SectionTitle("Подборки") }
                            items(details.collections, key = { it.id }) { collection ->
                                CollectionCard(collection = collection, onClick = {})
                            }
                        }
                        if (details.canEdit) {
                            item {
                                Button(onClick = { onEdit(details.meme.id) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Редактировать")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
