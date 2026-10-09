package com.flykpicks.android.service

import com.flykpicks.android.api.UserFacingError
import com.flykpicks.android.api.endpoints.AddressApi
import com.flykpicks.android.api.endpoints.DisputeApi
import com.flykpicks.android.api.endpoints.MembershipApi
import com.flykpicks.android.api.endpoints.OrderApi
import com.flykpicks.android.api.endpoints.PaymentApi
import com.flykpicks.android.model.PaginatedResponse
import com.flykpicks.android.model.enums.DisputeReason
import com.flykpicks.android.model.enums.MembershipTier
import com.flykpicks.android.model.enums.OrderStatus
import com.flykpicks.android.model.enums.OrderType
import com.flykpicks.android.model.enums.ResolutionType
import com.flykpicks.android.model.profile.AddressDTO
import com.flykpicks.android.model.profile.BillingEntryDTO
import com.flykpicks.android.model.profile.ChangePlanRequest
import com.flykpicks.android.model.profile.CreateOrderRequest
import com.flykpicks.android.model.profile.DisputeDTO
import com.flykpicks.android.model.profile.DisputeDetailDTO
import com.flykpicks.android.model.profile.DisputeMessageDTO
import com.flykpicks.android.model.profile.ExtendRentalRequest
import com.flykpicks.android.model.profile.FileDisputeRequest
import com.flykpicks.android.model.profile.MembershipDTO
import com.flykpicks.android.model.profile.MembershipError
import com.flykpicks.android.model.profile.OrderCreationType
import com.flykpicks.android.model.profile.OrderDTO
import com.flykpicks.android.model.profile.OrderDetailDTO
import com.flykpicks.android.model.profile.OrderError
import com.flykpicks.android.model.profile.PaymentMethodDTO
import com.flykpicks.android.model.profile.PayoutOnboardResponse
import com.flykpicks.android.model.profile.PayoutStatusDTO
import com.flykpicks.android.model.profile.SendDisputeMessageRequest
import com.flykpicks.android.model.profile.SetupIntentResponse
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val PAGE_SIZE = 20

@Singleton
class OrderService @Inject constructor(private val api: OrderApi) {

    suspend fun fetchOrders(
        type: OrderType?,
        status: OrderStatus?,
        page: Int,
        limit: Int = PAGE_SIZE,
    ): PaginatedResponse<OrderDTO> = api.fetchOrders(page, limit, type?.raw, status?.raw)

    suspend fun fetchOrderDetail(id: String): OrderDetailDTO = api.fetchOrderDetail(id)

    suspend fun extendRental(rentalId: String, additionalDays: Int): OrderDTO =
        api.extendRental(rentalId, ExtendRentalRequest(additionalDays))

    suspend fun createOrder(listingId: String, type: OrderCreationType): OrderDTO =
        mapHttpErrors({
            when (it.status) {
                400 -> OrderError.InvalidRequest
                404 -> OrderError.NotFound
                409 -> OrderError.map409(it.serverMessage)
                else -> null
            }
        }) { api.createOrder(CreateOrderRequest(listingId, type.raw)) }
}

sealed class PaymentError(detail: String) : Exception(detail), UserFacingError {
    data object PaymentMethodNotFound : PaymentError("payment_method_not_found") {
        override val userMessage get() = "This payment method is no longer available."
    }

    data object LastPaymentMethod : PaymentError("last_payment_method") {
        override val userMessage get() = "You must keep at least one payment method on file."
    }

    data object ProcessorUnavailable : PaymentError("processor_unavailable") {
        override val userMessage get() = "Payments temporarily unavailable — try again."
    }
}

@Singleton
class PaymentService @Inject constructor(private val api: PaymentApi) {

    suspend fun fetchPaymentMethods(): List<PaymentMethodDTO> = api.fetchPaymentMethods()

    /** The client secret drives Stripe PaymentSheet in setup mode (blueprint §15.1). */
    suspend fun createSetupIntent(): SetupIntentResponse = api.createSetupIntent()

    suspend fun removePaymentMethod(id: String) =
        mapHttpErrors({
            when (it.status) {
                400 -> PaymentError.LastPaymentMethod
                404 -> PaymentError.PaymentMethodNotFound
                502 -> PaymentError.ProcessorUnavailable
                else -> null
            }
        }) { api.removePaymentMethod(id) }

    suspend fun setDefault(id: String) =
        mapHttpErrors({ if (it.status == 404) PaymentError.PaymentMethodNotFound else null }) {
            api.setDefault(id)
        }

    suspend fun fetchPayoutStatus(): PayoutStatusDTO = api.fetchPayoutStatus()

    /** Open the returned URL in a Custom Tab (Stripe Connect onboarding). */
    suspend fun startPayoutOnboarding(): PayoutOnboardResponse = api.startPayoutOnboarding()
}

@Singleton
class DisputeService @Inject constructor(private val api: DisputeApi) {

    /** [status] is a `DisputeStatus.raw`; null lists all. */
    suspend fun fetchDisputes(status: String?, page: Int, limit: Int = PAGE_SIZE): PaginatedResponse<DisputeDTO> =
        api.fetchDisputes(page, limit, status)

    suspend fun fetchDisputeDetail(id: String): DisputeDetailDTO = api.fetchDisputeDetail(id)

    suspend fun fileDispute(
        orderId: String,
        reason: DisputeReason,
        description: String,
        desiredResolution: ResolutionType,
    ): DisputeDTO = api.fileDispute(FileDisputeRequest(orderId, reason.raw, description, desiredResolution.raw))

    suspend fun sendMessage(disputeId: String, content: String): DisputeMessageDTO =
        api.sendMessage(disputeId, SendDisputeMessageRequest(content))

    suspend fun acceptResolution(disputeId: String) = api.acceptResolution(disputeId)

    suspend fun escalate(disputeId: String) = api.escalate(disputeId)
}

@Singleton
class MembershipService @Inject constructor(private val api: MembershipApi) {

    suspend fun fetchMembership(): MembershipDTO = api.fetchMembership()

    suspend fun changePlan(newTier: MembershipTier): MembershipDTO =
        mapHttpErrors({ if (it.status == 400) MembershipError.InvalidTier else null }) {
            api.changePlan(ChangePlanRequest(newTier.raw.lowercase(Locale.ROOT)))
        }

    suspend fun cancelMembership() =
        mapHttpErrors({ if (it.status == 409) MembershipError.AlreadyCanceled else null }) {
            api.cancelMembership()
        }

    suspend fun fetchBillingHistory(): List<BillingEntryDTO> = api.fetchBillingHistory()
}

sealed class AddressError(detail: String) : Exception(detail), UserFacingError {
    data object NotFound : AddressError("address_not_found") {
        override val userMessage get() = "This address is no longer available."
    }
}

@Singleton
class AddressService @Inject constructor(private val api: AddressApi) {

    suspend fun fetchAddresses(): List<AddressDTO> = api.fetchAddresses()

    suspend fun createAddress(address: AddressDTO): AddressDTO = api.createAddress(address)

    suspend fun updateAddress(address: AddressDTO): AddressDTO {
        val id = requireNotNull(address.id) { "Cannot update an address without an id" }
        return api.updateAddress(id, address)
    }

    suspend fun deleteAddress(id: String) =
        mapHttpErrors({ if (it.status == 404) AddressError.NotFound else null }) { api.deleteAddress(id) }

    suspend fun setDefault(id: String) =
        mapHttpErrors({ if (it.status == 404) AddressError.NotFound else null }) { api.setDefault(id) }
}
