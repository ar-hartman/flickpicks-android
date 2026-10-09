package com.flykpicks.android.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.flykpicks.android.ui.components.DecoCTAButton
import com.flykpicks.android.ui.components.DecoCTAVariant
import com.flykpicks.android.ui.gallery.ComponentGallery
import com.flykpicks.android.ui.navigation.NavRoutes.Companion.ARG_DISPUTE_ID
import com.flykpicks.android.ui.navigation.NavRoutes.Companion.ARG_FLYKS_ID
import com.flykpicks.android.ui.navigation.NavRoutes.Companion.ARG_ID
import com.flykpicks.android.ui.navigation.NavRoutes.Companion.ARG_ORDER_ID
import com.flykpicks.android.ui.navigation.NavRoutes.Companion.ARG_SEASON
import com.flykpicks.android.ui.navigation.NavRoutes.Companion.ARG_SOURCE
import com.flykpicks.android.ui.navigation.NavRoutes.Companion.ARG_TITLE_ID

/** Shell actions the Phase 3 placeholders call. Goes away as the real screens land. */
data class ShellPlaceholderActions(
    val signOut: () -> Unit,
    val showToast: (String) -> Unit,
)

private fun NavBackStackEntry.arg(name: String): String? = arguments?.getString(name)

fun NavGraphBuilder.libraryGraph(nav: NavController) {
    navigation(startDestination = MainTab.Library.startRoute, route = MainTab.Library.graphRoute) {
        composable(NavRoutes.Library.route) {
            TabRootPlaceholder(
                title = "Library",
                phase = 5,
                links = listOf(
                    PlaceholderLink("The Third Man", { nav.navigate(NavRoutes.LibraryDetail.build("title-1")) }, Icons.Outlined.Movie),
                    PlaceholderLink("Twin Peaks", { nav.navigate(NavRoutes.LibraryDetail.build("title-2")) }, Icons.Outlined.Tv),
                ),
            )
        }
        composable(NavRoutes.LibraryDetail.route) { entry ->
            val titleId = entry.arg(ARG_TITLE_ID)
            PushedPlaceholder(
                title = "Library Item",
                phase = 5,
                onBack = nav::popBackStack,
                args = listOf(ARG_TITLE_ID to titleId),
                links = listOf(
                    PlaceholderLink("Season 1", { nav.navigate(NavRoutes.TVSeasonDetail.build(titleId.orEmpty(), 1)) }),
                    PlaceholderLink("Season 2", { nav.navigate(NavRoutes.TVSeasonDetail.build(titleId.orEmpty(), 2)) }),
                ),
            )
        }
        composable(
            NavRoutes.TVSeasonDetail.route,
            arguments = listOf(navArgument(ARG_SEASON) { type = NavType.IntType }),
        ) { entry ->
            PushedPlaceholder(
                title = "Season ${entry.arguments?.getInt(ARG_SEASON)}",
                phase = 5,
                onBack = nav::popBackStack,
                args = listOf(ARG_TITLE_ID to entry.arg(ARG_TITLE_ID), ARG_SEASON to entry.arguments?.getInt(ARG_SEASON)?.toString()),
            )
        }
    }
}

fun NavGraphBuilder.searchGraph(nav: NavController) {
    navigation(startDestination = MainTab.Search.startRoute, route = MainTab.Search.graphRoute) {
        composable(NavRoutes.Search.route) {
            TabRootPlaceholder(
                title = "Discover",
                phase = 6,
                links = listOf(
                    PlaceholderLink("Brazil (popular movies)", { nav.navigate(NavRoutes.SearchDetail.build("m1", "recommendation.popularMovies")) }, Icons.Outlined.Movie),
                    PlaceholderLink("Columbo (search result)", { nav.navigate(NavRoutes.SearchDetail.build("t 1/x", "search.results")) }, Icons.Outlined.Tv),
                ),
            )
        }
        composable(NavRoutes.SearchDetail.route) { entry ->
            PushedPlaceholder(
                title = "Title Detail",
                phase = 6,
                onBack = nav::popBackStack,
                args = listOf(ARG_FLYKS_ID to entry.arg(ARG_FLYKS_ID), ARG_SOURCE to entry.arg(ARG_SOURCE)),
            )
        }
    }
}

