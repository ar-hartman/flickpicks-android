package com.flykpicks.android.api.endpoints

import com.flykpicks.android.model.PaginatedResponse
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
import com.flykpicks.android.model.profile.OrderDTO
import com.flykpicks.android.model.profile.OrderDetailDTO
import com.flykpicks.android.model.profile.PaymentMethodDTO
import com.flykpicks.android.model.profile.PayoutOnboardResponse
import com.flykpicks.android.model.profile.PayoutStatusDTO
import com.flykpicks.android.model.profile.SendDisputeMessageRequest
import com.flykpicks.android.model.profile.SetupIntentResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface OrderApi {
    /** [type] / [status] are `OrderType.raw` / `OrderStatus.raw`; null omits the filter. */
    @GET("v1/users/me/orders")
    suspend fun fetchOrders(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("type") type: String?,
        @Query("status") status: String?,
    ): PaginatedResponse<OrderDTO>

    @GET("v1/users/me/orders/{id}")
    suspend fun fetchOrderDetail(@Path("id") id: String): OrderDetailDTO

    /** Creates the order in PENDING_PAYMENT; no payment is captured in this build. */
    @POST("v1/users/me/orders")
    suspend fun createOrder(@Body body: CreateOrderRequest): OrderDTO

    @POST("v1/users/me/rentals/{id}/extend")
    suspend fun extendRental(@Path("id") id: String, @Body body: ExtendRentalRequest): OrderDTO
}

interface PaymentApi {
    @GET("v1/users/me/payment-methods")
    suspend fun fetchPaymentMethods(): List<PaymentMethodDTO>

    @POST("v1/users/me/payment-methods/setup-intent")
    suspend fun createSetupIntent(): SetupIntentResponse

    @DELETE("v1/users/me/payment-methods/{id}")
    suspend fun removePaymentMethod(@Path("id") id: String)

    @PATCH("v1/users/me/payment-methods/{id}/default")
    suspend fun setDefault(@Path("id") id: String)

    @GET("v1/users/me/payout/status")
    suspend fun fetchPayoutStatus(): PayoutStatusDTO

    @POST("v1/users/me/payout/onboard")
    suspend fun startPayoutOnboarding(): PayoutOnboardResponse
}

interface DisputeApi {
    @GET("v1/users/me/disputes")
    suspend fun fetchDisputes(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("status") status: String?,
    ): PaginatedResponse<DisputeDTO>

    @GET("v1/disputes/{id}")
    suspend fun fetchDisputeDetail(@Path("id") id: String): DisputeDetailDTO

    @POST("v1/disputes")
    suspend fun fileDispute(@Body body: FileDisputeRequest): DisputeDTO

    @POST("v1/disputes/{id}/messages")
    suspend fun sendMessage(@Path("id") id: String, @Body body: SendDisputeMessageRequest): DisputeMessageDTO

    @POST("v1/disputes/{id}/accept")
    suspend fun acceptResolution(@Path("id") id: String)

    @POST("v1/disputes/{id}/escalate")
    suspend fun escalate(@Path("id") id: String)
}

interface MembershipApi {
    @GET("v1/users/me/membership")
    suspend fun fetchMembership(): MembershipDTO

    @POST("v1/users/me/membership/change")
    suspend fun changePlan(@Body body: ChangePlanRequest): MembershipDTO

    @POST("v1/users/me/membership/cancel")
    suspend fun cancelMembership()

    @GET("v1/users/me/membership/billing-history")
    suspend fun fetchBillingHistory(): List<BillingEntryDTO>
}

interface AddressApi {
    @GET("v1/users/me/addresses")
    suspend fun fetchAddresses(): List<AddressDTO>

    @POST("v1/users/me/addresses")
    suspend fun createAddress(@Body body: AddressDTO): AddressDTO

    @PUT("v1/users/me/addresses/{id}")
    suspend fun updateAddress(@Path("id") id: String, @Body body: AddressDTO): AddressDTO

    @DELETE("v1/users/me/addresses/{id}")
    suspend fun deleteAddress(@Path("id") id: String)

    @PATCH("v1/users/me/addresses/{id}/default")
    suspend fun setDefault(@Path("id") id: String)
}
