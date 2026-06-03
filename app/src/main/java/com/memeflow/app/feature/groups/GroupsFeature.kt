package com.memeflow.app.feature.groups

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
import com.memeflow.app.core.model.Group
import com.memeflow.app.core.model.GroupInvitation
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

data class GroupsUiState(
    val isLoading: Boolean = true,
    val groups: List<Group> = emptyList(),
    val invitations: List<GroupInvitation> = emptyList(),
    val newGroupName: String = "",
    val error: String? = null
)

class GroupsViewModel(
    private val loadGroups: suspend () -> List<Group>,
    private val loadInvitations: suspend () -> List<GroupInvitation>,
    private val createGroup: suspend (String) -> Group
) : ViewModel() {
    private val _state = MutableStateFlow(GroupsUiState())
    val state: StateFlow<GroupsUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            runCatching {
                GroupsUiState(
                    isLoading = false,
                    groups = loadGroups(),
                    invitations = loadInvitations()
                )
            }.onSuccess { _state.value = it }
                .onFailure { error -> _state.update { it.copy(isLoading = false, error = error.readableMessage()) } }
        }
    }

    fun updateNewGroupName(value: String) = _state.update { it.copy(newGroupName = value) }

    fun createGroup() {
        val name = _state.value.newGroupName
        viewModelScope.launch {
            runCatching { createGroup(name) }
                .onSuccess {
                    load()
                    _state.update { state -> state.copy(newGroupName = "") }
                }
                .onFailure { error -> _state.update { it.copy(error = error.readableMessage()) } }
        }
    }
}

class GroupDetailsViewModel(
    private val loadGroupDetails: suspend (String) -> Group,
    private val inviteUser: suspend (String, String) -> GroupInvitation
) : ViewModel() {
    private val _group = MutableStateFlow<Pair<Boolean, Group?>>(true to null)
    val group: StateFlow<Pair<Boolean, Group?>> = _group.asStateFlow()
    private val _inviteLogin = MutableStateFlow("")
    val inviteLogin: StateFlow<String> = _inviteLogin.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load(groupId: String) {
        viewModelScope.launch {
            runCatching { loadGroupDetails(groupId) }
                .onSuccess { _group.value = false to it }
                .onFailure { _error.value = it.readableMessage(); _group.value = false to null }
        }
    }

    fun updateInviteLogin(value: String) {
        _inviteLogin.value = value
    }

    fun invite(groupId: String) {
        viewModelScope.launch {
            runCatching { inviteUser(groupId, _inviteLogin.value) }
                .onSuccess {
                    _inviteLogin.value = ""
                    load(groupId)
                }
                .onFailure { _error.value = it.readableMessage() }
        }
    }
}

class InvitationsViewModel(
    private val loadInvitations: suspend () -> List<GroupInvitation>,
    private val respond: suspend (String, Boolean) -> GroupInvitation
) : ViewModel() {
    private val _state = MutableStateFlow<Pair<Boolean, List<GroupInvitation>>>(true to emptyList())
    val state: StateFlow<Pair<Boolean, List<GroupInvitation>>> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            runCatching { loadInvitations() }
                .onSuccess { _state.value = false to it }
        }
    }

    fun respond(invitationId: String, accept: Boolean) {
        viewModelScope.launch {
            runCatching { respond(invitationId, accept) }.onSuccess { load() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onBack: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onInvitations: () -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel = memeflowViewModel {
        GroupsViewModel(
            loadGroups = { container.loadMyGroupsUseCase() },
            loadInvitations = { container.loadInvitationsUseCase() },
            createGroup = { name -> container.createGroupUseCase(name) }
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    MemeFlowBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(title = { Text("Groups") })
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
                                value = state.newGroupName,
                                onValueChange = viewModel::updateNewGroupName,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Новая группа") }
                            )
                        }
                        item {
                            Button(onClick = viewModel::createGroup, modifier = Modifier.fillMaxWidth()) {
                                Text("Создать группу")
                            }
                        }
                        item {
                            Button(onClick = onInvitations, modifier = Modifier.fillMaxWidth()) {
                                Text("Инвайты")
                            }
                        }
                        item { SectionTitle("Мои группы") }
                        if (state.groups.isEmpty()) {
                            item { EmptyState("Групп пока нет", "Создайте первую группу для shared access.") }
                        } else {
                            items(state.groups, key = { it.id }) { group ->
                                Button(onClick = { onOpenGroup(group.id) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("${group.name} · ${group.members.size} участников")
                                }
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
fun GroupDetailsScreen(
    groupId: String,
    onBack: () -> Unit,
    onOpenMeme: (String) -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel = memeflowViewModel(key = groupId) {
        GroupDetailsViewModel(
            loadGroupDetails = { id -> container.loadGroupDetailsUseCase(id) },
            inviteUser = { id, login -> container.inviteUserToGroupUseCase(id, login) }
        )
    }
    val group by viewModel.group.collectAsStateWithLifecycle()
    val inviteLogin by viewModel.inviteLogin.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(groupId) { viewModel.load(groupId) }

    MemeFlowBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(title = { Text("Group Details") })
            when {
                group.first -> LoadingState()
                error != null -> FullScreenError(error ?: "")
                group.second == null -> EmptyState("Группа недоступна", "Проверьте членство.")
                else -> {
                    val data = group.second ?: return@Column
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { SectionTitle(data.name, "${data.members.size} участников") }
                        item {
                            OutlinedTextField(
                                value = inviteLogin,
                                onValueChange = viewModel::updateInviteLogin,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Login для приглашения") }
                            )
                        }
                        item {
                            Button(onClick = { viewModel.invite(groupId) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Пригласить в группу")
                            }
                        }
                        item { SectionTitle("Участники") }
                        items(data.members, key = { it.user.id }) { member ->
                            Text("${member.user.displayName} · ${member.role.name.lowercase()}")
                        }
                        item { SectionTitle("Group feed") }
                        items(data.accessibleMemes, key = { it.id }) { meme ->
                            MemeCard(meme = meme, onClick = { onOpenMeme(meme.id) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvitationsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = memeflowViewModel {
        InvitationsViewModel(
            loadInvitations = { container.loadInvitationsUseCase() },
            respond = { id, accept -> container.respondToInvitationUseCase(id, accept) }
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    MemeFlowBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(title = { Text("Invitations") })
            if (state.first) {
                LoadingState()
            } else if (state.second.isEmpty()) {
                EmptyState("Инвайтов нет", "Когда вас пригласят в группу, они появятся здесь.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.second, key = { it.id }) { invite ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${invite.groupName} · от @${invite.inviter.login}", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { viewModel.respond(invite.id, true) }, modifier = Modifier.weight(1f)) {
                                    Text("Принять")
                                }
                                Button(onClick = { viewModel.respond(invite.id, false) }, modifier = Modifier.weight(1f)) {
                                    Text("Отклонить")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
