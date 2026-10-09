package com.flykpicks.android.model.enums

enum class OrderStatus(val raw: String, val displayName: String) {
    PENDING_PAYMENT("PENDING_PAYMENT", "Pending Payment"),
    PAYMENT_RECEIVED("PAYMENT_RECEIVED", "Payment Received"),
    LABEL_CREATED("LABEL_CREATED", "Label Created"),
    SHIPPED("SHIPPED", "Shipped"),
    DELIVERED("DELIVERED", "Delivered"),
    RETURNED("RETURNED", "Returned"),
    CANCELED("CANCELED", "Canceled"),
    COMPLETED("COMPLETED", "Completed"),
    REFUNDED("REFUNDED", "Refunded"),
    DEFAULT("DEFAULT", "—"),
    ;

    companion object {
        fun fromRaw(raw: String?): OrderStatus? = entries.firstOrNull { it.raw == raw }
    }
}
