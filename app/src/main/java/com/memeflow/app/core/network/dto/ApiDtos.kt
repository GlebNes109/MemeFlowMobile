package com.memeflow.app.core.network.dto

import com.google.gson.annotations.SerializedName

data class ErrorResponseDto(
    val code: String,
    val message: String
)

data class UserSummaryDto(
    val id: String,
    val login: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("avatar_url") val avatarUrl: String?
)

data class GroupSummaryDto(
    val id: String,
    val name: String,
    @SerializedName("member_count") val memberCount: Int
)

data class AuthTokensDto(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_in") val expiresIn: Int
)

data class AuthSessionDto(
    val tokens: AuthTokensDto,
    val user: UserSummaryDto
)

data class LoginRequestDto(
    val login: String,
    val password: String
)

data class RegisterRequestDto(
    val login: String,
    val password: String,
    @SerializedName("display_name") val displayName: String
)

data class RefreshTokenRequestDto(
    @SerializedName("refresh_token") val refreshToken: String
)

data class MediaAssetDto(
    val id: String,
    val kind: String,
    @SerializedName("source_type") val sourceType: String,
    val provider: String,
    val status: String,
    @SerializedName("original_url") val originalUrl: String?,
    @SerializedName("storage_url") val storageUrl: String?,
    @SerializedName("thumbnail_url") val thumbnailUrl: String?,
    val title: String?,
    @SerializedName("created_at") val createdAt: String
)

data class CreateExternalImportRequestDto(
    val url: String
)

data class MemeCardDto(
    val id: String,
    val author: UserSummaryDto,
    val media: MediaAssetDto,
    val caption: String?,
    val tags: List<String>,
    @SerializedName("direct_visibility") val directVisibility: String,
    @SerializedName("direct_shared_group_ids") val directSharedGroupIds: List<String>,
    @SerializedName("effective_visibility") val effectiveVisibility: String,
    @SerializedName("effective_shared_group_ids") val effectiveSharedGroupIds: List<String>,
    @SerializedName("moderation_status") val moderationStatus: String,
    @SerializedName("created_at") val createdAt: String
)

data class CollectionAccessSummaryDto(
    val id: String,
    val name: String,
    val visibility: String,
    @SerializedName("shared_group_ids") val sharedGroupIds: List<String>
)

data class MemeDetailsDto(
    val id: String,
    val author: UserSummaryDto,
    val media: MediaAssetDto,
    val caption: String?,
    val tags: List<String>,
    @SerializedName("direct_visibility") val directVisibility: String,
    @SerializedName("direct_shared_group_ids") val directSharedGroupIds: List<String>,
    @SerializedName("effective_visibility") val effectiveVisibility: String,
    @SerializedName("effective_shared_group_ids") val effectiveSharedGroupIds: List<String>,
    @SerializedName("moderation_status") val moderationStatus: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("can_edit") val canEdit: Boolean,
    @SerializedName("collection_ids") val collectionIds: List<String>,
    @SerializedName("inherited_access_from_collections") val inheritedAccessFromCollections: List<CollectionAccessSummaryDto>
)

data class CreateMemeRequestDto(
    @SerializedName("media_asset_id") val mediaAssetId: String,
    val caption: String?,
    val tags: List<String>?,
    val visibility: String,
    @SerializedName("shared_group_ids") val sharedGroupIds: List<String>?
)

data class UpdateMemeRequestDto(
    val caption: String?,
    val tags: List<String>?,
    val visibility: String?,
    @SerializedName("shared_group_ids") val sharedGroupIds: List<String>?
)

data class CollectionCardDto(
    val id: String,
    val name: String,
    val description: String?,
    val author: UserSummaryDto,
    val visibility: String,
    @SerializedName("shared_group_ids") val sharedGroupIds: List<String>,
    @SerializedName("moderation_status") val moderationStatus: String,
    @SerializedName("item_count") val itemCount: Int,
    @SerializedName("cover_thumbnail_url") val coverThumbnailUrl: String?,
    @SerializedName("created_at") val createdAt: String
)

data class CollectionItemViewDto(
    val position: Int,
    val meme: MemeCardDto
)

data class CollectionDetailsDto(
    val id: String,
    val name: String,
    val description: String?,
    val author: UserSummaryDto,
    val visibility: String,
    @SerializedName("shared_group_ids") val sharedGroupIds: List<String>,
    @SerializedName("moderation_status") val moderationStatus: String,
    @SerializedName("item_count") val itemCount: Int,
    @SerializedName("cover_thumbnail_url") val coverThumbnailUrl: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("can_edit") val canEdit: Boolean,
    val items: List<CollectionItemViewDto>
)

data class CreateCollectionRequestDto(
    val name: String,
    val description: String?,
    val visibility: String,
    @SerializedName("shared_group_ids") val sharedGroupIds: List<String>?,
    @SerializedName("meme_ids") val memeIds: List<String>?
)

data class UpdateCollectionRequestDto(
    val name: String?,
    val description: String?,
    val visibility: String?,
    @SerializedName("shared_group_ids") val sharedGroupIds: List<String>?
)

data class AddCollectionItemsRequestDto(
    @SerializedName("meme_ids") val memeIds: List<String>
)

data class GroupMemberDto(
    val user: UserSummaryDto,
    val role: String
)

data class GroupDetailsDto(
    val id: String,
    val name: String,
    val members: List<GroupMemberDto>,
    @SerializedName("current_user_role") val currentUserRole: String?
)

data class GroupListResponseDto(
    val items: List<GroupSummaryDto>
)

data class CreateGroupRequestDto(
    val name: String
)

data class CreateGroupInviteRequestDto(
    @SerializedName("invitee_user_id") val inviteeUserId: String
)

data class GroupInvitationDto(
    val id: String,
    val group: GroupSummaryDto,
    val inviter: UserSummaryDto,
    val invitee: UserSummaryDto,
    val status: String,
    @SerializedName("created_at") val createdAt: String
)

data class GroupInvitationListResponseDto(
    val items: List<GroupInvitationDto>
)

data class MyProfileDto(
    val id: String,
    val login: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("avatar_url") val avatarUrl: String?,
    val bio: String?,
    val groups: List<GroupSummaryDto>
)

data class PublicProfileDto(
    val id: String,
    val login: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("avatar_url") val avatarUrl: String?,
    val bio: String?,
    @SerializedName("public_meme_count") val publicMemeCount: Int,
    @SerializedName("public_collection_count") val publicCollectionCount: Int
)

data class UpdateMyProfileRequestDto(
    @SerializedName("display_name") val displayName: String?,
    val bio: String?,
    @SerializedName("avatar_url") val avatarUrl: String?
)

data class MemePageDto(
    val items: List<MemeCardDto>,
    @SerializedName("next_cursor") val nextCursor: String?
)

data class CollectionPageDto(
    val items: List<CollectionCardDto>,
    @SerializedName("next_cursor") val nextCursor: String?
)

data class SearchResponseDto(
    val users: List<UserSummaryDto>,
    val memes: List<MemeCardDto>,
    val collections: List<CollectionCardDto>
)

data class CreateReportRequestDto(
    @SerializedName("target_type") val targetType: String,
    @SerializedName("target_id") val targetId: String,
    val reason: String,
    val comment: String?
)

data class ReportDto(
    val id: String,
    @SerializedName("target_type") val targetType: String,
    @SerializedName("target_id") val targetId: String,
    val reason: String,
    val comment: String?,
    val status: String,
    @SerializedName("created_at") val createdAt: String
)
