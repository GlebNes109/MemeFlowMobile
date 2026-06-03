package com.memeflow.app.data.repository

import com.memeflow.app.core.common.AppException
import com.memeflow.app.core.common.ErrorKind
import com.memeflow.app.core.model.AuthSession
import com.memeflow.app.core.model.CollectionDetails
import com.memeflow.app.core.model.CollectionDraft
import com.memeflow.app.core.model.FeedScope
import com.memeflow.app.core.model.Group
import com.memeflow.app.core.model.GroupInvitation
import com.memeflow.app.core.model.MediaAsset
import com.memeflow.app.core.model.Meme
import com.memeflow.app.core.model.MemeCollection
import com.memeflow.app.core.model.MemeDetails
import com.memeflow.app.core.model.MemeDraft
import com.memeflow.app.core.model.PagedResult
import com.memeflow.app.core.model.ProfileBundle
import com.memeflow.app.core.model.ReportDraft
import com.memeflow.app.core.model.SearchResults
import com.memeflow.app.core.model.SessionSnapshot
import com.memeflow.app.core.model.UserSummary
import com.memeflow.app.core.session.SessionStore
import com.memeflow.app.core.session.StoredSession
import com.memeflow.app.data.mock.FakeMemeFlowBackend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AuthRepository {
    val session: StateFlow<SessionSnapshot>
    suspend fun bootstrap()
    suspend fun continueAsGuest()
    suspend fun login(login: String, password: String)
    suspend fun register(login: String, password: String, displayName: String)
    suspend fun logout()
    suspend fun currentUserIdOrNull(): String?
}

interface FeedRepository {
    suspend fun loadFeed(scope: FeedScope, cursor: String?): PagedResult<Meme>
}

interface SearchRepository {
    suspend fun search(query: String): SearchResults
}

interface UserRepository {
    suspend fun loadMyProfile(): ProfileBundle
    suspend fun loadPublicProfile(userId: String): ProfileBundle
}

interface MediaRepository {
    suspend fun uploadImage(localUri: String): MediaAsset
    suspend fun importExternalMedia(url: String): MediaAsset
    suspend fun getMedia(mediaAssetId: String): MediaAsset
}

interface MemeRepository {
    suspend fun createMeme(draft: MemeDraft): Meme
    suspend fun updateMeme(memeId: String, draft: MemeDraft): Meme
    suspend fun loadMemeDetails(memeId: String): MemeDetails
}

interface CollectionRepository {
    suspend fun createCollection(draft: CollectionDraft): MemeCollection
    suspend fun updateCollection(collectionId: String, draft: CollectionDraft): MemeCollection
    suspend fun addMemes(collectionId: String, memeIds: List<String>): MemeCollection
    suspend fun removeMeme(collectionId: String, memeId: String): MemeCollection
    suspend fun loadCollectionDetails(collectionId: String): CollectionDetails
}

interface GroupRepository {
    suspend fun loadMyGroups(): List<Group>
    suspend fun loadInvitations(): List<GroupInvitation>
    suspend fun createGroup(name: String): Group
    suspend fun loadGroupDetails(groupId: String): Group
    suspend fun inviteUser(groupId: String, login: String): GroupInvitation
    suspend fun respondToInvitation(invitationId: String, accept: Boolean): GroupInvitation
}

interface ReportRepository {
    suspend fun createReport(reportDraft: ReportDraft)
}

