package com.memeflow.app.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.memeflow.app.LocalAppContainer
import com.memeflow.app.core.common.FormStatus
import com.memeflow.app.core.common.memeflowViewModel
import com.memeflow.app.core.common.readableMessage
import com.memeflow.app.core.ui.MemeFlowBackground
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { LOGIN, REGISTER }

data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val login: String = "",
    val displayName: String = "",
    val password: String = "",
    val status: FormStatus = FormStatus.IDLE,
    val error: String? = null,
    val isAuthorized: Boolean = false
)

class AuthViewModel(
    private val loginUseCase: suspend (String, String) -> Unit,
    private val registerUseCase: suspend (String, String, String) -> Unit,
    private val continueAsGuestUseCase: suspend () -> Unit
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun setMode(mode: AuthMode) {
        _state.update { it.copy(mode = mode, error = null, status = FormStatus.IDLE) }
    }

    fun updateLogin(value: String) = _state.update { it.copy(login = value) }
    fun updateDisplayName(value: String) = _state.update { it.copy(displayName = value) }
    fun updatePassword(value: String) = _state.update { it.copy(password = value) }

    fun submit() {
        val current = _state.value
        if (current.login.isBlank() || current.password.isBlank() || (current.mode == AuthMode.REGISTER && current.displayName.isBlank())) {
            _state.update { it.copy(status = FormStatus.VALIDATION_ERROR, error = "Заполните обязательные поля.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(status = FormStatus.SUBMITTING, error = null) }
            runCatching {
                when (current.mode) {
                    AuthMode.LOGIN -> loginUseCase(current.login, current.password)
                    AuthMode.REGISTER -> registerUseCase(current.login, current.password, current.displayName)
                }
            }.onSuccess {
                _state.update { it.copy(status = FormStatus.SUCCESS, isAuthorized = true) }
            }.onFailure { error ->
                _state.update { it.copy(status = FormStatus.ERROR, error = error.readableMessage()) }
            }
        }
    }

    fun continueAsGuest(onDone: () -> Unit) {
        viewModelScope.launch {
            continueAsGuestUseCase()
            onDone()
        }
    }
}

@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
    onContinueAsGuest: () -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel = memeflowViewModel {
        AuthViewModel(
            loginUseCase = { login, password -> container.loginUseCase(login, password) },
            registerUseCase = { login, password, displayName -> container.registerUseCase(login, password, displayName) },
            continueAsGuestUseCase = { container.continueAsGuestUseCase() }
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isAuthorized) {
        if (state.isAuthorized) onAuthenticated()
    }

    MemeFlowBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "MemeFlow", style = MaterialTheme.typography.displaySmall)
            Text(
                text = "Делиться мемами так же просто, как смеяться над ними\n",
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TabRow(selectedTabIndex = state.mode.ordinal) {
                        Tab(selected = state.mode == AuthMode.LOGIN, onClick = { viewModel.setMode(AuthMode.LOGIN) }, text = { Text("Вход") })
                        Tab(selected = state.mode == AuthMode.REGISTER, onClick = { viewModel.setMode(AuthMode.REGISTER) }, text = { Text("Регистрация") })
                    }
                    OutlinedTextField(
                        value = state.login,
                        onValueChange = viewModel::updateLogin,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Login") }
                    )
                    if (state.mode == AuthMode.REGISTER) {
                        OutlinedTextField(
                            value = state.displayName,
                            onValueChange = viewModel::updateDisplayName,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Display name") }
                        )
                    }
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = viewModel::updatePassword,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    state.error?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = viewModel::submit,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.status != FormStatus.SUBMITTING
                    ) {
                        Text(if (state.mode == AuthMode.LOGIN) "Войти" else "Создать аккаунт")
                    }
                    TextButton(
                        onClick = { viewModel.continueAsGuest(onContinueAsGuest) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Продолжить как гость")
                    }
                }
            }
        }
    }
}
