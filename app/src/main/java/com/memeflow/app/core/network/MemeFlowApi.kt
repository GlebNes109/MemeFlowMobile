package com.memeflow.app.core.network

import com.memeflow.app.core.network.dto.*
import okhttp3.MultipartBody
import retrofit2.http.*

interface MemeFlowApi {

    @POST("v1/auth/register")
    suspend fun register(@Body request: RegisterRequestDto): AuthSessionDto

    @POST("v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): AuthSessionDto

    @POST("v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshTokenRequestDto): AuthSessionDto

    @GET("v1/users/me")
    suspend fun getMyProfile(): MyProfileDto

    @PATCH("v1/users/me")
    suspend fun updateMyProfile(@Body request: UpdateMyProfileRequestDto): MyProfileDto

    @GET("v1/users/me/memes")
    suspend fun getMyMemes(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null
    ): MemePageDto

    @GET("v1/users/me/collections")
    suspend fun getMyCollections(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null
    ): CollectionPageDto

    @GET("v1/users/{userId}")
    suspend fun getPublicProfile(@Path("userId") userId: String): PublicProfileDto

    @GET("v1/users/{userId}/memes")
    suspend fun getUserPublicMemes(
        @Path("userId") userId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null
    ): MemePageDto

    @GET("v1/users/{userId}/collections")
    suspend fun getUserPublicCollections(
        @Path("userId") userId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null
    ): CollectionPageDto

    @GET("v1/feed")
    suspend fun getFeed(
        @Query("scope") scope: String = "public",
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null
    ): MemePageDto

    @GET("v1/search")
    suspend fun search(
        @Query("query") query: String,
        @Query("limit") limit: Int? = null
    ): SearchResponseDto

    @Multipart
    @POST("v1/media/images")
    suspend fun uploadImage(@Part file: MultipartBody.Part): MediaAssetDto

    @POST("v1/media/external-imports")
    suspend fun importExternalMedia(@Body request: CreateExternalImportRequestDto): MediaAssetDto

    @GET("v1/media/{mediaAssetId}")
    suspend fun getMedia(@Path("mediaAssetId") mediaAssetId: String): MediaAssetDto

    @POST("v1/memes")
    suspend fun createMeme(@Body request: CreateMemeRequestDto): MemeDetailsDto

    @GET("v1/memes/{memeId}")
    suspend fun getMeme(@Path("memeId") memeId: String): MemeDetailsDto

    @PATCH("v1/memes/{memeId}")
    suspend fun updateMeme(
        @Path("memeId") memeId: String,
        @Body request: UpdateMemeRequestDto
    ): MemeDetailsDto

    @DELETE("v1/memes/{memeId}")
    suspend fun deleteMeme(@Path("memeId") memeId: String)

    @POST("v1/collections")
    suspend fun createCollection(@Body request: CreateCollectionRequestDto): CollectionDetailsDto

    @GET("v1/collections/{collectionId}")
    suspend fun getCollection(@Path("collectionId") collectionId: String): CollectionDetailsDto

    @PATCH("v1/collections/{collectionId}")
    suspend fun updateCollection(
        @Path("collectionId") collectionId: String,
        @Body request: UpdateCollectionRequestDto
    ): CollectionDetailsDto

    @DELETE("v1/collections/{collectionId}")
    suspend fun deleteCollection(@Path("collectionId") collectionId: String)

    @POST("v1/collections/{collectionId}/items")
    suspend fun addCollectionItems(
        @Path("collectionId") collectionId: String,
        @Body request: AddCollectionItemsRequestDto
    ): CollectionDetailsDto

    @DELETE("v1/collections/{collectionId}/items/{memeId}")
    suspend fun removeCollectionItem(
        @Path("collectionId") collectionId: String,
        @Path("memeId") memeId: String
    )

    @POST("v1/groups")
    suspend fun createGroup(@Body request: CreateGroupRequestDto): GroupDetailsDto

    @GET("v1/groups/my")
    suspend fun getMyGroups(): GroupListResponseDto

    @GET("v1/groups/{groupId}")
    suspend fun getGroup(@Path("groupId") groupId: String): GroupDetailsDto

    @GET("v1/groups/{groupId}/feed")
    suspend fun getGroupFeed(
        @Path("groupId") groupId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null
    ): MemePageDto

    @POST("v1/groups/{groupId}/invites")
    suspend fun inviteToGroup(
        @Path("groupId") groupId: String,
        @Body request: CreateGroupInviteRequestDto
    ): GroupInvitationDto

    @GET("v1/group-invitations")
    suspend fun getInvitations(): GroupInvitationListResponseDto

    @POST("v1/group-invitations/{inviteId}/accept")
    suspend fun acceptInvitation(@Path("inviteId") inviteId: String): GroupInvitationDto

    @POST("v1/group-invitations/{inviteId}/decline")
    suspend fun declineInvitation(@Path("inviteId") inviteId: String): GroupInvitationDto

    @POST("v1/reports")
    suspend fun createReport(@Body request: CreateReportRequestDto)
}
