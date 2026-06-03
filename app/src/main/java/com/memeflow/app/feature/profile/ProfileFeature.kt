package com.memeflow.app.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.memeflow.app.core.model.ProfileBundle
import com.memeflow.app.core.model.SessionSnapshot
import com.memeflow.app.core.model.UserSummary
import com.memeflow.app.core.ui.Avatar
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

data class ProfileUiState(
    val isLoading: Boolean = true,
    val bundle: ProfileBundle? = null,
    val error: String? = null
)

class PublicProfileViewModel(
    private val loadProfile: suspend (String) -> ProfileBundle
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun load(userId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { loadProfile(userId) }
                .onSuccess { bundle -> _state.update { it.copy(isLoading = false, bundle = bundle) } }
                .onFailure { error -> _state.update { it.copy(isLoading = false, error = error.readableMessage()) } }
        }
    }
}

class MyProfileViewModel(
    private val loadProfile: suspend () -> ProfileBundle
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { loadProfile() }
                .onSuccess { bundle -> _state.update { it.copy(isLoading = false, bundle = bundle) } }
                .onFailure { error -> _state.update { it.copy(isLoading = false, error = error.readableMessage()) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    userId: String,
    onBack: () -> Unit,
    onOpenMeme: (String) -> Unit,
    onOpenCollection: (String) -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel = memeflowViewModel(key = userId) {
        PublicProfileViewModel(loadProfile = { container.loadPublicProfileUseCase(it) })
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(userId) { viewModel.load(userId) }

    MemeFlowBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(title = { Text("Публичный профиль") })
            ProfileContent(
                state = state,
                onOpenMeme = onOpenMeme,
                onOpenCollection = onOpenCollection,
                extraContent = {}
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyProfileScreen(
    padding: PaddingValues,
    session: SessionSnapshot,
    onRequireAuth: () -> Unit,
    onOpenMeme: (String) -> Unit,
    onOpenCollection: (String) -> Unit,
    onCreateCollection: () -> Unit,
    onGroups: () -> Unit,
    onInvitations: () -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel = memeflowViewModel {
        MyProfileViewModel(loadProfile = { container.loadMyProfileUseCase() })
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(session.isAuthorized) {
        if (session.isAuthorized) viewModel.load()
    }

    MemeFlowBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TopAppBar(title = { Text("Мой профиль") })
            if (!session.isAuthorized) {
                EmptyState("Гостевой режим", "Войдите, чтобы увидеть свои private и groups мемы.")
                Button(
                    onClick = onRequireAuth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text("Открыть авторизацию")
                }
            } else {
                ProfileContent(
                    state = state,
                    onOpenMeme = onOpenMeme,
                    onOpenCollection = onOpenCollection,
                    extraContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = onCreateCollection, modifier = Modifier.weight(1f)) { Text("Новая подборка") }
                            Button(onClick = onGroups, modifier = Modifier.weight(1f)) { Text("Группы") }
                        }
                        TextButton(onClick = onInvitations) { Text("Открыть инвайты") }
                    }
                )
            }
        }
    }
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    onOpenMeme: (String) -> Unit,
    onOpenCollection: (String) -> Unit,
    extraContent: @Composable () -> Unit
) {
    when {
        state.isLoading -> LoadingState()
        state.error != null -> FullScreenError(state.error ?: "")
        state.bundle == null -> EmptyState("Профиль не найден", "Попробуйте открыть экран позже.")
        else -> {
            val bundle = state.bundle
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Avatar(user = bundle.profile.toSummary())
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(bundle.profile.displayName, style = MaterialTheme.typography.titleLarge)
                            Text("@${bundle.profile.login}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(bundle.profile.bio)
                        }
                    }
                }
                item { extraContent() }
                if (bundle.groups.isNotEmpty()) {
                    item { SectionTitle("Мои группы") }
                    items(bundle.groups, key = { it.id }) { group ->
                        Text(text = group.name, style = MaterialTheme.typography.titleMedium)
                    }
                }
                item { SectionTitle("Мемы") }
                if (bundle.memes.isEmpty()) {
                    item { EmptyState("Мемов пока нет", "Создайте первый мем через upload.") }
                } else {
                    items(bundle.memes, key = { it.id }) { meme ->
                        MemeCard(meme = meme, onClick = { onOpenMeme(meme.id) })
                    }
                }
                item { SectionTitle("Подборки") }
                if (bundle.collections.isEmpty()) {
                    item { EmptyState("Подборок пока нет", "Подборки пригодятся для управления доступами.") }
                } else {
                    items(bundle.collections, key = { it.id }) { collection ->
                        CollectionCard(collection = collection, onClick = { onOpenCollection(collection.id) })
                    }
                }
            }
        }
    }
}

private fun com.memeflow.app.core.model.UserProfile.toSummary() = UserSummary(
    id = id,
    login = login,
    displayName = displayName,
    avatarUrl = avatarUrl
)
