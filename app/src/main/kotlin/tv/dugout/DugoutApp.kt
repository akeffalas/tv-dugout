package tv.dugout

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import tv.dugout.home.HomeScreen
import tv.dugout.home.HomeViewModel
import tv.dugout.library.Skill
import tv.dugout.parent.ParentPlaceholderScreen
import tv.dugout.picker.ProfilePickerScreen
import tv.dugout.picker.ProfilePickerViewModel
import tv.dugout.player.PlayerScreen
import tv.dugout.player.PlayerViewModel
import tv.dugout.upnext.UpNextScreen
import tv.dugout.upnext.nextVideo

const val PICKER_ROUTE = "picker"
const val PARENT_ROUTE = "parent"
private const val PROFILE_ID_ARG = "profileId"
private const val VIDEO_ID_ARG = "videoId"

/** Set on Home's back stack entry by the player and Up next, so Home can put focus back. */
const val RESULT_LAST_VIDEO = "lastVideoId"
const val RESULT_OPEN_SKILL = "openSkill"

fun homeRoute(profileId: String): String = "home/$profileId"

private const val PLAYER_ROUTE_PATTERN = "player/{$PROFILE_ID_ARG}/{$VIDEO_ID_ARG}"
private const val UP_NEXT_ROUTE_PATTERN = "upnext/{$PROFILE_ID_ARG}/{$VIDEO_ID_ARG}"

fun upNextRoute(
    profileId: String,
    finishedVideoId: String,
): String = "upnext/$profileId/$finishedVideoId"

fun playerRoute(
    profileId: String,
    videoId: String,
): String = "player/$profileId/$videoId"

@Composable
fun DugoutApp(
    graph: AppGraph,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = PICKER_ROUTE) {
        composable(PICKER_ROUTE) {
            val pickerViewModel = viewModel { ProfilePickerViewModel(graph.profiles, graph.history) }
            val state by pickerViewModel.uiState.collectAsStateWithLifecycle()

            // The picker stays on screen and focusable while it fades out, so a fast second press
            // could push another screen. Only navigate while the picker is the current destination.
            fun fromPicker(action: () -> Unit) {
                if (navController.currentDestination?.route == PICKER_ROUTE) action()
            }
            ProfilePickerScreen(
                state = state,
                onProfileSelected = { profileId ->
                    fromPicker {
                        pickerViewModel.onProfileSelected(profileId)
                        navController.navigate(homeRoute(profileId)) { launchSingleTop = true }
                    }
                },
                onParentSelected = {
                    fromPicker {
                        pickerViewModel.onParentSelected()
                        navController.navigate(PARENT_ROUTE) { launchSingleTop = true }
                    }
                },
            )
        }
        composable("home/{$PROFILE_ID_ARG}") { entry -> HomeDestination(graph, navController, entry) }
        composable(PLAYER_ROUTE_PATTERN) { entry -> PlayerDestination(graph, navController, entry) }
        composable(UP_NEXT_ROUTE_PATTERN) { entry -> UpNextDestination(graph, navController, entry) }
        composable(PARENT_ROUTE) { ParentPlaceholderScreen() }
    }
}

@Composable
private fun HomeDestination(
    graph: AppGraph,
    navController: NavHostController,
    entry: NavBackStackEntry,
) {
    val profile = entry.arguments?.getString(PROFILE_ID_ARG)?.let(graph.profiles::profile)
    if (profile == null) {
        LaunchedEffect(Unit) { navController.popBackStack(PICKER_ROUTE, inclusive = false) }
        return
    }
    val homeViewModel = viewModel { HomeViewModel(profile.id, graph) }
    val state by homeViewModel.uiState.collectAsStateWithLifecycle()
    val returned by entry.savedStateHandle.getStateFlow<String?>(RESULT_LAST_VIDEO, null).collectAsStateWithLifecycle()
    LaunchedEffect(returned) {
        val videoId = returned ?: return@LaunchedEffect
        val openSkill = entry.savedStateHandle.get<String>(RESULT_OPEN_SKILL)?.let(Skill::valueOf)
        entry.savedStateHandle.remove<String>(RESULT_OPEN_SKILL)
        entry.savedStateHandle[RESULT_LAST_VIDEO] = null
        homeViewModel.onReturned(videoId, openSkill)
    }
    HomeScreen(
        state = state,
        onTabSelected = homeViewModel::onTabSelected,
        onVideoSelected = { tab, row, videoId ->
            // Only once Home has fully arrived: a press that lands during the transition in from the
            // picker, or out to a player, must not open a video.
            if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                homeViewModel.onVideoSelected(tab, row, videoId)
                navController.navigate(playerRoute(profile.id, videoId))
            }
        },
    )
}

