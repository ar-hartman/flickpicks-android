package com.flykpicks.android.ui.navigation

import android.net.Uri

/**
 * Every destination, as string routes (blueprint §4.2, plan D7). Routes with arguments have a
 * `build()` that URL-encodes them.
 *
 * Differences from §4.2, following the iOS screens (plan D21): the library detail is keyed by
 * title id and has a TV-season child; Change Password and File Dispute are screens; MFA setup
 * takes no method (iOS picks it on the screen); there's no Trusted Devices screen (iOS shows
 * trusted devices inside Active Sessions).
 */
sealed class NavRoutes(val route: String) {
    // Auth
    data object Login : NavRoutes("auth/login")
    data object Register : NavRoutes("auth/register")
    data object MFAChallenge : NavRoutes("auth/mfa")
    data object ForgotPassword : NavRoutes("auth/forgot_password")

    // Library tab
    data object Library : NavRoutes("library")
    data object LibraryDetail : NavRoutes("library/detail/{$ARG_TITLE_ID}") {
        fun build(titleId: String) = "library/detail/${titleId.encoded()}"
    }
    data object TVSeasonDetail : NavRoutes("library/detail/{$ARG_TITLE_ID}/season/{$ARG_SEASON}") {
        fun build(titleId: String, seasonNumber: Int) = "library/detail/${titleId.encoded()}/season/$seasonNumber"
    }

    // Search tab
    data object Search : NavRoutes("search")
    data object SearchDetail : NavRoutes("search/detail/{$ARG_FLYKS_ID}/{$ARG_SOURCE}") {
        fun build(flyksId: String, source: String) = "search/detail/${flyksId.encoded()}/${source.encoded()}"
    }

    // Profile tab
    data object Profile : NavRoutes("profile")
    data object Security : NavRoutes("profile/security")
    data object ChangePassword : NavRoutes("profile/security/change_password")
    data object MFASetup : NavRoutes("profile/security/mfa_setup")
    data object ActiveSessions : NavRoutes("profile/security/sessions")
    data object Notifications : NavRoutes("profile/notifications")
    data object PaymentWallet : NavRoutes("profile/payment_wallet")
    data object AddressList : NavRoutes("profile/addresses")
    data object AddressForm : NavRoutes("profile/addresses/form?$ARG_ID={$ARG_ID}") {
        fun build(id: String? = null) =
            if (id != null) "profile/addresses/form?$ARG_ID=${id.encoded()}" else "profile/addresses/form"
    }
    data object Membership : NavRoutes("profile/membership")
    data object OrderHistory : NavRoutes("profile/orders")
    data object OrderDetail : NavRoutes("profile/orders/{$ARG_ORDER_ID}") {
        fun build(orderId: String) = "profile/orders/${orderId.encoded()}"
    }
    data object DisputeList : NavRoutes("profile/disputes")
    data object DisputeDetail : NavRoutes("profile/disputes/{$ARG_DISPUTE_ID}") {
        fun build(disputeId: String) = "profile/disputes/${disputeId.encoded()}"
    }

    // Not under profile/disputes/, where it would also match DisputeDetail's pattern.
    data object FileDispute : NavRoutes("profile/file_dispute")
    data object SellerDashboard : NavRoutes("profile/seller_dashboard")
    data object CollectionInsights : NavRoutes("profile/collection_insights")
    data object Referral : NavRoutes("profile/referral")
    data object HelpSupport : NavRoutes("profile/help_support")

    companion object {
        const val ARG_TITLE_ID = "titleId"
        const val ARG_SEASON = "seasonNumber"
        const val ARG_FLYKS_ID = "flyksId"
        const val ARG_SOURCE = "source"
        const val ARG_ID = "id"
        const val ARG_ORDER_ID = "orderId"
        const val ARG_DISPUTE_ID = "disputeId"
    }
}

private fun String.encoded(): String = Uri.encode(this)
