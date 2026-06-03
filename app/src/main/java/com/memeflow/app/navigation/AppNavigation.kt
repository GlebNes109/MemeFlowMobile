package com.memeflow.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.memeflow.app.LocalAppContainer
import com.memeflow.app.feature.auth.AuthScreen
import com.memeflow.app.feature.collection.CollectionDetailsScreen
import com.memeflow.app.feature.collection.CreateEditCollectionScreen
import com.memeflow.app.feature.feed.FeedScreen
import com.memeflow.app.feature.groups.GroupDetailsScreen
import com.memeflow.app.feature.groups.GroupsScreen
import com.memeflow.app.feature.groups.InvitationsScreen
import com.memeflow.app.feature.meme.CreateEditMemeScreen
import com.memeflow.app.feature.meme.MemeDetailsScreen
import com.memeflow.app.feature.profile.MyProfileScreen
import com.memeflow.app.feature.profile.PublicProfileScreen
import com.memeflow.app.feature.search.SearchScreen
import com.memeflow.app.feature.settings.SettingsScreen
import com.memeflow.app.feature.splash.SplashScreen
import com.memeflow.app.feature.upload.UploadScreen

object Routes {
    const val Splash = "splash"
    const val Auth = "auth"
    const val Feed = "feed"
    const val Search = "search"
    const val Upload = "upload"
    const val MyProfile = "profile/me"
    const val Settings = "settings"
    const val Groups = "groups"
    const val Invitations = "invitations"
    const val PublicProfile = "profile/public/{userId}"
    const val MemeDetails = "meme/{memeId}"
    const val CreateMeme = "meme/create/{mediaId}"
    const val EditMeme = "meme/edit/{memeId}"
    const val CollectionDetails = "collection/{collectionId}"
    const val CreateCollection = "collection/create"
    const val EditCollection = "collection/edit/{collectionId}"
    const val GroupDetails = "group/{groupId}"