fun NavGraphBuilder.profileGraph(nav: NavController, actions: ShellPlaceholderActions) {
    fun link(label: String, route: String, icon: ImageVector) =
        PlaceholderLink(label, { nav.navigate(route) }, icon)

    fun NavGraphBuilder.pushed(route: String, title: String, phase: Int, vararg links: PlaceholderLink) {
        composable(route) { PushedPlaceholder(title, phase, nav::popBackStack, links = links.toList()) }
    }

    navigation(startDestination = MainTab.Profile.startRoute, route = MainTab.Profile.graphRoute) {
        composable(NavRoutes.Profile.route) {
            TabRootPlaceholder(
                title = "Profile",
                phase = 7,
                links = listOf(
                    link("Privacy & Security", NavRoutes.Security.route, Icons.Outlined.Security),
                    link("Notifications", NavRoutes.Notifications.route, Icons.Outlined.Notifications),
                    link("Payment Wallet", NavRoutes.PaymentWallet.route, Icons.Outlined.CreditCard),
                    link("Shipping Addresses", NavRoutes.AddressList.route, Icons.Outlined.Inventory2),
                    link("Membership", NavRoutes.Membership.route, Icons.Outlined.Star),
                    link("Orders & Rentals", NavRoutes.OrderHistory.route, Icons.Outlined.ShoppingBag),
                    link("Disputes", NavRoutes.DisputeList.route, Icons.Outlined.ReportProblem),
                    link("Seller Dashboard", NavRoutes.SellerDashboard.route, Icons.Outlined.BarChart),
                    link("Collection Insights", NavRoutes.CollectionInsights.route, Icons.Outlined.PieChart),
                    link("Help & Support", NavRoutes.HelpSupport.route, Icons.Outlined.SupportAgent),
                    link("Invite Friends", NavRoutes.Referral.route, Icons.Outlined.People),
                    link("Component gallery (Phase 2)", ComponentGalleryRoute, Icons.Outlined.Widgets),
                    PlaceholderLink("Show a toast", { actions.showToast("Session signed out") }, Icons.Outlined.Badge),
                ),
            ) {
                DecoCTAButton(
                    "Log Out",
                    onClick = actions.signOut,
                    modifier = Modifier.padding(top = 8.dp),
                    leadingIcon = Icons.AutoMirrored.Outlined.Logout,
                    variant = DecoCTAVariant.Danger,
                )
            }
        }

        pushed(
            NavRoutes.Security.route, "Security", 8,
            link("Change Password", NavRoutes.ChangePassword.route, Icons.Outlined.Security),
            link("Set Up Two-Factor", NavRoutes.MFASetup.route, Icons.Outlined.Security),
            link("Active Sessions", NavRoutes.ActiveSessions.route, Icons.Outlined.Security),
        )
        pushed(NavRoutes.ChangePassword.route, "Change Password", 8)
        pushed(NavRoutes.MFASetup.route, "Two-Factor Setup", 8)
        pushed(NavRoutes.ActiveSessions.route, "Active Sessions", 8)
        pushed(NavRoutes.Notifications.route, "Notifications", 11)
        pushed(NavRoutes.PaymentWallet.route, "Payment Wallet", 9)
        pushed(
            NavRoutes.AddressList.route, "Addresses", 9,
            link("New Address", NavRoutes.AddressForm.build(), Icons.Outlined.Inventory2),
            link("Edit 1 Main St", NavRoutes.AddressForm.build("addr-1"), Icons.Outlined.Inventory2),
        )
        composable(
            NavRoutes.AddressForm.route,
            arguments = listOf(navArgument(ARG_ID) { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            val id = entry.arg(ARG_ID)
            PushedPlaceholder(if (id == null) "New Address" else "Edit Address", 9, nav::popBackStack, args = listOf(ARG_ID to id))
        }
        pushed(NavRoutes.Membership.route, "Membership", 9)
        pushed(
            NavRoutes.OrderHistory.route, "Orders & Rentals", 10,
            link("Order #1042", NavRoutes.OrderDetail.build("order-1042"), Icons.Outlined.ShoppingBag),
        )
        composable(NavRoutes.OrderDetail.route) { entry ->
            PushedPlaceholder("Order Detail", 10, nav::popBackStack, args = listOf(ARG_ORDER_ID to entry.arg(ARG_ORDER_ID)))
        }
        pushed(
            NavRoutes.DisputeList.route, "Disputes", 10,
            link("File New Dispute", NavRoutes.FileDispute.route, Icons.Outlined.ReportProblem),
            link("Dispute #77", NavRoutes.DisputeDetail.build("dispute-77"), Icons.Outlined.ReportProblem),
        )
        pushed(NavRoutes.FileDispute.route, "File a Dispute", 10)
        composable(NavRoutes.DisputeDetail.route) { entry ->
            PushedPlaceholder("Dispute", 10, nav::popBackStack, args = listOf(ARG_DISPUTE_ID to entry.arg(ARG_DISPUTE_ID)))
        }
        pushed(NavRoutes.SellerDashboard.route, "Seller Dashboard", 11)
        pushed(NavRoutes.CollectionInsights.route, "Collection Insights", 11)
        pushed(NavRoutes.Referral.route, "Invite Friends", 11)
        pushed(NavRoutes.HelpSupport.route, "Help & Support", 7)
        composable(ComponentGalleryRoute) {
            ComponentGallery(toast = null, onShowToast = actions.showToast)
        }
    }
}

/** Phase 2's component gallery, kept reachable from the placeholder Profile screen. */
private const val ComponentGalleryRoute = "profile/component_gallery"
