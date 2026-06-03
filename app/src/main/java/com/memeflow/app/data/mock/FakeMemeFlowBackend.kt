package com.memeflow.app.data.mock

import com.memeflow.app.core.common.AppException
import com.memeflow.app.core.common.ErrorKind
import com.memeflow.app.core.common.computeEffectiveAccess
import com.memeflow.app.core.common.rank
import com.memeflow.app.core.model.AccessLevel
import com.memeflow.app.core.model.AuthSession
import com.memeflow.app.core.model.CollectionDetails
import com.memeflow.app.core.model.CollectionDraft
import com.memeflow.app.core.model.CollectionAccessSource
import com.memeflow.app.core.model.FeedScope
import com.memeflow.app.core.model.Group
import com.memeflow.app.core.model.GroupInvitation
import com.memeflow.app.core.model.GroupMember
import com.memeflow.app.core.model.GroupRole
import com.memeflow.app.core.model.InvitationStatus
import com.memeflow.app.core.model.MediaAsset
import com.memeflow.app.core.model.MediaKind
import com.memeflow.app.core.model.MediaProvider
import com.memeflow.app.core.model.MediaSourceType
import com.memeflow.app.core.model.MediaStatus
import com.memeflow.app.core.model.Meme
import com.memeflow.app.core.model.MemeCollection
import com.memeflow.app.core.model.MemeDetails
import com.memeflow.app.core.model.MemeDraft
import com.memeflow.app.core.model.ModerationStatus
import com.memeflow.app.core.model.PagedResult
import com.memeflow.app.core.model.ProfileBundle
import com.memeflow.app.core.model.ReportDraft
import com.memeflow.app.core.model.SearchResults
import com.memeflow.app.core.model.UserProfile
import com.memeflow.app.core.model.UserSummary
import java.util.UUID
import kotlinx.coroutines.delay

class FakeMemeFlowBackend {
    private data class FakeUser(
        val profile: UserProfile,
        var password: String
    )

    private val users = linkedMapOf<String, FakeUser>()
    private val memes = linkedMapOf<String, Meme>()
    private val collections = linkedMapOf<String, MemeCollection>()
    private val groups = linkedMapOf<String, Group>()
    private val invitations = linkedMapOf<String, GroupInvitation>()
    private val mediaAssets = linkedMapOf<String, MediaAsset>()
    private val processingTicks = mutableMapOf<String, Int>()
    private val reports = mutableListOf<ReportDraft>()
    private val pageSize = 5

    init {
        seed()
    }

    suspend fun login(login: String, password: String): AuthSession {
        delay(250)
        val user = users.values.firstOrNull { it.profile.login.equals(login.trim(), ignoreCase = true) }
            ?: throw AppException(ErrorKind.UNAUTHORIZED, "Неверный логин или пароль.")
        if (user.password != password) {
            throw AppException(ErrorKind.UNAUTHORIZED, "Неверный логин или пароль.")
        }
        return createSession(user.profile)
    }

    suspend fun register(login: String, password: String, displayName: String): AuthSession {
        delay(350)
        if (login.length < 3) {
            throw AppException(ErrorKind.VALIDATION, "Логин должен быть не короче 3 символов.")
        }
        if (password.length < 6) {
            throw AppException(ErrorKind.VALIDATION, "Пароль должен быть не короче 6 символов.")
        }
        if (displayName.isBlank()) {
            throw AppException(ErrorKind.VALIDATION, "Добавьте отображаемое имя.")
        }
        val normalized = login.trim().lowercase()
        if (users.values.any { it.profile.login.lowercase() == normalized }) {
            throw AppException(ErrorKind.VALIDATION, "Такой логин уже занят.")
        }

        val userId = UUID.randomUUID().toString()
        val profile = UserProfile(
            id = userId,
            login = normalized,
            displayName = displayName.trim(),
            avatarUrl = "https://picsum.photos/seed/$normalized/120/120",
            bio = "Коллекционирую мемы с хорошим таймингом.",
            createdAt = System.currentTimeMillis()
        )
        users[userId] = FakeUser(profile, password)
        return createSession(profile)
    }

