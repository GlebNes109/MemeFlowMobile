package com.memeflow.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import com.memeflow.app.core.model.ProfileBundle
import com.memeflow.app.core.model.SessionSnapshot
import com.memeflow.app.core.ui.EmptyState
import com.memeflow.app.core.ui.LoadingState
import com.memeflow.app.core.ui.MemeFlowBackground
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val profile: ProfileBundle? = null
)

class SettingsViewModel(
    private val logout: suspend () -> Unit,
    private val loadProfile: suspend () -> ProfileBundle
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            runCatching { loadProfile() }.onSuccess { _state.value = SettingsUiState(profile = it) }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            logout()
            onLoggedOut()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    padding: PaddingValues,
    session: SessionSnapshot,
    onRequireAuth: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel = memeflowViewModel {
        SettingsViewModel(
            logout = { container.logoutUseCase() },
            loadProfile = { container.loadMyProfileUseCase() }
        )
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
            TopAppBar(title = { Text("Settings") })
            if (!session.isAuthorized) {
                EmptyState("Вы в гостевом режиме", "Для настроек профиля нужен аккаунт.")
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
                    Text("Профиль", style = MaterialTheme.typography.titleLarge)
                    state.profile?.let { profile ->
                        Text(profile.profile.displayName)
                        Text("@${profile.profile.login}")
                        Text(profile.profile.bio)
                    } ?: LoadingState()
                    Button(onClick = { viewModel.logout(onLoggedOut) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Logout")
                    }
                }
            }
        }
    }
}
