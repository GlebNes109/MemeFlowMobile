package com.memeflow.app.core.domain

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
import com.memeflow.app.data.repository.AuthRepository
import com.memeflow.app.data.repository.CollectionRepository
import com.memeflow.app.data.repository.FeedRepository
import com.memeflow.app.data.repository.GroupRepository
import com.memeflow.app.data.repository.MediaRepository
import com.memeflow.app.data.repository.MemeRepository
import com.memeflow.app.data.repository.ReportRepository
import com.memeflow.app.data.repository.SearchRepository
import com.memeflow.app.data.repository.UserRepository
import kotlinx.coroutines.flow.StateFlow

class BootstrapSessionUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke() = repository.bootstrap()
}

class ObserveSessionUseCase(private val repository: AuthRepository) {
    operator fun invoke(): StateFlow<SessionSnapshot> = repository.session
}

class ContinueAsGuestUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke() = repository.continueAsGuest()
}

class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(login: String, password: String) = repository.login(login, password)
}

class RegisterUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(login: String, password: String, displayName: String) =
        repository.register(login, password, displayName)
}

class LogoutUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke() = repository.logout()
}

class LoadFeedUseCase(private val repository: FeedRepository) {
    suspend operator fun invoke(scope: FeedScope, cursor: String? = null): PagedResult<Meme> =
        repository.loadFeed(scope, cursor)
}

class SearchUseCase(private val repository: SearchRepository) {
    suspend operator fun invoke(query: String): SearchResults = repository.search(query)
}

class LoadMyProfileUseCase(private val repository: UserRepository) {
    suspend operator fun invoke(): ProfileBundle = repository.loadMyProfile()
}

class LoadPublicProfileUseCase(private val repository: UserRepository) {
    suspend operator fun invoke(userId: String): ProfileBundle = repository.loadPublicProfile(userId)
}

class UploadImageUseCase(private val repository: MediaRepository) {
    suspend operator fun invoke(localUri: String): MediaAsset = repository.uploadImage(localUri)
}

class ImportExternalMediaUseCase(private val repository: MediaRepository) {
    suspend operator fun invoke(url: String): MediaAsset = repository.importExternalMedia(url)
}

class PollMediaStatusUseCase(private val repository: MediaRepository) {
    suspend operator fun invoke(mediaAssetId: String): MediaAsset = repository.getMedia(mediaAssetId)
}

class CreateMemeUseCase(private val repository: MemeRepository) {
    suspend operator fun invoke(draft: MemeDraft): Meme = repository.createMeme(draft)
}

class UpdateMemeUseCase(private val repository: MemeRepository) {
    suspend operator fun invoke(memeId: String, draft: MemeDraft): Meme = repository.updateMeme(memeId, draft)
}

class LoadMemeDetailsUseCase(private val repository: MemeRepository) {
    suspend operator fun invoke(memeId: String): MemeDetails = repository.loadMemeDetails(memeId)
}

class CreateCollectionUseCase(private val repository: CollectionRepository) {
    suspend operator fun invoke(draft: CollectionDraft): MemeCollection = repository.createCollection(draft)
}

class UpdateCollectionUseCase(private val repository: CollectionRepository) {
    suspend operator fun invoke(collectionId: String, draft: CollectionDraft): MemeCollection =
        repository.updateCollection(collectionId, draft)
}

class AddMemesToCollectionUseCase(private val repository: CollectionRepository) {
    suspend operator fun invoke(collectionId: String, memeIds: List<String>): MemeCollection =
        repository.addMemes(collectionId, memeIds)
}

class RemoveMemeFromCollectionUseCase(private val repository: CollectionRepository) {
    suspend operator fun invoke(collectionId: String, memeId: String): MemeCollection =
        repository.removeMeme(collectionId, memeId)
}

class LoadCollectionDetailsUseCase(private val repository: CollectionRepository) {
    suspend operator fun invoke(collectionId: String): CollectionDetails =
        repository.loadCollectionDetails(collectionId)
}

class LoadMyGroupsUseCase(private val repository: GroupRepository) {
    suspend operator fun invoke(): List<Group> = repository.loadMyGroups()
}

class LoadInvitationsUseCase(private val repository: GroupRepository) {
    suspend operator fun invoke(): List<GroupInvitation> = repository.loadInvitations()
}

class CreateGroupUseCase(private val repository: GroupRepository) {
    suspend operator fun invoke(name: String): Group = repository.createGroup(name)
}

class LoadGroupDetailsUseCase(private val repository: GroupRepository) {
    suspend operator fun invoke(groupId: String): Group = repository.loadGroupDetails(groupId)
}

class InviteUserToGroupUseCase(private val repository: GroupRepository) {
    suspend operator fun invoke(groupId: String, login: String): GroupInvitation =
        repository.inviteUser(groupId, login)
}

class RespondToInvitationUseCase(private val repository: GroupRepository) {
    suspend operator fun invoke(invitationId: String, accept: Boolean): GroupInvitation =
        repository.respondToInvitation(invitationId, accept)
}

class CreateReportUseCase(private val repository: ReportRepository) {
    suspend operator fun invoke(reportDraft: ReportDraft) = repository.createReport(reportDraft)
}