    suspend fun refresh(refreshToken: String): AuthSession {
        delay(200)
        val userId = refreshToken.substringAfter("refresh-", missingDelimiterValue = "")
        val user = users[userId]?.profile
            ?: throw AppException(ErrorKind.UNAUTHORIZED, "Сессия истекла. Войдите снова.")
        return createSession(user)
    }

    fun findUser(userId: String): UserProfile? = users[userId]?.profile

    suspend fun getFeed(viewerId: String?, scope: FeedScope, cursor: String?): PagedResult<Meme> {
        delay(300)
        val items = memes.values
            .sortedByDescending { it.createdAt }
            .filter { meme ->
                when (scope) {
                    FeedScope.PUBLIC -> meme.effectiveVisibility == AccessLevel.PUBLIC
                    FeedScope.ACCESSIBLE -> canAccessMeme(meme, viewerId)
                }
            }
        return page(items, cursor)
    }

    suspend fun search(query: String, viewerId: String?): SearchResults {
        delay(220)
        val q = query.trim().lowercase()
        val matchedUsers = users.values
            .map { it.profile }
            .filter {
                it.login.lowercase().contains(q) ||
                    it.displayName.lowercase().contains(q) ||
                    it.bio.lowercase().contains(q)
            }
            .sortedBy { it.displayName }
        val matchedCollections = collections.values
            .filter { canAccessCollection(it, viewerId) }
            .filter {
                it.name.lowercase().contains(q) || it.description.lowercase().contains(q)
            }
            .sortedByDescending { it.createdAt }
        val matchedMemes = memes.values
            .filter { canAccessMeme(it, viewerId) }
            .filter {
                it.caption.lowercase().contains(q) || it.tags.any { tag -> tag.lowercase().contains(q) }
            }
            .sortedByDescending { it.createdAt }
        return SearchResults(
            users = matchedUsers,
            collections = matchedCollections,
            memes = matchedMemes
        )
    }

    suspend fun getPublicProfile(userId: String, viewerId: String?): ProfileBundle {
        delay(260)
        val profile = users[userId]?.profile ?: throw AppException(ErrorKind.NOT_FOUND, "Профиль не найден.")
        return ProfileBundle(
            profile = profile,
            memes = getUserMemes(userId, viewerId, ownProfile = false),
            collections = getUserCollections(userId, viewerId, ownProfile = false),
            groups = emptyList()
        )
    }

    suspend fun getMyProfile(userId: String): ProfileBundle {
        delay(260)
        val profile = users[userId]?.profile ?: throw AppException(ErrorKind.NOT_FOUND, "Профиль не найден.")
        return ProfileBundle(
            profile = profile,
            memes = getUserMemes(userId, userId, ownProfile = true),
            collections = getUserCollections(userId, userId, ownProfile = true),
            groups = getMyGroups(userId)
        )
    }

    suspend fun uploadImage(localUri: String): MediaAsset {
        delay(450)
        val mediaId = UUID.randomUUID().toString()
        return MediaAsset(
            id = mediaId,
            kind = MediaKind.IMAGE,
            sourceType = MediaSourceType.IMAGE_UPLOAD,
            provider = MediaProvider.UPLOAD,
            status = MediaStatus.READY,
            originalUrl = localUri,
            storageUrl = localUri,
            thumbnailUrl = localUri,
            title = "Локальная картинка"
        ).also { mediaAssets[it.id] = it }
    }