class FakeAuthRepository(
    private val backend: FakeMemeFlowBackend,
    private val sessionStore: SessionStore
) : AuthRepository {
    private val _session = MutableStateFlow(SessionSnapshot())
    private val refreshMutex = Mutex()

    override val session: StateFlow<SessionSnapshot> = _session.asStateFlow()

    override suspend fun bootstrap() {
        val stored = sessionStore.read()
        val snapshot = stored?.toSnapshot()
        if (snapshot == null) {
            _session.value = SessionSnapshot(isBootstrapping = false)
            return
        }
        val user = backend.findUser(stored.userId)
        if (user == null) {
            sessionStore.clear()
            _session.value = SessionSnapshot(isBootstrapping = false)
            return
        }
        _session.value = snapshot.copy(
            isBootstrapping = false,
            currentUser = user.toSummary()
        )
        ensureFreshSession()
    }

    override suspend fun continueAsGuest() {
        sessionStore.clear()
        _session.value = SessionSnapshot(isBootstrapping = false)
    }

    override suspend fun login(login: String, password: String) {
        val authSession = backend.login(login, password)
        applySession(authSession)
    }

    override suspend fun register(login: String, password: String, displayName: String) {
        val authSession = backend.register(login, password, displayName)
        applySession(authSession)
    }

    override suspend fun logout() {
        sessionStore.clear()
        _session.value = SessionSnapshot(isBootstrapping = false)
    }

    override suspend fun currentUserIdOrNull(): String? {
        ensureFreshSession()
        return _session.value.currentUser?.id
    }

    private suspend fun applySession(authSession: AuthSession) {
        sessionStore.write(
            StoredSession(
                userId = authSession.user.id,
                login = authSession.user.login,
                displayName = authSession.user.displayName,
                avatarUrl = authSession.user.avatarUrl,
                accessToken = authSession.accessToken,
                refreshToken = authSession.refreshToken,
                accessTokenExpiresAt = authSession.accessTokenExpiresAt
            )
        )
        _session.value = SessionSnapshot(
            isBootstrapping = false,
            accessToken = authSession.accessToken,
            refreshToken = authSession.refreshToken,
            accessTokenExpiresAt = authSession.accessTokenExpiresAt,
            currentUser = authSession.user.toSummary()
        )
    }

    private suspend fun ensureFreshSession() {
        val snapshot = _session.value
        if (!snapshot.isAuthorized) return
        if (snapshot.accessTokenExpiresAt > System.currentTimeMillis()) return
        refreshMutex.withLock {
            val current = _session.value
            if (!current.isAuthorized) return
            if (current.accessTokenExpiresAt > System.currentTimeMillis()) return
            val refreshToken = current.refreshToken
                ?: throw AppException(ErrorKind.UNAUTHORIZED, "Сессия истекла.")
            runCatching {
                backend.refresh(refreshToken)
            }.onSuccess { refreshed ->
                applySession(refreshed)
            }.onFailure {
                logout()
            }
        }
    }

    private fun StoredSession.toSnapshot(): SessionSnapshot {
        val user = backend.findUser(userId)?.toSummary()
            ?: UserSummary(id = userId, login = login, displayName = displayName, avatarUrl = avatarUrl)
        return SessionSnapshot(
            isBootstrapping = true,
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresAt = accessTokenExpiresAt,
            currentUser = user
        )
    }

    private fun com.memeflow.app.core.model.UserProfile.toSummary(): UserSummary = UserSummary(
        id = id,
        login = login,
        displayName = displayName,
        avatarUrl = avatarUrl
    )
}

class FakeFeedRepository(
    private val backend: FakeMemeFlowBackend,
    private val authRepository: FakeAuthRepository
) : FeedRepository {
    override suspend fun loadFeed(scope: FeedScope, cursor: String?): PagedResult<Meme> {
        return backend.getFeed(authRepository.currentUserIdOrNull(), scope, cursor)
    }
}

class FakeSearchRepository(
    private val backend: FakeMemeFlowBackend,
    private val authRepository: FakeAuthRepository
) : SearchRepository {
    override suspend fun search(query: String): SearchResults {
        return backend.search(query, authRepository.currentUserIdOrNull())
    }
}

class FakeUserRepository(
    private val backend: FakeMemeFlowBackend,
    private val authRepository: FakeAuthRepository
) : UserRepository {
    override suspend fun loadMyProfile(): ProfileBundle {
        val userId = authRepository.currentUserIdOrNull()
            ?: throw AppException(ErrorKind.UNAUTHORIZED, "Войдите, чтобы открыть свой профиль.")
        return backend.getMyProfile(userId)
    }

    override suspend fun loadPublicProfile(userId: String): ProfileBundle {
        return backend.getPublicProfile(userId, authRepository.currentUserIdOrNull())
    }
}