/** Hands focus information to Home, then leaves the current screen. */
fun NavHostController.returnHome(
    videoId: String,
    openSkill: Skill?,
) {
    previousBackStackEntry?.savedStateHandle?.let { home ->
        home[RESULT_OPEN_SKILL] = openSkill?.name
        home[RESULT_LAST_VIDEO] = videoId
    }
    popBackStack()
}

@Composable
private fun PlayerDestination(
    graph: AppGraph,
    navController: NavHostController,
    entry: NavBackStackEntry,
) {
    val profileId = entry.arguments?.getString(PROFILE_ID_ARG)
    val video = entry.arguments?.getString(VIDEO_ID_ARG)?.let(graph.library::video)
    if (profileId == null || video == null) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }
    val playerViewModel = viewModel { PlayerViewModel(profileId, video, graph.history, graph.clock) }
    val state by playerViewModel.uiState.collectAsStateWithLifecycle()
    val ended by playerViewModel.ended.collectAsStateWithLifecycle()
    LaunchedEffect(ended) {
        if (ended && navController.currentBackStackEntry == entry) {
            navController.navigate(
                upNextRoute(profileId, video.id),
            ) { popUpTo(PLAYER_ROUTE_PATTERN) { inclusive = true } }
        }
    }
    val leave = {
        if (navController.currentBackStackEntry == entry) {
            playerViewModel.onLeave()
            navController.returnHome(video.id, openSkill = null)
        }
    }
    BackHandler(onBack = leave)
    PlayerScreen(
        state = state,
        surface = { modifier -> graph.playerSurface.Content(modifier, playerViewModel::attach) },
        onPlayPause = playerViewModel::onPlayPause,
        onBackTenSeconds = playerViewModel::onBackTenSeconds,
        onSlowMoToggle = playerViewModel::onSlowMoToggle,
        onBack = leave,
    )
}

@Composable
private fun UpNextDestination(
    graph: AppGraph,
    navController: NavHostController,
    entry: NavBackStackEntry,
) {
    val profileId = entry.arguments?.getString(PROFILE_ID_ARG)
    val finished = entry.arguments?.getString(VIDEO_ID_ARG)?.let(graph.library::video)
    if (profileId == null || finished == null) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }
    val next =
        remember(entry) {
            val skillVideos =
                graph.profileVideos
                    .videoIds(profileId)
                    .mapNotNull(graph.library::video)
                    .filter { it.skill == finished.skill }
            nextVideo(skillVideos, finished.id, graph.history.progress(profileId).value)
        }

    fun leave(openSkill: Skill?) {
        if (navController.currentBackStackEntry == entry) navController.returnHome(finished.id, openSkill)
    }
    BackHandler { leave(openSkill = null) }
    UpNextScreen(
        finished = finished,
        next = next,
        onPlayNext = {
            if (next != null && navController.currentBackStackEntry == entry) {
                navController.navigate(playerRoute(profileId, next.id)) {
                    popUpTo(UP_NEXT_ROUTE_PATTERN) {
                        inclusive =
                            true
                    }
                }
            }
        },
        onBackToSkill = { leave(openSkill = finished.skill) },
    )
}