    suspend fun importExternalMedia(url: String): MediaAsset {
        delay(380)
        val trimmed = url.trim()
        if (!trimmed.startsWith("http")) {
            throw AppException(ErrorKind.VALIDATION, "Добавьте корректный URL.")
        }
        val mediaId = UUID.randomUUID().toString()
        val isYoutube = trimmed.contains("youtu", ignoreCase = true)
        val failed = trimmed.contains("fail", ignoreCase = true)
        val blocked = trimmed.contains("blocked", ignoreCase = true)
        val asset = MediaAsset(
            id = mediaId,
            kind = if (isYoutube) MediaKind.EXTERNAL_VIDEO else MediaKind.IMAGE,
            sourceType = if (isYoutube) MediaSourceType.YOUTUBE_SHORT_URL else MediaSourceType.IMAGE_URL,
            provider = if (isYoutube) MediaProvider.YOUTUBE else MediaProvider.WEB,
            status = MediaStatus.PROCESSING,
            originalUrl = trimmed,
            storageUrl = null,
            thumbnailUrl = if (isYoutube) youtubeThumbnail(trimmed) else trimmed,
            title = if (isYoutube) "Short по ссылке" else "Импорт картинки"
        )
        mediaAssets[mediaId] = asset
        processingTicks[mediaId] = when {
            failed || blocked -> 1
            else -> 2
        }
        return asset
    }

    suspend fun getMedia(mediaAssetId: String): MediaAsset {
        delay(300)
        val asset = mediaAssets[mediaAssetId] ?: throw AppException(ErrorKind.NOT_FOUND, "Media asset не найден.")
        val ticks = processingTicks[mediaAssetId] ?: return asset
        if (ticks > 1) {
            processingTicks[mediaAssetId] = ticks - 1
            return asset
        }
        processingTicks.remove(mediaAssetId)
        val finalized = when {
            asset.originalUrl.contains("blocked", ignoreCase = true) -> asset.copy(status = MediaStatus.BLOCKED)
            asset.originalUrl.contains("fail", ignoreCase = true) -> asset.copy(status = MediaStatus.FAILED)
            asset.kind == MediaKind.EXTERNAL_VIDEO -> asset.copy(
                status = MediaStatus.READY,
                storageUrl = asset.originalUrl,
                thumbnailUrl = asset.thumbnailUrl,
                title = "YouTube Short"
            )
            else -> asset.copy(
                status = MediaStatus.READY,
                storageUrl = asset.originalUrl,
                thumbnailUrl = asset.originalUrl
            )
        }
        mediaAssets[mediaAssetId] = finalized
        return finalized
    }

    suspend fun createMeme(authorId: String, draft: MemeDraft): Meme {
        delay(320)
        val author = userSummary(authorId)
        val media = mediaAssets[draft.mediaAssetId]
            ?: throw AppException(ErrorKind.NOT_FOUND, "Media asset не найден.")
        if (media.status != MediaStatus.READY) {
            throw AppException(ErrorKind.VALIDATION, "Сначала дождитесь готовности медиа.")
        }
        validateVisibility(draft.visibility, draft.sharedGroupIds)
        val memeId = UUID.randomUUID().toString()
        val meme = Meme(
            id = memeId,
            author = author,
            media = media,
            caption = draft.caption.trim(),
            tags = draft.tags.filter { it.isNotBlank() }.map { it.trim() }.distinct(),
            directVisibility = draft.visibility,
            directSharedGroupIds = draft.sharedGroupIds.distinct(),
            effectiveVisibility = draft.visibility,
            effectiveSharedGroupIds = draft.sharedGroupIds.distinct(),
            moderationStatus = ModerationStatus.APPROVED,
            createdAt = System.currentTimeMillis()
        )
        memes[memeId] = meme
        recomputeMemeAccess(memeId)
        return memes.getValue(memeId)
    }

    suspend fun updateMeme(authorId: String, memeId: String, draft: MemeDraft): Meme {
        delay(300)
        validateVisibility(draft.visibility, draft.sharedGroupIds)
        val current = memes[memeId] ?: throw AppException(ErrorKind.NOT_FOUND, "Мем не найден.")
        if (current.author.id != authorId) {
            throw AppException(ErrorKind.FORBIDDEN, "Редактировать можно только свои мемы.")
        }
        memes[memeId] = current.copy(
            caption = draft.caption.trim(),
            tags = draft.tags.filter { it.isNotBlank() }.map { it.trim() }.distinct(),
            directVisibility = draft.visibility,
            directSharedGroupIds = draft.sharedGroupIds.distinct()
        )
        recomputeMemeAccess(memeId)
        return memes.getValue(memeId)
    }

