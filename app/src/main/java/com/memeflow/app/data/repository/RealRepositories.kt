package com.memeflow.app.data.repository

import android.content.Context
import android.net.Uri
import com.memeflow.app.core.common.AppException
import com.memeflow.app.core.common.ErrorKind
import com.memeflow.app.core.model.*
import com.memeflow.app.core.network.MemeFlowApi
import com.memeflow.app.core.network.TokenProvider
import com.memeflow.app.core.network.dto.*
import com.memeflow.app.core.network.mapHttpError
import com.memeflow.app.core.session.SessionStore
import com.memeflow.app.core.session.StoredSession
import com.memeflow.app.data.mapper.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class RealAuthRepository(
    private val api: MemeFlowApi,
    private val sessionStore: SessionStore,
    private val tokenProvider: TokenProvider
) : AuthRepository {
    private val _session = MutableStateFlow(SessionSnapshot())
    private val refreshMutex = Mutex()

    override val session: StateFlow<SessionSnapshot> = _session.asStateFlow()

    override suspend fun bootstrap() {
        val stored = sessionStore.read()
        if (stored == null) {
            _session.value = SessionSnapshot(isBootstrapping = false)
            return
        }
        tokenProvider.accessToken = stored.accessToken
        _session.value = SessionSnapshot(
            isBootstrapping = false,
            accessToken = stored.accessToken,
            refreshToken = stored.refreshToken,
            accessTokenExpiresAt = stored.accessTokenExpiresAt,
            currentUser = UserSummary(
                id = stored.userId,
                login = stored.login,
                displayName = stored.displayName,
                avatarUrl = stored.avatarUrl
            )
        )
        ensureFreshSession()
    }

    override suspend fun continueAsGuest() {
        tokenProvider.accessToken = null
        sessionStore.clear()
        _session.value = SessionSnapshot(isBootstrapping = false)
    }

    override suspend fun login(login: String, password: String) {
        val response = api.login(LoginRequestDto(login, password))
        applySession(response)
    }

    override suspend fun register(login: String, password: String, displayName: String) {
        val response = api.register(RegisterRequestDto(login, password, displayName))
        applySession(response)
    }

    override suspend fun logout() {
        tokenProvider.accessToken = null
        sessionStore.clear()
        _session.value = SessionSnapshot(isBootstrapping = false)
    }

    override suspend fun currentUserIdOrNull(): String? {
        ensureFreshSession()
        return _session.value.currentUser?.id
    }

    private suspend fun applySession(response: AuthSessionDto) {
        val expiresAt = System.currentTimeMillis() + (response.tokens.expiresIn * 1000L)
        tokenProvider.accessToken = response.tokens.accessToken
        sessionStore.write(
            StoredSession(
                userId = response.user.id,
                login = response.user.login,
                displayName = response.user.displayName,
                avatarUrl = response.user.avatarUrl,
                accessToken = response.tokens.accessToken,
                refreshToken = response.tokens.refreshToken,
                accessTokenExpiresAt = expiresAt
            )
        )
        _session.value = SessionSnapshot(
            isBootstrapping = false,
            accessToken = response.tokens.accessToken,
            refreshToken = response.tokens.refreshToken,
            accessTokenExpiresAt = expiresAt,
            currentUser = response.user.toDomain()
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
                api.refresh(RefreshTokenRequestDto(refreshToken))
            }.onSuccess { refreshed ->
                applySession(refreshed)
            }.onFailure {
                logout()
            }
        }
    }

    private suspend fun requireToken() {
        ensureFreshSession()
        if (!_session.value.isAuthorized) {
            throw AppException(ErrorKind.UNAUTHORIZED, "Требуется авторизация.")
        }
    }
}

class RealFeedRepository(
    private val api: MemeFlowApi,
    private val authRepository: RealAuthRepository
) : FeedRepository {
    override suspend fun loadFeed(scope: FeedScope, cursor: String?): PagedResult<Meme> {
        return try {
            val scopeStr = when (scope) {
                FeedScope.PUBLIC -> "public"
                FeedScope.ACCESSIBLE -> "accessible"
            }
            val response = api.getFeed(scope = scopeStr, cursor = cursor)
            PagedResult(
                items = response.items.map { it.toDomain() },
                nextCursor = response.nextCursor
            )
        } catch (e: Exception) {
            throw mapHttpError(e)
        }
    }
}

class RealSearchRepository(
    private val api: MemeFlowApi,
    private val authRepository: RealAuthRepository
) : SearchRepository {
    override suspend fun search(query: String): SearchResults {
        return try {
            val response = api.search(query = query)
            SearchResults(
                users = response.users.map { it.toUserProfile() },
                collections = response.collections.map { it.toDomain() },
                memes = response.memes.map { it.toDomain() }
            )
        } catch (e: Exception) {
            throw mapHttpError(e)
        }
    }
}

