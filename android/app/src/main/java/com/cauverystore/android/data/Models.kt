package com.cauverystore.android.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Only the fields the app shows are declared; everything else in the backend's JSON is ignored.

@Serializable
data class Category(
    val id: Long,
    val name: String,
    val description: String? = null,
)

@Serializable
data class ProductImage(
    val url: String? = null,
    val thumbUrl: String? = null,
    val previewUrl: String? = null,
    val zoomUrl: String? = null,
    val main: Boolean = false,
    val sortOrder: Int? = 0,
)

@Serializable
data class Product(
    val id: Long,
    val name: String = "",
    val description: String? = null,
    val shortDescription: String? = null,
    val price: Double? = null,
    val offerPrice: Double? = null,
    val stock: Int? = null,
    val brand: String? = null,
    val color: String? = null,
    val size: String? = null,
    val material: String? = null,
    val warranty: String? = null,
    val returnEligible: Boolean? = null,
    val returnWindow: Int? = null,
    val deliveryDays: Int? = null,
    val images: List<ProductImage> = emptyList(),
    val category: Category? = null,
) {
    /** The price a shopper pays: the offer price when it is a real discount. */
    val sellingPrice: Double
        get() {
            val p = price ?: 0.0
            val o = offerPrice
            return if (o != null && o > 0 && o < p) o else p
        }

    val hasDiscount: Boolean get() = sellingPrice < (price ?: 0.0)

    val imageUrls: List<String>
        get() = images.sortedWith(compareByDescending<ProductImage> { it.main }.thenBy { it.sortOrder ?: 0 })
            .mapNotNull { it.previewUrl ?: it.url }

    val thumbnail: String? get() = images.firstOrNull { it.main }?.let { it.thumbUrl ?: it.url }
        ?: images.firstOrNull()?.let { it.thumbUrl ?: it.url }
}

@Serializable
data class ProductPage(
    val content: List<Product> = emptyList(),
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val number: Int = 0,
    val last: Boolean = true,
)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(
    val fullName: String,
    val email: String,
    val password: String,
    val phone: String? = null,
)

@Serializable
data class MfaRequest(val email: String, val otp: String)

@Serializable
data class AuthResponse(
    val accessToken: String? = null,
    val username: String? = null,
    val email: String? = null,
    val role: String? = null,
    val roles: List<String>? = null,
    val userId: Long? = null,
    val mfaRequired: Boolean? = false,
    val message: String? = null,
) {
    /** Shopper accounts only; sellers and staff use the website. Unknown role counts as a shopper. */
    val isCustomer: Boolean
        get() {
            val all = (roles.orEmpty() + listOfNotNull(role)).map { it.uppercase().removePrefix("ROLE_") }
            return all.isEmpty() || "CUSTOMER" in all
        }
}

@Serializable
data class MessageResponse(val message: String? = null, val error: String? = null)

@Serializable
data class User(
    val id: Long? = null,
    val username: String? = null,
    val email: String? = null,
    val fullName: String? = null,
    val phone: String? = null,
)

@Serializable
data class CartProduct(
    val id: Long,
    val name: String = "",
    val price: Double? = null,
    val brand: String? = null,
    val image: String? = null,
    val stockStatus: String? = null,
    val inStock: Boolean? = true,
)

@Serializable
data class CartItem(
    val id: Long,
    val quantity: Int = 1,
    val subtotal: Double? = null,
    val product: CartProduct,
)

@Serializable
data class Cart(
    val items: List<CartItem> = emptyList(),
    val totalItems: Int = 0,
    val totalPrice: Double = 0.0,
    val deliveryCharge: Double = 0.0,
    val tax: Double? = null,
    val finalAmount: Double = 0.0,
    val taxComplete: Boolean? = true,
    val freeDeliveryEligible: Boolean? = false,
)

@Serializable
data class Address(
    val id: Long? = null,
    val fullName: String? = null,
    val phone: String? = null,
    val line1: String? = null,
    val line2: String? = null,
    val street: String? = null,
    val city: String? = null,
    val state: String? = null,
    val pincode: String? = null,
    val country: String? = "India",
) {
    fun oneLine(): String = listOfNotNull(line1, line2, street, city, state, pincode)
        .filter { it.isNotBlank() }.joinToString(", ")
}

@Serializable
data class BillRequest(val state: String, val pincode: String, val city: String?)

@Serializable
data class Bill(
    val totalTax: Double? = null,
    val canProceed: Boolean? = true,
    val message: String? = null,
)

@Serializable
data class PaymentOrder(
    val razorpayOrderId: String? = null,
    val amount: JsonElement? = null,
    val currency: String? = "INR",
    val key: String? = null,
)

@Serializable
data class VerifyRequest(
    val razorpay_payment_id: String,
    val razorpay_order_id: String,
    val razorpay_signature: String?,
)

@Serializable
data class VerifyResponse(val message: String? = null, val method: String? = null)

@Serializable
data class OrderLine(val productId: Long, val quantity: Int)

/**
 * The body sent to /api/payment/create and then /api/orders/place, in the same shape the
 * website's checkout sends.
 */
@Serializable
data class OrderPayload(
    val items: List<OrderLine>,
    val addressId: Long? = null,
    val fullName: String? = null,
    val phone: String? = null,
    val line1: String? = null,
    val line2: String? = null,
    val city: String? = null,
    val state: String? = null,
    val pincode: String? = null,
    val country: String = "India",
    val paymentMethod: String = "ONLINE",
    val subtotal: Double,
    val discount: Double = 0.0,
    val delivery: Double,
    val total: Double,
    val amount: Double? = null,
    val paymentId: String? = null,
    val razorpayOrderId: String? = null,
)

@Serializable
data class PlacedOrder(val id: Long? = null, val status: String? = null, val totalAmount: Double? = null)

@Serializable
data class OrderSummaryItem(
    val id: Long? = null,
    val quantity: Int = 1,
    val price: Double? = null,
    val productId: Long? = null,
    val productName: String? = null,
    val productImage: String? = null,
)

@Serializable
data class OrderSummary(
    val id: Long,
    val status: String? = null,
    val totalAmount: Double? = null,
    val createdAt: String? = null,
    val paymentMethod: String? = null,
    val items: List<OrderSummaryItem> = emptyList(),
)

@Serializable
data class OrderDetailItem(
    val name: String? = null,
    val quantity: Int = 1,
    val price: Double? = null,
    val subtotal: Double? = null,
)

@Serializable
data class ShippingAddress(
    val name: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val pincode: String? = null,
)

@Serializable
data class OrderDetail(
    val id: Long,
    val status: String? = null,
    val paymentMethod: String? = null,
    val paymentStatus: String? = null,
    val totalAmount: Double? = null,
    val subTotal: Double? = null,
    val tax: Double? = null,
    val deliveryCharge: Double? = null,
    val trackingNumber: String? = null,
    val courier: String? = null,
    val createdAt: String? = null,
    val shippingAddress: ShippingAddress? = null,
    val items: List<OrderDetailItem> = emptyList(),
)
