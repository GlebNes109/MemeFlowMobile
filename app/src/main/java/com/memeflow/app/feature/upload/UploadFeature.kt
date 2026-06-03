package com.memeflow.app.feature.upload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.memeflow.app.core.model.MediaAsset
import com.memeflow.app.core.model.MediaStatus
import com.memeflow.app.core.model.SessionSnapshot
import com.memeflow.app.core.ui.EmptyState
import com.memeflow.app.core.ui.LoadingState
import com.memeflow.app.core.ui.MemeFlowBackground
import com.memeflow.app.core.ui.MemeMedia
import com.memeflow.app.core.ui.StatusBadge
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UploadUiState(
    val imageUrl: String = "",
    val shortsUrl: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val media: MediaAsset? = null
)

class UploadViewModel(
    private val uploadImage: suspend (String) -> MediaAsset,
    private val importMedia: suspend (String) -> MediaAsset,
    private val pollMedia: suspend (String) -> MediaAsset
) : ViewModel() {
    private val _state = MutableStateFlow(UploadUiState())
    val state: StateFlow<UploadUiState> = _state.asStateFlow()

    fun updateImageUrl(value: String) = _state.update { it.copy(imageUrl = value) }
    fun updateShortsUrl(value: String) = _state.update { it.copy(shortsUrl = value) }

    fun uploadLocal(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { uploadImage(uri.toString()) }
                .onSuccess { media -> _state.update { it.copy(isLoading = false, media = media) } }
                .onFailure { error -> _state.update { it.copy(isLoading = false, error = error.readableMessage()) } }
        }
    }

    fun importImage() = import(_state.value.imageUrl)
    fun importShorts() = import(_state.value.shortsUrl)

    private fun import(url: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, media = null) }
            runCatching { importMedia(url) }
                .onSuccess { asset ->
                    _state.update { it.copy(isLoading = false, media = asset) }
                    if (asset.status == MediaStatus.PROCESSING) {
                        poll(asset.id)
                    }
                }
                .onFailure { error -> _state.update { it.copy(isLoading = false, error = error.readableMessage()) } }
        }
    }

    private fun poll(mediaId: String) {
        viewModelScope.launch {
            var current = _state.value.media ?: return@launch
            while (current.status == MediaStatus.PROCESSING) {
                delay(800)
                current = pollMedia(mediaId)
                _state.update { it.copy(media = current) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    padding: PaddingValues,
    session: SessionSnapshot,
    onRequireAuth: () -> Unit,
    onMediaReady: (String) -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel = memeflowViewModel {
        UploadViewModel(
            uploadImage = { uri -> container.uploadImageUseCase(uri) },
            importMedia = { url -> container.importExternalMediaUseCase(url) },
            pollMedia = { id -> container.pollMediaStatusUseCase(id) }
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.uploadLocal(uri)
    }

    MemeFlowBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TopAppBar(title = { Text("Upload Flow") })
            if (!session.isAuthorized) {
                EmptyState("Нужна авторизация", "Загрузка и импорт доступны только после входа.")
                Button(
                    onClick = onRequireAuth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) { Text("Войти") }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(onClick = { launcher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Загрузить картинку файлом")
                    }
                    OutlinedTextField(
                        value = state.imageUrl,
                        onValueChange = viewModel::updateImageUrl,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("URL картинки") }
                    )
                    Button(onClick = viewModel::importImage, modifier = Modifier.fillMaxWidth()) {
                        Text("Импортировать картинку")
                    }
                    OutlinedTextField(
                        value = state.shortsUrl,
                        onValueChange = viewModel::updateShortsUrl,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("YouTube Shorts URL") }
                    )
                    Button(onClick = viewModel::importShorts, modifier = Modifier.fillMaxWidth()) {
                        Text("Импортировать Shorts")
                    }
                    if (state.isLoading) {
                        LoadingState()
                    }
                    state.error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
                    state.media?.let { media ->
                        MemeMedia(
                            mediaUrl = media.thumbnailUrl ?: media.storageUrl ?: media.originalUrl,
                            kind = media.kind
                        )
                        StatusBadge(media.status)
                        if (media.status == MediaStatus.READY) {
                            Button(onClick = { onMediaReady(media.id) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Продолжить к форме мема")
                            }
                        }
                    }
                }
            }
        }
    }
}
