package com.memeflow.app.core.model

enum class AccessLevel {
    PRIVATE,
    GROUPS,
    PUBLIC
}

enum class ModerationStatus {
    PENDING,
    APPROVED,
    REJECTED
}

enum class MediaKind {
    IMAGE,
    EXTERNAL_VIDEO
}

enum class MediaSourceType {
    IMAGE_UPLOAD,
    IMAGE_URL,
    YOUTUBE_SHORT_URL
}

enum class MediaProvider {
    UPLOAD,
    WEB,
    YOUTUBE
}

enum class MediaStatus {
    PROCESSING,
    READY,
    FAILED,
    BLOCKED
}

enum class GroupRole {
    OWNER,
    MEMBER
}

enum class InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}

enum class FeedScope {
    PUBLIC,
    ACCESSIBLE
}

enum class ReportTargetType {
    MEME,
    COLLECTION
}

data class UserSummary(
    val id: String,
    val login: String,
    val displayName: String,
    val avatarUrl: String?
)

data class UserProfile(
    val id: String,
    val login: String,
    val displayName: String,
    val avatarUrl: String?,
    val bio: String,
    val createdAt: Long
)

data class SessionSnapshot(
    val isBootstrapping: Boolean = true,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val accessTokenExpiresAt: Long = 0L,
    val currentUser: UserSummary? = null
) {
    val isAuthorized: Boolean = currentUser != null && !accessToken.isNullOrBlank()
    val isGuest: Boolean = !isBootstrapping && !isAuthorized
}

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: Long,
    val user: UserProfile
)

data class MediaAsset(
    val id: String,
    val kind: MediaKind,
    val sourceType: MediaSourceType,
    val provider: MediaProvider,
    val status: MediaStatus,
    val originalUrl: String,
    val storageUrl: String?,
    val thumbnailUrl: String?,
    val title: String? = null
)

data class Meme(
    val id: String,
    val author: UserSummary,
    val media: MediaAsset,
    val caption: String,
    val tags: List<String>,
    val directVisibility: AccessLevel,
    val directSharedGroupIds: List<String>,
    val effectiveVisibility: AccessLevel,
    val effectiveSharedGroupIds: List<String>,
    val moderationStatus: ModerationStatus,
    val createdAt: Long
)

data class CollectionAccessSource(
    val collectionId: String,
    val collectionName: String,
    val visibility: AccessLevel,
    val sharedGroupIds: List<String>
)

data class MemeDetails(
    val meme: Meme,
    val canEdit: Boolean,
    val collections: List<MemeCollection>,
    val inheritedAccessFromCollections: List<CollectionAccessSource>
)

data class MemeCollection(
    val id: String,
    val name: String,
    val description: String,
    val author: UserSummary,
    val visibility: AccessLevel,
    val sharedGroupIds: List<String>,
    val itemCount: Int,
    val coverThumbnailUrl: String?,
    val itemIds: List<String>,
    val moderationStatus: ModerationStatus,
    val createdAt: Long
)

data class CollectionDetails(
    val collection: MemeCollection,
    val items: List<Meme>,
    val canEdit: Boolean
)

data class GroupMember(
    val user: UserSummary,
    val role: GroupRole
)

data class Group(
    val id: String,
    val name: String,
    val members: List<GroupMember>,
    val currentUserRole: GroupRole?,
    val accessibleMemes: List<Meme> = emptyList()
)

data class GroupInvitation(
    val id: String,
    val groupId: String,
    val groupName: String,
    val inviter: UserSummary,
    val invitee: UserSummary,
    val status: InvitationStatus,
    val createdAt: Long
)

data class SearchResults(
    val users: List<UserProfile>,
    val collections: List<MemeCollection>,
    val memes: List<Meme>
)

data class PagedResult<T>(
    val items: List<T>,
    val nextCursor: String?
)

data class ProfileBundle(
    val profile: UserProfile,
    val memes: List<Meme>,
    val collections: List<MemeCollection>,
    val groups: List<Group> = emptyList()
)

data class MemeDraft(
    val mediaAssetId: String,
    val caption: String,
    val tags: List<String>,
    val visibility: AccessLevel,
    val sharedGroupIds: List<String>
)

data class CollectionDraft(
    val name: String,
    val description: String,
    val visibility: AccessLevel,
    val sharedGroupIds: List<String>,
    val memeIds: List<String>
)

data class ReportDraft(
    val targetType: ReportTargetType,
    val targetId: String,
    val reason: String,
    val comment: String
)

data class CollectionWarning(
    val memeId: String,
    val memeCaption: String,
    val from: AccessLevel,
    val to: AccessLevel
)
