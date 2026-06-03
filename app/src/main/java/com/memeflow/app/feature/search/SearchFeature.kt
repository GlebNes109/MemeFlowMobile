package com.memeflow.app.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.memeflow.app.LocalAppContainer
import com.memeflow.app.core.common.memeflowViewModel
import com.memeflow.app.core.common.readableMessage
import com.memeflow.app.core.model.SearchResults
import com.memeflow.app.core.ui.CollectionCard
import com.memeflow.app.core.ui.EmptyState
import com.memeflow.app.core.ui.MemeCard
import com.memeflow.app.core.ui.MemeFlowBackground
import com.memeflow.app.core.ui.SectionTitle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val results: SearchResults = SearchResults(emptyList(), emptyList(), emptyList())
)

class SearchViewModel(
    private val searchUseCase: suspend (String) -> SearchResults
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    fun updateQuery(value: String) {
        _state.update { it.copy(query = value) }
        searchJob?.cancel()
        if (value.trim().length < 2) {
            _state.update { it.copy(results = SearchResults(emptyList(), emptyList(), emptyList()), error = null, isLoading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { searchUseCase(value) }
                .onSuccess { _state.update { state -> state.copy(isLoading = false, results = it) } }
                .onFailure { error -> _state.update { it.copy(isLoading = false, error = error.readableMessage()) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenCollection: (String) -> Unit,
    onOpenMeme: (String) -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel = memeflowViewModel {
        SearchViewModel(searchUseCase = { query -> container.searchUseCase(query) })
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    MemeFlowBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(title = { Text("Поиск") })
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::updateQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Минимум 2 символа") }
                )
                if (state.query.length < 2) {
                    EmptyState("Начните поиск", "Ищем пользователей, подборки и мемы.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (state.results.users.isNotEmpty()) {
                            item { SectionTitle("Users") }
                            items(state.results.users, key = { it.id }) { user ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenUser(user.id) }
                                        .padding(vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text("@${user.login}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (state.results.collections.isNotEmpty()) {
                            item { SectionTitle("Collections") }
                            items(state.results.collections, key = { it.id }) { collection ->
                                CollectionCard(collection = collection, onClick = { onOpenCollection(collection.id) })
                            }
                        }
                        if (state.results.memes.isNotEmpty()) {
                            item { SectionTitle("Memes") }
                            items(state.results.memes, key = { it.id }) { meme ->
                                MemeCard(meme = meme, onClick = { onOpenMeme(meme.id) })
                            }
                        }
                        if (!state.isLoading &&
                            state.results.users.isEmpty() &&
                            state.results.collections.isEmpty() &&
                            state.results.memes.isEmpty()
                        ) {
                            item { EmptyState("Совпадений нет", "Попробуйте другой запрос.") }
                        }
                    }
                }
            }
        }
    }
}
