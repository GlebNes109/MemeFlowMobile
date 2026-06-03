package com.memeflow.app.data.mapper

import com.memeflow.app.core.model.*
import com.memeflow.app.core.network.dto.*
import java.text.SimpleDateFormat
import java.util.*

fun parseIsoTimestamp(iso: String): Long {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.parse(iso)?.time ?: 0L
}

fun mapVisibility(value: String): AccessLevel = when (value) {
    "public" -> AccessLevel.PUBLIC
    "private" -> AccessLevel.PRIVATE
    "groups" -> AccessLevel.GROUPS
    else -> AccessLevel.PRIVATE
}

fun mapModerationStatus(value: String): ModerationStatus = when (value) {
    "pending" -> ModerationStatus.PENDING
    "approved" -> ModerationStatus.APPROVED
    "rejected" -> ModerationStatus.REJECTED
    else -> ModerationStatus.PENDING
}

fun mapMediaKind(value: String): MediaKind = when (value) {
    "image" -> MediaKind.IMAGE
    "external_video" -> MediaKind.EXTERNAL_VIDEO
    else -> MediaKind.IMAGE
}

fun mapMediaSourceType(value: String): MediaSourceType = when (value) {
    "image_upload" -> MediaSourceType.IMAGE_UPLOAD
    "image_url" -> MediaSourceType.IMAGE_URL
    "youtube_short_url" -> MediaSourceType.YOUTUBE_SHORT_URL
    else -> MediaSourceType.IMAGE_URL
}

fun mapMediaProvider(value: String): MediaProvider = when (value) {
    "upload" -> MediaProvider.UPLOAD
    "web" -> MediaProvider.WEB
    "youtube" -> MediaProvider.YOUTUBE
    else -> MediaProvider.WEB
}

fun mapMediaStatus(value: String): MediaStatus = when (value) {
    "processing" -> MediaStatus.PROCESSING
    "ready" -> MediaStatus.READY
    "failed" -> MediaStatus.FAILED
    "blocked" -> MediaStatus.BLOCKED
    else -> MediaStatus.PROCESSING
}

fun mapGroupRole(value: String): GroupRole = when (value) {
    "owner" -> GroupRole.OWNER
    "member" -> GroupRole.MEMBER
    else -> GroupRole.MEMBER
}

fun mapInvitationStatus(value: String): InvitationStatus = when (value) {
    "pending" -> InvitationStatus.PENDING
    "accepted" -> InvitationStatus.ACCEPTED
    "declined" -> InvitationStatus.DECLINED
    else -> InvitationStatus.PENDING
}

fun mapVisibilityApi(value: AccessLevel): String = when (value) {
    AccessLevel.PUBLIC -> "public"
    AccessLevel.PRIVATE -> "private"
    AccessLevel.GROUPS -> "groups"
}

fun UserSummaryDto.toDomain() = UserSummary(
    id = id,
    login = login,
    displayName = displayName,
    avatarUrl = avatarUrl
)

fun UserSummaryDto.toUserProfile() = UserProfile(
    id = id,
    login = login,
    displayName = displayName,
    avatarUrl = avatarUrl,
    bio = "",
    createdAt = 0L
)

fun MediaAssetDto.toDomain() = MediaAsset(
    id = id,
    kind = mapMediaKind(kind),
    sourceType = mapMediaSourceType(sourceType),
    provider = mapMediaProvider(provider),
    status = mapMediaStatus(status),
    originalUrl = originalUrl ?: "",
    storageUrl = storageUrl,
    thumbnailUrl = thumbnailUrl,
    title = title
)

fun MemeCardDto.toDomain() = Meme(
    id = id,
    author = author.toDomain(),
    media = media.toDomain(),
    caption = caption ?: "",
    tags = tags,
    directVisibility = mapVisibility(directVisibility),
    directSharedGroupIds = directSharedGroupIds,
    effectiveVisibility = mapVisibility(effectiveVisibility),
    effectiveSharedGroupIds = effectiveSharedGroupIds,
    moderationStatus = mapModerationStatus(moderationStatus),
    createdAt = parseIsoTimestamp(createdAt)
)

