package tv.dugout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import tv.dugout.home.HomePlaceholderScreen
import tv.dugout.parent.ParentPlaceholderScreen
import tv.dugout.picker.ProfilePickerScreen
import tv.dugout.picker.ProfilePickerViewModel
import tv.dugout.profile.ProfileRepository

const val PICKER_ROUTE = "picker"
const val PARENT_ROUTE = "parent"
private const val PROFILE_ID_ARG = "profileId"

fun homeRoute(profileId: String): String = "home/$profileId"

@Composable
fun DugoutApp(
    repository: ProfileRepository,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = PICKER_ROUTE) {
        composable(PICKER_ROUTE) {
            val pickerViewModel: ProfilePickerViewModel =
                viewModel(factory = viewModelFactory { initializer { ProfilePickerViewModel(repository) } })
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
        composable("home/{$PROFILE_ID_ARG}") { entry ->
            val profile = entry.arguments?.getString(PROFILE_ID_ARG)?.let(repository::profile)
            if (profile == null) {
                LaunchedEffect(Unit) { navController.popBackStack(PICKER_ROUTE, inclusive = false) }
            } else {
                HomePlaceholderScreen(profile.name)
            }
        }
        composable(PARENT_ROUTE) { ParentPlaceholderScreen() }
    }
}
