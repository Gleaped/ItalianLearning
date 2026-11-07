package com.gleaped.italianlearning.ui.navigation

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gleaped.italianlearning.R
import com.gleaped.italianlearning.domain.reminders.ReminderTarget
import com.gleaped.italianlearning.ui.challenge.ChallengeScreen
import com.gleaped.italianlearning.ui.course.CourseMapScreen
import com.gleaped.italianlearning.ui.course.UnitScreen
import com.gleaped.italianlearning.ui.home.HomeScreen
import com.gleaped.italianlearning.ui.lesson.LessonScreen
import com.gleaped.italianlearning.ui.review.ReviewScreen
import com.gleaped.italianlearning.ui.settings.SettingsScreen

object Routes {
    const val TODAY = "today"
    const val COURSE = "course"
    const val REVIEW = "review"
    const val SETTINGS = "settings"
    const val UNIT = "unit/{unitId}"
    const val LESSON = "lesson/{unitId}/{lessonId}"
    const val CHALLENGE = "challenge/{unitId}/{tipId}"

    fun unit(id: String) = "unit/${Uri.encode(id)}"
    fun lesson(unitId: String, lessonId: String) = "lesson/${Uri.encode(unitId)}/${Uri.encode(lessonId)}"
    fun challenge(unitId: String, tipId: String) = "challenge/${Uri.encode(unitId)}/${Uri.encode(tipId)}"
}

/** A notification target waiting to be opened; [id] makes repeated taps distinct. */
data class PendingDeepLink(val target: ReminderTarget, val id: Long)

private data class TopLevel(val route: String, val label: String, @param:DrawableRes val icon: Int)

private val topLevel = listOf(
    TopLevel(Routes.TODAY, "Today", R.drawable.ic_today),
    TopLevel(Routes.COURSE, "Course", R.drawable.ic_map),
    TopLevel(Routes.REVIEW, "Review", R.drawable.ic_cards),
    TopLevel(Routes.SETTINGS, "Settings", R.drawable.ic_settings),
)

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * @param deepLink set when a reminder notification is tapped (cold start or while running);
 * the app opens its target and then calls [onDeepLinkHandled].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppNavigation(deepLink: PendingDeepLink?, onDeepLinkHandled: (PendingDeepLink) -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = topLevel.any { it.route == currentRoute }


    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevel.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = { nav.navigateTopLevel(item.route) },
                            icon = { Icon(painterResource(item.icon), contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            NavHost(navController = nav, startDestination = Routes.TODAY) {
                composable(Routes.TODAY) {
                    HomeScreen(
                        onStartLesson = { u, l -> nav.navigate(Routes.lesson(u, l)) },
                        onReview = { nav.navigateTopLevel(Routes.REVIEW) },
                        onCourse = { nav.navigateTopLevel(Routes.COURSE) },
                    )
                }
                composable(Routes.COURSE) {
                    CourseMapScreen(onOpenUnit = { nav.navigate(Routes.unit(it)) })
                }
                composable(Routes.REVIEW) {
                    ReviewScreen(onDone = { nav.navigateTopLevel(Routes.TODAY) })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen()
                }
                composable(Routes.UNIT) { entry ->
                    val unitId = entry.arguments?.getString("unitId").orEmpty()
                    UnitScreen(
                        unitId = unitId,
                        onBack = { nav.popBackStack() },
                        onStartLesson = { u, l -> nav.navigate(Routes.lesson(u, l)) },
                        onOpenUnit = { nav.navigate(Routes.unit(it)) },
                        onOpenChallenge = { u, t -> nav.navigate(Routes.challenge(u, t)) },
                    )
                }
                composable(Routes.CHALLENGE) { entry ->
                    val unitId = entry.arguments?.getString("unitId").orEmpty()
                    val tipId = entry.arguments?.getString("tipId").orEmpty()
                    ChallengeScreen(
                        unitId = unitId,
                        tipId = tipId,
                        onClose = {
                            if (!nav.popBackStack()) nav.navigateTopLevel(Routes.TODAY)
                        },
                        onOpenLesson = { u, l ->
                            nav.navigate(Routes.lesson(u, l)) {
                                popUpTo(Routes.CHALLENGE) { inclusive = true }
                            }
                        },
                        onOpenUnit = { u ->
                            nav.navigate(Routes.unit(u)) {
                                popUpTo(Routes.CHALLENGE) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Routes.LESSON) { entry ->
                    val unitId = entry.arguments?.getString("unitId").orEmpty()
                    val lessonId = entry.arguments?.getString("lessonId").orEmpty()
                    LessonScreen(
                        unitId = unitId,
                        lessonId = lessonId,
                        onClose = { nav.popBackStack() },
                        onOpenLesson = { u, l ->
                            nav.navigate(Routes.lesson(u, l)) {
                                popUpTo(Routes.LESSON) { inclusive = true }
                            }
                        },
                        onReview = {
                            nav.popBackStack()
                            nav.navigateTopLevel(Routes.REVIEW)
                        },
                    )
                }
            }
            // After NavHost in the same composition, so the graph is set before navigating.
            LaunchedEffect(deepLink) {
                val link = deepLink ?: return@LaunchedEffect
                when (val target = link.target) {
                    ReminderTarget.Today -> nav.navigateTopLevel(Routes.TODAY)
                    ReminderTarget.Review -> nav.navigateTopLevel(Routes.REVIEW)
                    is ReminderTarget.Challenge -> nav.navigate(Routes.challenge(target.unitId, target.tipId)) {
                        // A newer challenge replaces one that is still open.
                        popUpTo(Routes.CHALLENGE) { inclusive = true }
                    }
                }
                onDeepLinkHandled(link)
            }
        }
    }
}
