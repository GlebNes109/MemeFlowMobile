package com.memeflow.app.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.memeflow.app.LocalAppContainer
import com.memeflow.app.core.common.memeflowViewModel
import com.memeflow.app.core.common.readableMessage
import com.memeflow.app.core.model.FeedScope
import com.memeflow.app.core.model.Meme
import com.memeflow.app.core.model.PagedResult
import com.memeflow.app.core.model.SessionSnapshot
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

data class FeedUiState(
    val items: List<Meme> = emptyList(),
    val nextCursor: String? = null,
    val isLoading: Boolean = false,
    val isPaginating: Boolean = false,
    val error: String? = null
)

class FeedViewModel(
    private val loadFeed: suspend (FeedScope, String?) -> PagedResult<Meme>
) : ViewModel() {
    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    fun load(scope: FeedScope, reset: Boolean = false) {
        if (_state.value.isLoading || _state.value.isPaginating) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = reset || it.items.isEmpty(),
                    isPaginating = !reset && it.items.isNotEmpty(),
                    error = null
                )
            }
            runCatching { loadFeed(scope, if (reset) null else _state.value.nextCursor) }
                .onSuccess { page ->
                    _state.update {
                        val merged = if (reset) page.items else it.items + page.items
                        it.copy(
                            items = merged.distinctBy(Meme::id),
                            nextCursor = page.nextCursor,
                            isLoading = false,
                            isPaginating = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, isPaginating = false, error = error.readableMessage()) }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    padding: PaddingValues,
    session: SessionSnapshot,
    onSearch: () -> Unit,
    onOpenMeme: (String) -> Unit,
    onRequireAuth: () -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel = memeflowViewModel {
        FeedViewModel(loadFeed = { scope, cursor -> container.loadFeedUseCase(scope, cursor) })
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = if (session.isAuthorized) FeedScope.ACCESSIBLE else FeedScope.PUBLIC

    LaunchedEffect(scope) {
        viewModel.load(scope, reset = true)
    }

    LaunchedEffect(listState, state.nextCursor, state.isPaginating) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                val nearTheEnd = lastVisible != null && lastVisible >= state.items.lastIndex - 1
                if (nearTheEnd && state.nextCursor != null && !state.isPaginating) {
                    viewModel.load(scope)
                }
            }
    }

    MemeFlowBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text("MemeFlow")
                        Text(
                            text = if (session.isAuthorized) "Accessible feed" else "Public feed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search")
                    }
                    if (!session.isAuthorized) {
                        IconButton(onClick = onRequireAuth) {
                            Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = "Login")
                        }
                    }
                }
            )

            when {
                state.isLoading && state.items.isEmpty() -> LoadingState()
                state.error != null && state.items.isEmpty() -> FullScreenError(state.error ?: "")
                state.items.isEmpty() -> EmptyState(
                    title = "Пока пусто",
                    description = "В моковом фиде сейчас нет мемов, сори"
                )
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            if (!session.isAuthorized) {
                                Button(onClick = onRequireAuth, modifier = Modifier.fillMaxWidth()) {
                                    Text("Войти, чтобы видеть private/groups контент")
                                }
                            } else {
                                SectionTitle("Ваш фид", "Контент подмешивается по access model из спецификации.")
                            }
                        }
                        items(state.items, key = { it.id }) { meme ->
                            MemeCard(meme = meme, onClick = { onOpenMeme(meme.id) })
                        }
                        if (state.isPaginating) {
                            item { Text("Загружаем еще…", modifier = Modifier.padding(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}