    suspend fun getMemeDetails(memeId: String, viewerId: String?): MemeDetails {
        delay(240)
        val meme = memes[memeId] ?: throw AppException(ErrorKind.NOT_FOUND, "Мем не найден.")
        if (!canAccessMeme(meme, viewerId)) {
            throw AppException(ErrorKind.FORBIDDEN, "Этот мем вам пока недоступен.")
        }
        val relatedCollections = collections.values
            .filter { it.itemIds.contains(memeId) }
            .filter { canAccessCollection(it, viewerId) || it.author.id == viewerId }
        val inheritedSources = relatedCollections
            .filter { it.visibility.rank() > meme.directVisibility.rank() }
            .map {
                CollectionAccessSource(
                    collectionId = it.id,
                    collectionName = it.name,
                    visibility = it.visibility,
                    sharedGroupIds = it.sharedGroupIds
                )
            }
        return MemeDetails(
            meme = meme,
            canEdit = meme.author.id == viewerId,
            collections = relatedCollections,
            inheritedAccessFromCollections = inheritedSources
        )
    }

    suspend fun createCollection(authorId: String, draft: CollectionDraft): MemeCollection {
        delay(320)
        validateVisibility(draft.visibility, draft.sharedGroupIds)
        val author = userSummary(authorId)
        val itemIds = draft.memeIds.distinct()
        ensureOwnership(authorId, itemIds)
        val collection = MemeCollection(
            id = UUID.randomUUID().toString(),
            name = draft.name.trim(),
            description = draft.description.trim(),
            author = author,
            visibility = draft.visibility,
            sharedGroupIds = draft.sharedGroupIds.distinct(),
            itemCount = itemIds.size,
            coverThumbnailUrl = itemIds.firstOrNull()?.let { memes[it]?.media?.thumbnailUrl ?: memes[it]?.media?.storageUrl },
            itemIds = itemIds,
            moderationStatus = ModerationStatus.APPROVED,
            createdAt = System.currentTimeMillis()
        )
        collections[collection.id] = collection
        itemIds.forEach(::recomputeMemeAccess)
        return collection
    }

    suspend fun updateCollection(authorId: String, collectionId: String, draft: CollectionDraft): MemeCollection {
        delay(320)
        validateVisibility(draft.visibility, draft.sharedGroupIds)
        val current = collections[collectionId] ?: throw AppException(ErrorKind.NOT_FOUND, "Подборка не найдена.")
        if (current.author.id != authorId) {
            throw AppException(ErrorKind.FORBIDDEN, "Редактировать можно только свои подборки.")
        }
        val itemIds = draft.memeIds.distinct()
        ensureOwnership(authorId, itemIds)
        val updated = current.copy(
            name = draft.name.trim(),
            description = draft.description.trim(),
            visibility = draft.visibility,
            sharedGroupIds = draft.sharedGroupIds.distinct(),
            itemIds = itemIds,
            itemCount = itemIds.size,
            coverThumbnailUrl = itemIds.firstOrNull()?.let { memes[it]?.media?.thumbnailUrl ?: memes[it]?.media?.storageUrl }
        )
        collections[collectionId] = updated
        (current.itemIds + itemIds).distinct().forEach(::recomputeMemeAccess)
        return updated
    }

    suspend fun addMemesToCollection(authorId: String, collectionId: String, memeIds: List<String>): MemeCollection {
        val current = collections[collectionId] ?: throw AppException(ErrorKind.NOT_FOUND, "Подборка не найдена.")
        return updateCollection(
            authorId = authorId,
            collectionId = collectionId,
            draft = CollectionDraft(
                name = current.name,
                description = current.description,
                visibility = current.visibility,
                sharedGroupIds = current.sharedGroupIds,
                memeIds = (current.itemIds + memeIds).distinct()
            )
        )
    }

    suspend fun removeMemeFromCollection(authorId: String, collectionId: String, memeId: String): MemeCollection {
        val current = collections[collectionId] ?: throw AppException(ErrorKind.NOT_FOUND, "Подборка не найдена.")
        return updateCollection(
            authorId = authorId,
            collectionId = collectionId,
            draft = CollectionDraft(
                name = current.name,
                description = current.description,
                visibility = current.visibility,
                sharedGroupIds = current.sharedGroupIds,
                memeIds = current.itemIds.filterNot { it == memeId }
            )
        )
    }

