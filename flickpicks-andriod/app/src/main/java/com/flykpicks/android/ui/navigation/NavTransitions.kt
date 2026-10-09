package com.flykpicks.android.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy

// iOS navigation-stack motion: a pushed screen slides in from the trailing edge while the one
// beneath drifts a third of the way out; back reverses it. Tab switches are instant, as on iOS.

private const val PUSH_MILLIS = 350
private val pushSpec = tween<IntOffset>(PUSH_MILLIS, easing = FastOutSlowInEasing)

/** The tab whose graph contains this destination, or null outside the main graphs. */
fun NavDestination.tab(): MainTab? =
    hierarchy.firstNotNullOfOrNull { dest -> MainTab.entries.firstOrNull { it.graphRoute == dest.route } }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean {
    val from = initialState.destination.tab()
    val to = targetState.destination.tab()
    return from != null && to != null && from != to
}

val pushEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (isTabSwitch()) EnterTransition.None
    else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, pushSpec)
}

val pushExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (isTabSwitch()) ExitTransition.None
    else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, pushSpec) { it / 3 }
}

val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (isTabSwitch()) EnterTransition.None
    else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, pushSpec) { it / 3 }
}

val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (isTabSwitch()) ExitTransition.None
    else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, pushSpec)
}
