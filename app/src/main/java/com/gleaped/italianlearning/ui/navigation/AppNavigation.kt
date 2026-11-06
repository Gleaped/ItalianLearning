package com.gleaped.italianlearning.ui.navigation

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

    fun unit(id: String) = "unit/$id"
    fun lesson(unitId: String, lessonId: String) = "lesson/$unitId/$lessonId"
}

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
 * @param openTodaySignal incremented when a reminder notification is tapped while the
 * app is already running; the app then jumps to Today.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppNavigation(openTodaySignal: Int) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = topLevel.any { it.route == currentRoute }

    LaunchedEffect(openTodaySignal) {
        if (openTodaySignal > 0) nav.navigateTopLevel(Routes.TODAY)
    }

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
        }
    }
}