class RealUserRepository(
    private val api: MemeFlowApi,
    private val authRepository: RealAuthRepository
) : UserRepository {
    override suspend fun loadMyProfile(): ProfileBundle {
        return try {
            val profile = api.getMyProfile()
            val memesResponse = api.getMyMemes()
            val collectionsResponse = api.getMyCollections()
            ProfileBundle(
                profile = UserProfile(
                    id = profile.id,
                    login = profile.login,
                    displayName = profile.displayName,
                    avatarUrl = profile.avatarUrl,
                    bio = profile.bio ?: "",
                    createdAt = 0L
                ),
                memes = memesResponse.items.map { it.toDomain() },
                collections = collectionsResponse.items.map { it.toDomain() },
                groups = profile.groups.map { it.toDomain() }
            )
        } catch (e: Exception) {
            throw mapHttpError(e)
        }
    }

    override suspend fun loadPublicProfile(userId: String): ProfileBundle {
        return try {
            val profile = api.getPublicProfile(userId)
            val memesResponse = api.getUserPublicMemes(userId)
            val collectionsResponse = api.getUserPublicCollections(userId)
            ProfileBundle(
                profile = profile.toDomain(),
                memes = memesResponse.items.map { it.toDomain() },
                collections = collectionsResponse.items.map { it.toDomain() },
                groups = emptyList()
            )
        } catch (e: Exception) {
            throw mapHttpError(e)
        }
    }
}

class RealMediaRepository(
    private val api: MemeFlowApi,
    private val authRepository: RealAuthRepository,
    private val context: Context
) : MediaRepository {
    override suspend fun uploadImage(localUri: String): MediaAsset {
        return try {
            val uri = Uri.parse(localUri)
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw AppException(ErrorKind.VALIDATION, "Не удалось открыть файл.")
            val bytes = inputStream.readBytes()
            inputStream.close()
            val fileName = uri.lastPathSegment ?: "upload.jpg"
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", fileName, requestBody)
            val response = api.uploadImage(part)
            response.toDomain()
        } catch (e: AppException) {
            throw e
        } catch (e: Exception) {
            throw mapHttpError(e)
        }
    }

    override suspend fun importExternalMedia(url: String): MediaAsset {
        return try {
            val response = api.importExternalMedia(CreateExternalImportRequestDto(url))
            response.toDomain()
        } catch (e: Exception) {
            throw mapHttpError(e)
        }
    }

    override suspend fun getMedia(mediaAssetId: String): MediaAsset {
        return try {
            val response = api.getMedia(mediaAssetId)
            response.toDomain()
        } catch (e: Exception) {
            throw mapHttpError(e)
        }
    }
}

class RealMemeRepository(
    private val api: MemeFlowApi,
    private val authRepository: RealAuthRepository
) : MemeRepository {
    override suspend fun createMeme(draft: MemeDraft): Meme {
        return try {
            val response = api.createMeme(
                CreateMemeRequestDto(
                    mediaAssetId = draft.mediaAssetId,
                    caption = draft.caption.ifBlank { null },
                    tags = draft.tags.ifEmpty { null },
                    visibility = mapVisibilityApi(draft.visibility),
                    sharedGroupIds = draft.sharedGroupIds.ifEmpty { null }
                )
            )
            response.toMeme()
        } catch (e: Exception) {
            throw mapHttpError(e)
        }
    }

    override suspend fun updateMeme(memeId: String, draft: MemeDraft): Meme {
        return try {
            val response = api.updateMeme(
                memeId = memeId,
                request = UpdateMemeRequestDto(
                    caption = draft.caption.ifBlank { null },
                    tags = draft.tags.ifEmpty { null },
                    visibility = mapVisibilityApi(draft.visibility),
                    sharedGroupIds = draft.sharedGroupIds.ifEmpty { null }
                )
            )
            response.toMeme()
        } catch (e: Exception) {
            throw mapHttpError(e)
        }
    }

    override suspend fun loadMemeDetails(memeId: String): MemeDetails {
        return try {
            val response = api.getMeme(memeId)
            response.toDomain()
        } catch (e: Exception) {
            throw mapHttpError(e)
        }
    }
}