    suspend fun getCollectionDetails(collectionId: String, viewerId: String?): CollectionDetails {
        delay(240)
        val collection = collections[collectionId]
            ?: throw AppException(ErrorKind.NOT_FOUND, "Подборка не найдена.")
        if (!canAccessCollection(collection, viewerId)) {
            throw AppException(ErrorKind.FORBIDDEN, "Подборка вам недоступна.")
        }
        val items = collection.itemIds.mapNotNull { memes[it] }.filter { canAccessMeme(it, viewerId) }
        return CollectionDetails(
            collection = collection,
            items = items,
            canEdit = collection.author.id == viewerId
        )
    }

    suspend fun getMyGroups(userId: String): List<Group> {
        delay(220)
        return groups.values
            .filter { group -> group.members.any { it.user.id == userId } }
            .sortedBy { it.name }
    }

    suspend fun getInvitations(userId: String): List<GroupInvitation> {
        delay(200)
        return invitations.values
            .filter { it.invitee.id == userId && it.status == InvitationStatus.PENDING }
            .sortedByDescending { it.createdAt }
    }

    suspend fun createGroup(ownerId: String, name: String): Group {
        delay(260)
        if (name.trim().length < 3) {
            throw AppException(ErrorKind.VALIDATION, "Название группы должно быть не короче 3 символов.")
        }
        val owner = userSummary(ownerId)
        val group = Group(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            members = listOf(GroupMember(owner, GroupRole.OWNER)),
            currentUserRole = GroupRole.OWNER
        )
        groups[group.id] = group
        return group
    }

    suspend fun getGroupDetails(groupId: String, viewerId: String): Group {
        delay(250)
        val group = groups[groupId] ?: throw AppException(ErrorKind.NOT_FOUND, "Группа не найдена.")
        val membership = group.members.firstOrNull { it.user.id == viewerId }
            ?: throw AppException(ErrorKind.FORBIDDEN, "Откройте группу только после вступления.")
        val accessibleMemes = memes.values
            .filter {
                it.effectiveVisibility == AccessLevel.GROUPS &&
                    it.effectiveSharedGroupIds.contains(groupId)
            }
            .sortedByDescending { it.createdAt }
        return group.copy(
            currentUserRole = membership.role,
            accessibleMemes = accessibleMemes
        )
    }

    suspend fun inviteUserToGroup(ownerId: String, groupId: String, login: String): GroupInvitation {
        delay(260)
        val group = groups[groupId] ?: throw AppException(ErrorKind.NOT_FOUND, "Группа не найдена.")
        val ownerMembership = group.members.firstOrNull { it.user.id == ownerId }
            ?: throw AppException(ErrorKind.FORBIDDEN, "Вы не состоите в группе.")
        if (ownerMembership.role != GroupRole.OWNER) {
            throw AppException(ErrorKind.FORBIDDEN, "Приглашать может только владелец группы.")
        }
        val user = users.values.firstOrNull { it.profile.login.equals(login.trim(), ignoreCase = true) }?.profile
            ?: throw AppException(ErrorKind.NOT_FOUND, "Пользователь не найден.")
        if (group.members.any { it.user.id == user.id }) {
            throw AppException(ErrorKind.VALIDATION, "Пользователь уже в группе.")
        }
        val invitation = GroupInvitation(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            groupName = group.name,
            inviter = userSummary(ownerId),
            invitee = user.toSummary(),
            status = InvitationStatus.PENDING,
            createdAt = System.currentTimeMillis()
        )
        invitations[invitation.id] = invitation
        return invitation
    }

