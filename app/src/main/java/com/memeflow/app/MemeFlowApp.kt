package com.memeflow.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.memeflow.app.BuildConfig
import com.memeflow.app.core.domain.AddMemesToCollectionUseCase
import com.memeflow.app.core.domain.BootstrapSessionUseCase
import com.memeflow.app.core.domain.ContinueAsGuestUseCase
import com.memeflow.app.core.domain.CreateCollectionUseCase
import com.memeflow.app.core.domain.CreateGroupUseCase
import com.memeflow.app.core.domain.CreateMemeUseCase
import com.memeflow.app.core.domain.CreateReportUseCase
import com.memeflow.app.core.domain.ImportExternalMediaUseCase
import com.memeflow.app.core.domain.InviteUserToGroupUseCase
import com.memeflow.app.core.domain.LoadCollectionDetailsUseCase
import com.memeflow.app.core.domain.LoadFeedUseCase
import com.memeflow.app.core.domain.LoadGroupDetailsUseCase
import com.memeflow.app.core.domain.LoadInvitationsUseCase
import com.memeflow.app.core.domain.LoadMemeDetailsUseCase
import com.memeflow.app.core.domain.LoadMyGroupsUseCase
import com.memeflow.app.core.domain.LoadMyProfileUseCase
import com.memeflow.app.core.domain.LoadPublicProfileUseCase
import com.memeflow.app.core.domain.LoginUseCase
import com.memeflow.app.core.domain.LogoutUseCase
import com.memeflow.app.core.domain.ObserveSessionUseCase
import com.memeflow.app.core.domain.PollMediaStatusUseCase
import com.memeflow.app.core.domain.RegisterUseCase
import com.memeflow.app.core.domain.RemoveMemeFromCollectionUseCase
import com.memeflow.app.core.domain.RespondToInvitationUseCase
import com.memeflow.app.core.domain.SearchUseCase
import com.memeflow.app.core.domain.UpdateCollectionUseCase
import com.memeflow.app.core.domain.UpdateMemeUseCase
import com.memeflow.app.core.domain.UploadImageUseCase
import com.memeflow.app.core.network.TokenProvider
import com.memeflow.app.core.network.createRetrofitClient
import com.memeflow.app.core.session.SessionStore
import com.memeflow.app.data.mock.FakeMemeFlowBackend
import com.memeflow.app.data.repository.AuthRepository
import com.memeflow.app.data.repository.CollectionRepository
import com.memeflow.app.data.repository.FakeAuthRepository
import com.memeflow.app.data.repository.FakeCollectionRepository
import com.memeflow.app.data.repository.FakeFeedRepository
import com.memeflow.app.data.repository.FakeGroupRepository
import com.memeflow.app.data.repository.FakeMediaRepository
import com.memeflow.app.data.repository.FakeMemeRepository
import com.memeflow.app.data.repository.FakeReportRepository
import com.memeflow.app.data.repository.FakeSearchRepository
import com.memeflow.app.data.repository.FakeUserRepository
import com.memeflow.app.data.repository.FeedRepository
import com.memeflow.app.data.repository.GroupRepository
import com.memeflow.app.data.repository.MediaRepository
import com.memeflow.app.data.repository.MemeRepository
import com.memeflow.app.data.repository.RealAuthRepository
import com.memeflow.app.data.repository.RealCollectionRepository
import com.memeflow.app.data.repository.RealFeedRepository
import com.memeflow.app.data.repository.RealGroupRepository
import com.memeflow.app.data.repository.RealMediaRepository
import com.memeflow.app.data.repository.RealMemeRepository
import com.memeflow.app.data.repository.RealReportRepository
import com.memeflow.app.data.repository.RealSearchRepository
import com.memeflow.app.data.repository.RealUserRepository
import com.memeflow.app.data.repository.ReportRepository
import com.memeflow.app.data.repository.SearchRepository
import com.memeflow.app.data.repository.UserRepository
import com.memeflow.app.navigation.AppNavigation
import com.memeflow.app.ui.theme.MemeFlowTheme

class AppContainer(context: Context) {
    val useMockBackend: Boolean = BuildConfig.USE_MOCK_BACKEND

    private val sessionStore = SessionStore(context)

