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
import tv.dugout.parent.LockStatus
import tv.dugout.parent.pin.PinFlow
import tv.dugout.parent.pin.PinFlowScreen
import tv.dugout.parent.pin.PinFlowViewModel
import tv.dugout.parent.settings.ParentSettingsScreen
import tv.dugout.parent.settings.ParentSettingsViewModel
import tv.dugout.parent.settings.ParentTab
import tv.dugout.parent.settings.ProgressPane
import tv.dugout.picker.ProfilePickerScreen
import tv.dugout.picker.ProfilePickerViewModel
import tv.dugout.player.PlayerScreen
import tv.dugout.player.PlayerViewModel
import tv.dugout.setup.SetupScreen
import tv.dugout.setup.SetupStep
import tv.dugout.setup.SetupViewModel
import tv.dugout.upnext.UpNextScreen
import tv.dugout.upnext.nextVideo

const val PICKER_ROUTE = "picker"
const val PARENT_ROUTE = "parent"
const val SETUP_ROUTE = "setup"
const val FIRST_PIN_ROUTE = "first-pin"
const val PARENT_SETTINGS_ROUTE = "parent/settings"
const val CHANGE_PIN_ROUTE = "parent/change-pin"
const val PARENT_SETUP_ROUTE = "parent/setup"
private const val PROFILE_ID_ARG = "profileId"
private const val VIDEO_ID_ARG = "videoId"

/** Set on Home's back stack entry by the player and Up next, so Home can put focus back. */
const val RESULT_LAST_VIDEO = "lastVideoId"
const val RESULT_OPEN_SKILL = "openSkill"