    suspend fun respondToInvitation(userId: String, invitationId: String, accept: Boolean): GroupInvitation {
        delay(240)
        val invitation = invitations[invitationId]
            ?: throw AppException(ErrorKind.NOT_FOUND, "Инвайт не найден.")
        if (invitation.invitee.id != userId) {
            throw AppException(ErrorKind.FORBIDDEN, "Нельзя отвечать на чужой инвайт.")
        }
        val updated = invitation.copy(status = if (accept) InvitationStatus.ACCEPTED else InvitationStatus.DECLINED)
        invitations[invitationId] = updated
        if (accept) {
            val group = groups[invitation.groupId] ?: throw AppException(ErrorKind.NOT_FOUND, "Группа не найдена.")
            if (group.members.none { it.user.id == userId }) {
                groups[group.id] = group.copy(
                    members = group.members + GroupMember(userSummary(userId), GroupRole.MEMBER)
                )
            }
        }
        return updated
    }

    suspend fun submitReport(userId: String, reportDraft: ReportDraft) {
        delay(220)
        if (reportDraft.reason.isBlank()) {
            throw AppException(ErrorKind.VALIDATION, "Укажите причину жалобы.")
        }
        userSummary(userId)
        reports += reportDraft
    }

    private fun getUserMemes(userId: String, viewerId: String?, ownProfile: Boolean): List<Meme> {
        return memes.values
            .filter { it.author.id == userId }
            .filter { ownProfile || canAccessMeme(it, viewerId) }
            .sortedByDescending { it.createdAt }
    }

    private fun getUserCollections(userId: String, viewerId: String?, ownProfile: Boolean): List<MemeCollection> {
        return collections.values
            .filter { it.author.id == userId }
            .filter { ownProfile || canAccessCollection(it, viewerId) }
            .sortedByDescending { it.createdAt }
    }

    private fun canAccessCollection(collection: MemeCollection, viewerId: String?): Boolean {
        if (collection.author.id == viewerId) return true
        return when (collection.visibility) {
            AccessLevel.PUBLIC -> true
            AccessLevel.PRIVATE -> false
            AccessLevel.GROUPS -> {
                val memberships = viewerId?.let(::groupIdsForUser).orEmpty()
                collection.sharedGroupIds.any { it in memberships }
            }
        }
    }

    private fun canAccessMeme(meme: Meme, viewerId: String?): Boolean {
        if (meme.author.id == viewerId) return true
        return when (meme.effectiveVisibility) {
            AccessLevel.PUBLIC -> true
            AccessLevel.PRIVATE -> false
            AccessLevel.GROUPS -> {
                val memberships = viewerId?.let(::groupIdsForUser).orEmpty()
                meme.effectiveSharedGroupIds.any { it in memberships }
            }
        }
    }

    private fun groupIdsForUser(userId: String): List<String> {
        return groups.values
            .filter { group -> group.members.any { it.user.id == userId } }
            .map { it.id }
    }

    private fun page(items: List<Meme>, cursor: String?): PagedResult<Meme> {
        val start = cursor?.toIntOrNull() ?: 0
        val chunk = items.drop(start).take(pageSize)
        val next = if (start + pageSize < items.size) (start + pageSize).toString() else null
        return PagedResult(items = chunk, nextCursor = next)
    }

    private fun validateVisibility(visibility: AccessLevel, sharedGroupIds: List<String>) {
        if (visibility == AccessLevel.GROUPS && sharedGroupIds.isEmpty()) {
            throw AppException(ErrorKind.VALIDATION, "Для ограниченного доступа выберите хотя бы одну группу.")
        }
    }

    private fun ensureOwnership(authorId: String, memeIds: List<String>) {
        val invalid = memeIds.firstOrNull { memes[it]?.author?.id != authorId }
        if (invalid != null) {
            throw AppException(ErrorKind.FORBIDDEN, "В подборку можно добавлять только свои мемы в этом MVP.")
        }
    }

    private fun recomputeMemeAccess(memeId: String) {
        val current = memes[memeId] ?: return
        val sources = collections.values
            .filter { it.itemIds.contains(memeId) }
            .map { it.visibility to it.sharedGroupIds }
        val (effectiveVisibility, effectiveGroups) = computeEffectiveAccess(
            directVisibility = current.directVisibility,
            directGroupIds = current.directSharedGroupIds,
            collectionSources = sources
        )
        memes[memeId] = current.copy(
            effectiveVisibility = effectiveVisibility,
            effectiveSharedGroupIds = effectiveGroups
        )
    }