    private val backend: FakeMemeFlowBackend?
    private val api: com.memeflow.app.core.network.MemeFlowApi?
    private val tokenProvider: TokenProvider?

    private val _auth: AuthRepository
    private val _feed: FeedRepository
    private val _search: SearchRepository
    private val _user: UserRepository
    private val _media: MediaRepository
    private val _meme: MemeRepository
    private val _collection: CollectionRepository
    private val _group: GroupRepository
    private val _report: ReportRepository

    init {
        if (useMockBackend) {
            val b = FakeMemeFlowBackend()
            backend = b
            api = null
            tokenProvider = null

            val a = FakeAuthRepository(b, sessionStore)
            _auth = a
            _feed = FakeFeedRepository(b, a)
            _search = FakeSearchRepository(b, a)
            _user = FakeUserRepository(b, a)
            _media = FakeMediaRepository(b, a)
            _meme = FakeMemeRepository(b, a)
            _collection = FakeCollectionRepository(b, a)
            _group = FakeGroupRepository(b, a)
            _report = FakeReportRepository(b, a)
        } else {
            backend = null
            val tp = TokenProvider()
            tokenProvider = tp
            val a = createRetrofitClient(
                baseUrl = BuildConfig.API_BASE_URL,
                tokenProvider = tp,
                onTokenExpired = {}
            )
            api = a

            val ra = RealAuthRepository(a, sessionStore, tp)
            _auth = ra
            _feed = RealFeedRepository(a, ra)
            _search = RealSearchRepository(a, ra)
            _user = RealUserRepository(a, ra)
            _media = RealMediaRepository(a, ra, context)
            _meme = RealMemeRepository(a, ra)
            _collection = RealCollectionRepository(a, ra)
            _group = RealGroupRepository(a, ra)
            _report = RealReportRepository(a, ra)
        }
    }

    val authRepository: AuthRepository get() = _auth

    val bootstrapSessionUseCase = BootstrapSessionUseCase(_auth)
    val observeSessionUseCase = ObserveSessionUseCase(_auth)
    val continueAsGuestUseCase = ContinueAsGuestUseCase(_auth)
    val loginUseCase = LoginUseCase(_auth)
    val registerUseCase = RegisterUseCase(_auth)
    val logoutUseCase = LogoutUseCase(_auth)

    val loadFeedUseCase = LoadFeedUseCase(_feed)
    val searchUseCase = SearchUseCase(_search)
    val loadMyProfileUseCase = LoadMyProfileUseCase(_user)
    val loadPublicProfileUseCase = LoadPublicProfileUseCase(_user)

    val uploadImageUseCase = UploadImageUseCase(_media)
    val importExternalMediaUseCase = ImportExternalMediaUseCase(_media)
    val pollMediaStatusUseCase = PollMediaStatusUseCase(_media)

    val createMemeUseCase = CreateMemeUseCase(_meme)
    val updateMemeUseCase = UpdateMemeUseCase(_meme)
    val loadMemeDetailsUseCase = LoadMemeDetailsUseCase(_meme)

    val createCollectionUseCase = CreateCollectionUseCase(_collection)
    val updateCollectionUseCase = UpdateCollectionUseCase(_collection)
    val addMemesToCollectionUseCase = AddMemesToCollectionUseCase(_collection)
    val removeMemeFromCollectionUseCase = RemoveMemeFromCollectionUseCase(_collection)
    val loadCollectionDetailsUseCase = LoadCollectionDetailsUseCase(_collection)

    val loadMyGroupsUseCase = LoadMyGroupsUseCase(_group)
    val loadInvitationsUseCase = LoadInvitationsUseCase(_group)
    val createGroupUseCase = CreateGroupUseCase(_group)
    val loadGroupDetailsUseCase = LoadGroupDetailsUseCase(_group)
    val inviteUserToGroupUseCase = InviteUserToGroupUseCase(_group)
    val respondToInvitationUseCase = RespondToInvitationUseCase(_group)

    val createReportUseCase = CreateReportUseCase(_report)
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer is not available")
}

@Composable
fun MemeFlowApp(container: AppContainer) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        MemeFlowTheme {
            AppNavigation()
        }
    }
}