class FakeMediaRepository(
    private val backend: FakeMemeFlowBackend,
    private val authRepository: FakeAuthRepository
) : MediaRepository {
    override suspend fun uploadImage(localUri: String): MediaAsset {
        requireUser()
        return backend.uploadImage(localUri)
    }

    override suspend fun importExternalMedia(url: String): MediaAsset {
        requireUser()
        return backend.importExternalMedia(url)
    }

    override suspend fun getMedia(mediaAssetId: String): MediaAsset = backend.getMedia(mediaAssetId)

    private suspend fun requireUser() {
        if (authRepository.currentUserIdOrNull() == null) {
            throw AppException(ErrorKind.UNAUTHORIZED, "Эта функция доступна после входа.")
        }
    }
}

class FakeMemeRepository(
    private val backend: FakeMemeFlowBackend,
    private val authRepository: FakeAuthRepository
) : MemeRepository {
    override suspend fun createMeme(draft: MemeDraft): Meme {
        val userId = authRepository.currentUserIdOrNull()
            ?: throw AppException(ErrorKind.UNAUTHORIZED, "Войдите, чтобы создать мем.")
        return backend.createMeme(userId, draft)
    }

    override suspend fun updateMeme(memeId: String, draft: MemeDraft): Meme {
        val userId = authRepository.currentUserIdOrNull()
            ?: throw AppException(ErrorKind.UNAUTHORIZED, "Войдите, чтобы редактировать мем.")
        return backend.updateMeme(userId, memeId, draft)
    }

    override suspend fun loadMemeDetails(memeId: String): MemeDetails {
        return backend.getMemeDetails(memeId, authRepository.currentUserIdOrNull())
    }
}

class FakeCollectionRepository(
    private val backend: FakeMemeFlowBackend,
    private val authRepository: FakeAuthRepository
) : CollectionRepository {
    override suspend fun createCollection(draft: CollectionDraft): MemeCollection {
        val userId = requireUser()
        return backend.createCollection(userId, draft)
    }

    override suspend fun updateCollection(collectionId: String, draft: CollectionDraft): MemeCollection {
        val userId = requireUser()
        return backend.updateCollection(userId, collectionId, draft)
    }

    override suspend fun addMemes(collectionId: String, memeIds: List<String>): MemeCollection {
        val userId = requireUser()
        return backend.addMemesToCollection(userId, collectionId, memeIds)
    }

    override suspend fun removeMeme(collectionId: String, memeId: String): MemeCollection {
        val userId = requireUser()
        return backend.removeMemeFromCollection(userId, collectionId, memeId)
    }

    override suspend fun loadCollectionDetails(collectionId: String): CollectionDetails {
        return backend.getCollectionDetails(collectionId, authRepository.currentUserIdOrNull())
    }

    private suspend fun requireUser(): String {
        return authRepository.currentUserIdOrNull()
            ?: throw AppException(ErrorKind.UNAUTHORIZED, "Войдите, чтобы управлять подборками.")
    }
}

class FakeGroupRepository(
    private val backend: FakeMemeFlowBackend,
    private val authRepository: FakeAuthRepository
) : GroupRepository {
    override suspend fun loadMyGroups(): List<Group> {
        return backend.getMyGroups(requireUser())
    }

    override suspend fun loadInvitations(): List<GroupInvitation> {
        return backend.getInvitations(requireUser())
    }

    override suspend fun createGroup(name: String): Group {
        return backend.createGroup(requireUser(), name)
    }

    override suspend fun loadGroupDetails(groupId: String): Group {
        return backend.getGroupDetails(groupId, requireUser())
    }

    override suspend fun inviteUser(groupId: String, login: String): GroupInvitation {
        return backend.inviteUserToGroup(requireUser(), groupId, login)
    }

    override suspend fun respondToInvitation(invitationId: String, accept: Boolean): GroupInvitation {
        return backend.respondToInvitation(requireUser(), invitationId, accept)
    }

    private suspend fun requireUser(): String {
        return authRepository.currentUserIdOrNull()
            ?: throw AppException(ErrorKind.UNAUTHORIZED, "Войдите, чтобы работать с группами.")
    }
}

class FakeReportRepository(
    private val backend: FakeMemeFlowBackend,
    private val authRepository: FakeAuthRepository
) : ReportRepository {
    override suspend fun createReport(reportDraft: ReportDraft) {
        val userId = authRepository.currentUserIdOrNull()
            ?: throw AppException(ErrorKind.UNAUTHORIZED, "Только авторизованный пользователь может пожаловаться.")
        backend.submitReport(userId, reportDraft)
    }
}