fun CollectionAccessSummaryDto.toDomain() = CollectionAccessSource(
    collectionId = id,
    collectionName = name,
    visibility = mapVisibility(visibility),
    sharedGroupIds = sharedGroupIds
)

fun MemeDetailsDto.toMeme() = Meme(
    id = id,
    author = author.toDomain(),
    media = media.toDomain(),
    caption = caption ?: "",
    tags = tags,
    directVisibility = mapVisibility(directVisibility),
    directSharedGroupIds = directSharedGroupIds,
    effectiveVisibility = mapVisibility(effectiveVisibility),
    effectiveSharedGroupIds = effectiveSharedGroupIds,
    moderationStatus = mapModerationStatus(moderationStatus),
    createdAt = parseIsoTimestamp(createdAt)
)

fun MemeDetailsDto.toCollections(): List<MemeCollection> {
    return collectionIds.map { id ->
        MemeCollection(
            id = id,
            name = "",
            description = "",
            author = author.toDomain(),
            visibility = AccessLevel.PRIVATE,
            sharedGroupIds = emptyList(),
            itemCount = 0,
            coverThumbnailUrl = null,
            itemIds = emptyList(),
            moderationStatus = ModerationStatus.APPROVED,
            createdAt = 0L
        )
    }
}

fun MemeDetailsDto.toDomain() = MemeDetails(
    meme = toMeme(),
    canEdit = canEdit,
    collections = toCollections(),
    inheritedAccessFromCollections = inheritedAccessFromCollections.map { it.toDomain() }
)

fun CollectionCardDto.toDomain() = MemeCollection(
    id = id,
    name = name,
    description = description ?: "",
    author = author.toDomain(),
    visibility = mapVisibility(visibility),
    sharedGroupIds = sharedGroupIds,
    itemCount = itemCount,
    coverThumbnailUrl = coverThumbnailUrl,
    itemIds = emptyList(),
    moderationStatus = mapModerationStatus(moderationStatus),
    createdAt = parseIsoTimestamp(createdAt)
)

fun CollectionDetailsDto.toCollection() = MemeCollection(
    id = id,
    name = name,
    description = description ?: "",
    author = author.toDomain(),
    visibility = mapVisibility(visibility),
    sharedGroupIds = sharedGroupIds,
    itemCount = itemCount,
    coverThumbnailUrl = coverThumbnailUrl,
    itemIds = items.map { it.meme.id },
    moderationStatus = mapModerationStatus(moderationStatus),
    createdAt = parseIsoTimestamp(createdAt)
)

fun CollectionDetailsDto.toDomain() = CollectionDetails(
    collection = toCollection(),
    items = items.map { it.meme.toDomain() },
    canEdit = canEdit
)

fun GroupMemberDto.toDomain() = GroupMember(
    user = user.toDomain(),
    role = mapGroupRole(role)
)

fun GroupDetailsDto.toDomain() = Group(
    id = id,
    name = name,
    members = members.map { it.toDomain() },
    currentUserRole = currentUserRole?.let { mapGroupRole(it) }
)

fun GroupSummaryDto.toDomain() = Group(
    id = id,
    name = name,
    members = emptyList(),
    currentUserRole = GroupRole.MEMBER
)

fun GroupInvitationDto.toDomain() = GroupInvitation(
    id = id,
    groupId = group.id,
    groupName = group.name,
    inviter = inviter.toDomain(),
    invitee = invitee.toDomain(),
    status = mapInvitationStatus(status),
    createdAt = parseIsoTimestamp(createdAt)
)

fun MyProfileDto.toDomain() = ProfileBundle(
    profile = UserProfile(
        id = id,
        login = login,
        displayName = displayName,
        avatarUrl = avatarUrl,
        bio = bio ?: "",
        createdAt = 0L
    ),
    memes = emptyList(),
    collections = emptyList(),
    groups = groups.map { it.toDomain() }
)

fun PublicProfileDto.toDomain() = UserProfile(
    id = id,
    login = login,
    displayName = displayName,
    avatarUrl = avatarUrl,
    bio = bio ?: "",
    createdAt = 0L
)