    private fun userSummary(userId: String): UserSummary {
        val profile = users[userId]?.profile ?: throw AppException(ErrorKind.NOT_FOUND, "Пользователь не найден.")
        return profile.toSummary()
    }

    private fun createSession(profile: UserProfile): AuthSession {
        val userId = profile.id
        return AuthSession(
            accessToken = "access-$userId-${System.currentTimeMillis()}",
            refreshToken = "refresh-$userId",
            accessTokenExpiresAt = System.currentTimeMillis() + 60_000,
            user = profile
        )
    }

    private fun youtubeThumbnail(url: String): String {
        val videoId = when {
            "watch?v=" in url -> url.substringAfter("watch?v=").substringBefore("&")
            "youtu.be/" in url -> url.substringAfter("youtu.be/").substringBefore("?")
            "shorts/" in url -> url.substringAfter("shorts/").substringBefore("?")
            else -> "dQw4w9WgXcQ"
        }
        return "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
    }

    private fun UserProfile.toSummary(): UserSummary = UserSummary(
        id = id,
        login = login,
        displayName = displayName,
        avatarUrl = avatarUrl
    )

    private fun seed() {
        val now = System.currentTimeMillis()
        val me = UserProfile(
            id = "user-me",
            login = "gleb",
            displayName = "Gleb",
            avatarUrl = "https://picsum.photos/seed/gleb/120/120",
            bio = "Собираю коллекции мемов и тестирую доступы.",
            createdAt = now - 800_000
        )
        val kira = UserProfile(
            id = "user-kira",
            login = "kira",
            displayName = "User 2121223",
            avatarUrl = "https://picsum.photos/seed/kira/120/120",
            bio = "Люблю резкие подписи и странные thumbnail.",
            createdAt = now - 700_000
        )
        val max = UserProfile(
            id = "user-max",
            login = "max",
            displayName = "User Test",
            avatarUrl = "https://picsum.photos/seed/max/120/120",
            bio = "Делаю подборки под конкретные чаты.",
            createdAt = now - 600_000
        )
        val lana = UserProfile(
            id = "user-lana",
            login = "lana",
            displayName = "User Test 2",
            avatarUrl = "https://picsum.photos/seed/lana/120/120",
            bio = "Архивирую все, что пережило дедлайны.",
            createdAt = now - 500_000
        )

        users[me.id] = FakeUser(me, "memeflow")
        users[kira.id] = FakeUser(kira, "memeflow")
        users[max.id] = FakeUser(max, "memeflow")
        users[lana.id] = FakeUser(lana, "memeflow")

        val groupCrew = Group(
            id = "group-crew",
            name = "Group 1",
            members = listOf(
                GroupMember(me.toSummary(), GroupRole.OWNER),
                GroupMember(max.toSummary(), GroupRole.MEMBER)
            ),
            currentUserRole = GroupRole.OWNER
        )
        val groupMood = Group(
            id = "Group 2",
            name = "Moodboard",
            members = listOf(
                GroupMember(kira.toSummary(), GroupRole.OWNER),
                GroupMember(me.toSummary(), GroupRole.MEMBER)
            ),
            currentUserRole = GroupRole.MEMBER
        )
        groups[groupCrew.id] = groupCrew
        groups[groupMood.id] = groupMood

        val invite = GroupInvitation(
            id = "invite-lana",
            groupId = groupCrew.id,
            groupName = groupCrew.name,
            inviter = me.toSummary(),
            invitee = lana.toSummary(),
            status = InvitationStatus.PENDING,
            createdAt = now - 20_000
        )
        invitations[invite.id] = invite

        val media1 = seedMedia("media-1", "https://picsum.photos/seed/meme1/900/1200")
        val media2 = seedMedia("media-2", "https://picsum.photos/seed/meme2/900/1200")
        val media3 = seedMedia("media-3", "https://picsum.photos/seed/meme3/900/1200")
        val media4 = seedMedia(
            id = "media-4",
            source = "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            kind = MediaKind.EXTERNAL_VIDEO,
            sourceType = MediaSourceType.YOUTUBE_SHORT_URL,
            provider = MediaProvider.YOUTUBE,
            thumbnail = "https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        )

        val meme1 = seedMeme(
            id = "meme-1",
            author = kira.toSummary(),
            media = media1,
            caption = "Тестовые данные",
            tags = listOf("release", "design", "late"),
            directVisibility = AccessLevel.PUBLIC,
            directGroups = emptyList(),
            createdAt = now - 10_000
        )
        val meme2 = seedMeme(
            id = "meme-2",
            author = me.toSummary(),
            media = media2,
            caption = "Внутренний мем , локальный. Не выносить за чат.",
            tags = listOf("crew", "internal"),
            directVisibility = AccessLevel.GROUPS,
            directGroups = listOf(groupCrew.id),
            createdAt = now - 25_000
        )
        val meme3 = seedMeme(
            id = "meme-3",
            author = me.toSummary(),
            media = media3,
            caption = "Моковое изображение 1",
            tags = listOf("retro", "private"),
            directVisibility = AccessLevel.PRIVATE,
            directGroups = emptyList(),
            createdAt = now - 40_000
        )
        val meme4 = seedMeme(
            id = "meme-4",
            author = max.toSummary(),
            media = media4,
            caption = "Моковое изображение 3",
            tags = listOf("shorts", "demo"),
            directVisibility = AccessLevel.PUBLIC,
            directGroups = emptyList(),
            createdAt = now - 55_000
        )

        memes[meme1.id] = meme1
        memes[meme2.id] = meme2
        memes[meme3.id] = meme3
        memes[meme4.id] = meme4

        val collection1 = MemeCollection(
            id = "collection-1",
            name = "Public Hype Pack",
            description = "Подборка тематических мемов о программировании",
            author = me.toSummary(),
            visibility = AccessLevel.PUBLIC,
            sharedGroupIds = emptyList(),
            itemCount = 2,
            coverThumbnailUrl = media2.thumbnailUrl,
            itemIds = listOf(meme2.id, meme3.id),
            moderationStatus = ModerationStatus.APPROVED,
            createdAt = now - 12_000
        )
        val collection2 = MemeCollection(
            id = "collection-2",
            name = "Moodboard Vault",
            description = "Подборка мемов 2",
            author = kira.toSummary(),
            visibility = AccessLevel.GROUPS,
            sharedGroupIds = listOf(groupMood.id),
            itemCount = 1,
            coverThumbnailUrl = media1.thumbnailUrl,
            itemIds = listOf(meme1.id),
            moderationStatus = ModerationStatus.APPROVED,
            createdAt = now - 15_000
        )
        collections[collection1.id] = collection1
        collections[collection2.id] = collection2

        memes.keys.forEach(::recomputeMemeAccess)
    }

    private fun seedMedia(
        id: String,
        source: String,
        kind: MediaKind = MediaKind.IMAGE,
        sourceType: MediaSourceType = MediaSourceType.IMAGE_URL,
        provider: MediaProvider = MediaProvider.WEB,
        thumbnail: String = source
    ): MediaAsset {
        val media = MediaAsset(
            id = id,
            kind = kind,
            sourceType = sourceType,
            provider = provider,
            status = MediaStatus.READY,
            originalUrl = source,
            storageUrl = source,
            thumbnailUrl = thumbnail,
            title = if (kind == MediaKind.EXTERNAL_VIDEO) "Short по ссылке" else "Изображение"
        )
        mediaAssets[id] = media
        return media
    }

    private fun seedMeme(
        id: String,
        author: UserSummary,
        media: MediaAsset,
        caption: String,
        tags: List<String>,
        directVisibility: AccessLevel,
        directGroups: List<String>,
        createdAt: Long
    ): Meme {
        return Meme(
            id = id,
            author = author,
            media = media,
            caption = caption,
            tags = tags,
            directVisibility = directVisibility,
            directSharedGroupIds = directGroups,
            effectiveVisibility = directVisibility,
            effectiveSharedGroupIds = directGroups,
            moderationStatus = ModerationStatus.APPROVED,
            createdAt = createdAt
        )
    }
}
