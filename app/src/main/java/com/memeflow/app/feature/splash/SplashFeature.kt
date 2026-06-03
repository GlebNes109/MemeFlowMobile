package com.memeflow.app.feature.splash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.memeflow.app.LocalAppContainer
import com.memeflow.app.core.common.memeflowViewModel
import com.memeflow.app.core.model.SessionSnapshot
import com.memeflow.app.core.ui.LoadingState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SplashViewModel(
    private val bootstrapSession: suspend () -> Unit,
    observeSession: StateFlow<SessionSnapshot>
) : ViewModel() {
    val session = observeSession

    init {
        viewModelScope.launch {
            bootstrapSession()
        }
    }
}

@Composable
fun SplashScreen(
    onAuthorized: () -> Unit,
    onUnauthorized: () -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel = memeflowViewModel {
        SplashViewModel(
            bootstrapSession = { container.bootstrapSessionUseCase() },
            observeSession = container.observeSessionUseCase()
        )
    }
    val session by viewModel.session.collectAsStateWithLifecycle()

    LaunchedEffect(session.isBootstrapping, session.isAuthorized) {
        if (!session.isBootstrapping) {
            if (session.isAuthorized) onAuthorized() else onUnauthorized()
        }
    }

    LoadingState()
}
