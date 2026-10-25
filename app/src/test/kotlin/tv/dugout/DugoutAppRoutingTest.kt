package tv.dugout

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.dugout.profile.InMemoryProfileRepository
import tv.dugout.ui.theme.DugoutTheme

@RunWith(AndroidJUnit4::class)
class DugoutAppRoutingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun unknownProfileRouteReturnsToPicker() {
        lateinit var navController: NavHostController
        compose.setContent {
            navController = rememberNavController()
            DugoutTheme { DugoutApp(InMemoryProfileRepository(), navController) }
        }

        compose.runOnUiThread { navController.navigate(homeRoute("nobody")) }
        compose.waitForIdle()

        compose.onNodeWithText("Who's up to bat?").assertIsDisplayed()
    }
}