/** Set on the picker's back stack entry when the wizard adds a child, so focus lands on them. */
const val RESULT_ADDED_PROFILE = "addedProfileId"

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
    // A fresh install has no children yet, so it starts by choosing a PIN (if none) and adding one.
    val start =
        remember {
            when {
                graph.profiles.profiles.value
                    .isNotEmpty() -> PICKER_ROUTE

                graph.lock.status.value == LockStatus.NO_PIN -> FIRST_PIN_ROUTE

                else -> SETUP_ROUTE
            }
        }
    NavHost(navController = navController, startDestination = start) {
        composable(PICKER_ROUTE) { entry ->
            val pickerViewModel =
                viewModel { ProfilePickerViewModel(graph.profiles, graph.profileVideos, graph.history, graph.lock) }
            val state by pickerViewModel.uiState.collectAsStateWithLifecycle()
            val added by entry.savedStateHandle
                .getStateFlow<String?>(RESULT_ADDED_PROFILE, null)
                .collectAsStateWithLifecycle()
            LaunchedEffect(added) {
                val profileId = added ?: return@LaunchedEffect
                entry.savedStateHandle[RESULT_ADDED_PROFILE] = null
                pickerViewModel.onProfileAdded(profileId)
            }

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
                onAddSelected = {
                    fromPicker {
                        pickerViewModel.onAddSelected()
                        navController.navigate(SETUP_ROUTE) { launchSingleTop = true }
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
        composable(FIRST_PIN_ROUTE) { entry ->
            PinDestination(graph, navController, entry, PinFlow.FIRST_RUN) {
                navController.navigate(SETUP_ROUTE) { popUpTo(FIRST_PIN_ROUTE) { inclusive = true } }
            }
        }
        composable(PARENT_ROUTE) { entry ->
            PinDestination(graph, navController, entry, PinFlow.UNLOCK) {
                navController.navigate(PARENT_SETTINGS_ROUTE) { popUpTo(PARENT_ROUTE) { inclusive = true } }
            }
        }
        composable(CHANGE_PIN_ROUTE) { entry ->
            PinDestination(graph, navController, entry, PinFlow.CHANGE) { navController.popBackStack() }
        }
        composable(PARENT_SETTINGS_ROUTE) { entry -> ParentSettingsDestination(graph, navController, entry) }
        composable(SETUP_ROUTE) { entry -> SetupDestination(graph, navController, entry, fromParent = false) }
        composable(PARENT_SETUP_ROUTE) { entry -> SetupDestination(graph, navController, entry, fromParent = true) }
    }
}

/** The PIN screens for [flow]; [onFinished] runs once, while this screen is current, when the flow ends. */
@Composable
private fun PinDestination(
    graph: AppGraph,
    navController: NavHostController,
    entry: NavBackStackEntry,
    flow: PinFlow,
    onFinished: () -> Unit,
) {
    val pinViewModel = viewModel { PinFlowViewModel(flow, graph.lock) }
    val state by pinViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) {
        if (state.finished && navController.currentBackStackEntry == entry) onFinished()
    }
    // Otherwise Back leaves the PIN screens: to the screen beneath, or out of the app on a fresh install.
    BackHandler(enabled = state.canStepBack || state.busy) {
        if (navController.currentBackStackEntry == entry) pinViewModel.onBack()
    }
    PinFlowScreen(
        state = state,
        onDigit = pinViewModel::onDigit,
        onDelete = pinViewModel::onDelete,
        onOk = pinViewModel::onOk,
        onForgot = pinViewModel::onForgot,
        onCodeChanged = pinViewModel::onCodeChanged,
        onCodeSubmitted = pinViewModel::onCodeSubmitted,
        onSaved = pinViewModel::onSaved,
    )
}

@Composable
private fun ParentSettingsDestination(
    graph: AppGraph,
    navController: NavHostController,
    entry: NavBackStackEntry,
) {
    val settingsViewModel = viewModel { ParentSettingsViewModel(graph) }
    val state by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val added by entry.savedStateHandle
        .getStateFlow<String?>(RESULT_ADDED_PROFILE, null)
        .collectAsStateWithLifecycle()
    LaunchedEffect(added) {
        val profileId = added ?: return@LaunchedEffect
        entry.savedStateHandle[RESULT_ADDED_PROFILE] = null
        settingsViewModel.onReturnedWithAdded(profileId)
    }

    fun fromSettings(action: () -> Unit) {
        if (navController.currentBackStackEntry == entry) action()
    }
    ParentSettingsScreen(
        state = state,
        onChildFocused = settingsViewModel::onChildFocused,
        onTabFocused = settingsViewModel::onTabFocused,
        onAddPlayer = {
            fromSettings {
                settingsViewModel.onAddPlayerSelected()
                navController.navigate(PARENT_SETUP_ROUTE)
            }
        },
        onChangePin = {
            fromSettings {
                settingsViewModel.onChangePinSelected()
                navController.navigate(CHANGE_PIN_ROUTE)
            }
        },
    ) { child, tab ->
        when (tab) {
            ParentTab.PROGRESS -> {
                ProgressPane(
                    state = state,
                    childName = child.name,
                    onSkillFocused = settingsViewModel::onProgressSkillFocused,
                    onResetVideo = settingsViewModel::onResetVideoRequested,
                    onResetSkill = settingsViewModel::onResetSkillRequested,
                    onConfirm = settingsViewModel::onConfirm,
                    onCancel = settingsViewModel::onCancelConfirm,
                )
            }

            else -> {
                Unit
            }
        }
    }
}

/** The wizard; [fromParent] when opened from parent settings, so finishing returns there. */
@Composable
private fun SetupDestination(
    graph: AppGraph,
    navController: NavHostController,
    entry: NavBackStackEntry,
    fromParent: Boolean,
) {
    val setupViewModel = viewModel { SetupViewModel(graph.profiles, graph.library) }
    val state by setupViewModel.uiState.collectAsStateWithLifecycle()

    // Only while the wizard is the current screen, so a fast double press cannot navigate twice.
    fun fromSetup(action: () -> Unit) {
        if (navController.currentBackStackEntry == entry) action()
    }

    // Leaves the wizard for the picker, which may not exist yet on a fresh install, focusing [addedId].
    fun toPicker(addedId: String) {
        if (!navController.popBackStack(PICKER_ROUTE, inclusive = false)) {
            navController.navigate(PICKER_ROUTE) { popUpTo(SETUP_ROUTE) { inclusive = true } }
        }
        navController.getBackStackEntry(PICKER_ROUTE).savedStateHandle[RESULT_ADDED_PROFILE] = addedId
    }

    // Back from the ready screen shows the new child where the wizard was opened from.
    fun leaveReady(addedId: String) {
        if (fromParent) {
            navController.popBackStack(PARENT_SETTINGS_ROUTE, inclusive = false)
            navController.getBackStackEntry(PARENT_SETTINGS_ROUTE).savedStateHandle[RESULT_ADDED_PROFILE] = addedId
        } else {
            toPicker(addedId)
        }
    }

    // On the first step and the ready screen, Back is left to the NavHost or, on a fresh install, the activity.
    BackHandler(enabled = state.step != SetupStep.NAME) {
        fromSetup {
            val saved = state.saved
            if (state.step == SetupStep.READY && saved != null) leaveReady(saved.id) else setupViewModel.onBack()
        }
    }
    SetupScreen(
        state = state,
        onNameChanged = setupViewModel::onNameChanged,
        onNameSubmitted = setupViewModel::onNameSubmitted,
        onAgeSelected = setupViewModel::onAgeSelected,
        onLevelSelected = setupViewModel::onLevelSelected,
        onSkillToggled = setupViewModel::onSkillToggled,
        onSkillsSubmitted = setupViewModel::onSkillsSubmitted,
        onVideoSkillFocused = setupViewModel::onVideoSkillFocused,
        onVideoToggled = setupViewModel::onVideoToggled,
        onDone = setupViewModel::onDone,
        onStartWatching = {
            val saved = state.saved
            if (saved != null) {
                fromSetup {
                    toPicker(saved.id)
                    navController.navigate(homeRoute(saved.id))
                }
            }
        },
        onAddAnother = {
            val saved = state.saved
            if (saved != null) {
                // A fresh wizard over the picker, so Back on its first step returns there even on a fresh install.
                fromSetup {
                    if (fromParent) {
                        leaveReady(saved.id)
                        navController.navigate(PARENT_SETUP_ROUTE)
                    } else {
                        toPicker(saved.id)
                        navController.navigate(SETUP_ROUTE)
                    }
                }
            }
        },
    )
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