    fun publicProfile(userId: String) = "profile/public/$userId"
    fun memeDetails(memeId: String) = "meme/$memeId"
    fun createMeme(mediaId: String) = "meme/create/$mediaId"
    fun editMeme(memeId: String) = "meme/edit/$memeId"
    fun collectionDetails(collectionId: String) = "collection/$collectionId"
    fun editCollection(collectionId: String) = "collection/edit/$collectionId"
    fun groupDetails(groupId: String) = "group/$groupId"
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun AppNavigation() {
    val container = LocalAppContainer.current
    val session by container.observeSessionUseCase().collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val route = currentBackStackEntry?.destination?.route

    val bottomDestinations = listOf(
        BottomDestination(Routes.Feed, "Главная", Icons.Rounded.Home),
        BottomDestination(Routes.Upload, "Загрузить", Icons.Rounded.AddCircleOutline),
        BottomDestination(Routes.MyProfile, "Профиль", Icons.Rounded.Person),
        BottomDestination(Routes.Settings, "Настройки", Icons.Rounded.Settings)
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (route in bottomDestinations.map { it.route }) {
                NavigationBar {
                    bottomDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = route == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(Routes.Feed) { saveState = true }
                                    restoreState = true
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(navController = navController, startDestination = Routes.Splash, modifier = Modifier.fillMaxSize()) {
            composable(Routes.Splash) {
                SplashScreen(
                    onAuthorized = {
                        navController.navigate(Routes.Feed) {
                            popUpTo(Routes.Splash) { inclusive = true }
                        }
                    },
                    onUnauthorized = {
                        navController.navigate(Routes.Auth) {
                            popUpTo(Routes.Splash) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.Auth) {
                AuthScreen(
                    onAuthenticated = {
                        navController.navigate(Routes.Feed) {
                            popUpTo(Routes.Auth) { inclusive = true }
                        }
                    },
                    onContinueAsGuest = {
                        navController.navigate(Routes.Feed) {
                            popUpTo(Routes.Auth) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.Feed) {
                FeedScreen(
                    padding = padding,
                    session = session,
                    onSearch = { navController.navigate(Routes.Search) },
                    onOpenMeme = { navController.navigate(Routes.memeDetails(it)) },
                    onRequireAuth = { navController.navigate(Routes.Auth) }
                )
            }
            composable(Routes.Search) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenUser = { navController.navigate(Routes.publicProfile(it)) },
                    onOpenCollection = { navController.navigate(Routes.collectionDetails(it)) },
                    onOpenMeme = { navController.navigate(Routes.memeDetails(it)) }
                )
            }
            composable(
                route = Routes.PublicProfile,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) { entry ->
                PublicProfileScreen(
                    userId = entry.arguments?.getString("userId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenMeme = { navController.navigate(Routes.memeDetails(it)) },
                    onOpenCollection = { navController.navigate(Routes.collectionDetails(it)) }
                )
            }
            composable(Routes.Upload) {
                UploadScreen(
                    padding = padding,
                    session = session,
                    onRequireAuth = { navController.navigate(Routes.Auth) },
                    onMediaReady = { navController.navigate(Routes.createMeme(it)) }
                )
            }
            composable(
                route = Routes.CreateMeme,
                arguments = listOf(navArgument("mediaId") { type = NavType.StringType })
            ) { entry ->
                CreateEditMemeScreen(
                    mediaId = entry.arguments?.getString("mediaId"),
                    memeId = null,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.navigate(Routes.memeDetails(it)) }
                )
            }
            composable(
                route = Routes.EditMeme,
                arguments = listOf(navArgument("memeId") { type = NavType.StringType })
            ) { entry ->
                CreateEditMemeScreen(
                    mediaId = null,
                    memeId = entry.arguments?.getString("memeId"),
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.navigate(Routes.memeDetails(it)) }
                )
            }
            composable(
                route = Routes.MemeDetails,
                arguments = listOf(navArgument("memeId") { type = NavType.StringType })
            ) { entry ->
                MemeDetailsScreen(
                    memeId = entry.arguments?.getString("memeId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.editMeme(it)) }
                )
            }
            composable(Routes.MyProfile) {
                MyProfileScreen(
                    padding = padding,
                    session = session,
                    onRequireAuth = { navController.navigate(Routes.Auth) },
                    onOpenMeme = { navController.navigate(Routes.memeDetails(it)) },
                    onOpenCollection = { navController.navigate(Routes.collectionDetails(it)) },
                    onCreateCollection = { navController.navigate(Routes.CreateCollection) },
                    onGroups = { navController.navigate(Routes.Groups) },
                    onInvitations = { navController.navigate(Routes.Invitations) }
                )
            }
            composable(Routes.CreateCollection) {
                CreateEditCollectionScreen(
                    collectionId = null,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.navigate(Routes.collectionDetails(it)) }
                )
            }
            composable(
                route = Routes.EditCollection,
                arguments = listOf(navArgument("collectionId") { type = NavType.StringType })
            ) { entry ->
                CreateEditCollectionScreen(
                    collectionId = entry.arguments?.getString("collectionId"),
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.navigate(Routes.collectionDetails(it)) }
                )
            }
            composable(
                route = Routes.CollectionDetails,
                arguments = listOf(navArgument("collectionId") { type = NavType.StringType })
            ) { entry ->
                CollectionDetailsScreen(
                    collectionId = entry.arguments?.getString("collectionId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.editCollection(it)) },
                    onOpenMeme = { navController.navigate(Routes.memeDetails(it)) }
                )
            }
            composable(Routes.Groups) {
                GroupsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenGroup = { navController.navigate(Routes.groupDetails(it)) },
                    onInvitations = { navController.navigate(Routes.Invitations) }
                )
            }
            composable(Routes.Invitations) {
                InvitationsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.GroupDetails,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
            ) { entry ->
                GroupDetailsScreen(
                    groupId = entry.arguments?.getString("groupId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenMeme = { navController.navigate(Routes.memeDetails(it)) }
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    padding = padding,
                    session = session,
                    onRequireAuth = { navController.navigate(Routes.Auth) },
                    onLoggedOut = {
                        navController.navigate(Routes.Auth) {
                            popUpTo(Routes.Feed) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