class RealCollectionRepository(
    private val api: MemeFlowApi,
    private val authRepository: RealAuthRepository
) : CollectionRepository {
    override suspend fun createCollection(draft: CollectionDraft): MemeCollection {
        return try {
            val response = api.createCollection(
                CreateCollectionRequestDto(
                    name = draft.name,
                    description = draft.description.ifBlank { null },
                    visibility = mapVisibilityApi(draft.visibility),
                    sharedGroupIds = draft.sharedGroupIds.ifEmpty { null },
                    memeIds = draft.memeIds.ifEmpty { null }
                )
            )
            response.toCollection()
        } catch (e: Exception) {
            throw mapHttpError(e)
        }
    }

    override suspend fun updateCollection(collectionId: String, draft: CollectionDraft): MemeCollection {
        return try {
            val response = api.updateCollection(
                collectionId = collectionId,
                request = UpdateCollectionRequestDto(
                    name = draft.name,
                    description = draft.description.ifBlank { null },
                    visibility = mapVisibilityApi(draft.visibility),
                    sharedGroupIds = draft.sharedGroupIds.ifEmpty { null }
                )
            )
            response.toCollection()
        } catch (e: Exception) {
            throw mapHttpError(e)
        }
    }

    override suspend fun addMemes(collectionId: String, memeIds: List<String>): MemeCollection {
        return try {
            val response = api.addCollectionItems(
                collectionId = collectionId,
                request = AddCollectionItemsRequestDto(memeIds = memeIds)
            )
            response.toCollection()
        } catch (e: Exception) {
            throw mapHttpError(e)
        }
    }

    override suspend fun removeMeme(collectionId: String, memeId: String): MemeCollection {
        return try {
            api.removeCollectionItem(collectionId, memeId)
            api.getCollection(collectionId).toCollection()
        } catch (e: Exception) {
            throw mapHttpError(e)
        }
    }

    override suspend fun loadCollectionDetails(collectionId: String): CollectionDetails {
        return try {
            val response = api.getCollection(collectionId)
            response.toDomain()
        } catch (e: Exception) {
            throw mapHttpError(e)
        }
    }
}

class RealGroupRepository(
    private val api: MemeFlowApi,
    private val authRepository: RealAuthRepository
) : GroupRepository {
    override suspend fun loadMyGroups(): List<Group> {
        return try {
            api.getMyGroups().items.map { it.toDomain() }
        } catch (e: Exception) {
            throw mapHttpError(e)
        }
    }

    override suspend fun loadInvitations(): List<GroupInvitation> {
        return try {
            api.getInvitations().items.map { it.toDomain() }
        } catch (e: Exception) {
            throw mapHttpError(e)
        }
    }

    override suspend fun createGroup(name: String): Group {
        return try {
            api.createGroup(CreateGroupRequestDto(name)).toDomain()
        } catch (e: Exception) {
            throw mapHttpError(e)
        }
    }

    override suspend fun loadGroupDetails(groupId: String): Group {
        return try {
            val details = api.getGroup(groupId)
            val feed = api.getGroupFeed(groupId)
            details.toDomain().copy(accessibleMemes = feed.items.map { it.toDomain() })
        } catch (e: Exception) {
            throw mapHttpError(e)
        }
    }

    override suspend fun inviteUser(groupId: String, login: String): GroupInvitation {
        return try {
            val inviteeUserId = resolveUserId(login)
            val response = api.inviteToGroup(groupId, CreateGroupInviteRequestDto(inviteeUserId))
            response.toDomain()
        } catch (e: Exception) {
            throw mapHttpError(e)
        }
    }

    override suspend fun respondToInvitation(invitationId: String, accept: Boolean): GroupInvitation {
        return try {
            val response = if (accept) {
                api.acceptInvitation(invitationId)
            } else {
                api.declineInvitation(invitationId)
            }
            response.toDomain()
        } catch (e: Exception) {
            throw mapHttpError(e)
        }
    }

    private suspend fun resolveUserId(login: String): String {
        val results = api.search(query = login, limit = 1)
        return results.users.firstOrNull { it.login.equals(login, ignoreCase = true) }?.id
            ?: throw AppException(ErrorKind.NOT_FOUND, "Пользователь не найден.")
    }
}

class RealReportRepository(
    private val api: MemeFlowApi,
    private val authRepository: RealAuthRepository
) : ReportRepository {
    override suspend fun createReport(reportDraft: ReportDraft) {
        try {
            val targetType = when (reportDraft.targetType) {
                ReportTargetType.MEME -> "meme"
                ReportTargetType.COLLECTION -> "collection"
            }
            api.createReport(
                CreateReportRequestDto(
                    targetType = targetType,
                    targetId = reportDraft.targetId,
                    reason = reportDraft.reason,
                    comment = reportDraft.comment.ifBlank { null }
                )
            )
        } catch (e: Exception) {
            throw mapHttpError(e)
        }
    }
}
