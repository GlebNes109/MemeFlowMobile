package com.memeflow.app.core.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memeflow.app.core.model.AccessLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ErrorKind {
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    VALIDATION,
    NETWORK,
    UNKNOWN
}

enum class FormStatus {
    IDLE,
    SUBMITTING,
    SUCCESS,
    VALIDATION_ERROR,
    ERROR
}

class AppException(
    val kind: ErrorKind,
    override val message: String
) : IllegalStateException(message)

fun Throwable.readableMessage(): String = when (this) {
    is AppException -> message
    else -> "Что-то пошло не так. Попробуйте еще раз."
}

fun AccessLevel.uiLabel(): String = when (this) {
    AccessLevel.PRIVATE -> "Приватный"
    AccessLevel.GROUPS -> "Ограниченный"
    AccessLevel.PUBLIC -> "Публичный"
}

fun AccessLevel.rank(): Int = when (this) {
    AccessLevel.PRIVATE -> 0
    AccessLevel.GROUPS -> 1
    AccessLevel.PUBLIC -> 2
}

fun computeEffectiveAccess(
    directVisibility: AccessLevel,
    directGroupIds: List<String>,
    collectionSources: List<Pair<AccessLevel, List<String>>>
): Pair<AccessLevel, List<String>> {
    val allSources = listOf(directVisibility to directGroupIds) + collectionSources
    return when {
        allSources.any { it.first == AccessLevel.PUBLIC } -> AccessLevel.PUBLIC to emptyList()
        allSources.any { it.first == AccessLevel.GROUPS } -> {
            val groupIds = allSources
                .filter { it.first == AccessLevel.GROUPS }
                .flatMap { it.second }
                .distinct()
            AccessLevel.GROUPS to groupIds
        }

        else -> AccessLevel.PRIVATE to emptyList()
    }
}

fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

@Composable
inline fun <reified VM : ViewModel> memeflowViewModel(
    key: String? = null,
    crossinline builder: () -> VM
): VM {
    val factory = remember {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = builder() as T
        }
    }
    return viewModel(key = key, factory = factory)
}
