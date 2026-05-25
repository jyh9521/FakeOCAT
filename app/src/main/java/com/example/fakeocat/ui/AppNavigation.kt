package com.example.fakeocat.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.fakeocat.ui.screens.BookmarksScreen
import com.example.fakeocat.ui.screens.ChatScreen
import com.example.fakeocat.ui.screens.HistoryScreen
import com.example.fakeocat.ui.screens.SettingsScreen
import com.example.fakeocat.ui.viewmodel.ChatViewModel

sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object History : Screen("history")
    object Bookmarks : Screen("bookmarks")
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    chatViewModel: ChatViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        composable(Screen.Chat.route) {
            ChatScreen(
                viewModel = chatViewModel,
                onNavigateToHistory = { navController.navigate(Screen.History.route) { launchSingleTop = true } },
                onNavigateToBookmarks = { navController.navigate(Screen.Bookmarks.route) { launchSingleTop = true } },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } }
            )
        }
        composable(Screen.History.route) {
            HistoryScreen(
                viewModel = chatViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Bookmarks.route) {
            BookmarksScreen(
                viewModel = chatViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToChat = { navController.popBackStack(Screen.Chat.route, false) }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = chatViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
