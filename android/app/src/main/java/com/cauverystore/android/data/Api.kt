package com.cauverystore.android.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** The Cauvery Store backend endpoints the app uses (same ones the website uses). */
interface Api {
    // Catalogue (public)
    @GET("api/categories")
    suspend fun categories(): List<Category>

    @GET("api/products")
    suspend fun products(): List<Product>

    @GET("api/products/featured")
    suspend fun featured(): List<Product>

    @GET("api/products/search")
    suspend fun search(
        @Query("name") name: String? = null,
        @Query("category") category: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 40,
    ): ProductPage

    @GET("api/products/{id}")
    suspend fun product(@Path("id") id: Long): Product

    @GET("api/products/{id}/similar")
    suspend fun similar(@Path("id") id: Long): List<Product>

    // Sign in
    // Same sign-in endpoint as the website (its error messages are clearer than /user/login).
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/auth/login-2fa")
    suspend fun loginMfa(@Body body: MfaRequest): AuthResponse

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): MessageResponse

    @POST("api/auth/logout")
    suspend fun logout(): MessageResponse

    @GET("api/auth/me")
    suspend fun me(): User

    @GET("api/users/profile")
    suspend fun profile(): User

    @PUT("api/users/profile")
    suspend fun updateProfile(@Body user: User): User

    // Cart
    @GET("api/cart")
    suspend fun cart(): Cart

    @POST("api/cart/add")
    suspend fun addToCart(@Query("productId") productId: Long, @Query("quantity") quantity: Int = 1)

    @POST("api/cart/update-quantity/{itemId}")
    suspend fun updateQuantity(@Path("itemId") itemId: Long, @Query("quantity") quantity: Int)

    @DELETE("api/cart/remove/{itemId}")
    suspend fun removeFromCart(@Path("itemId") itemId: Long)

    // Checkout
    @GET("api/users/addresses")
    suspend fun addresses(): List<Address>

    @POST("api/users/addresses")
    suspend fun addAddress(@Body address: Address): Address

    @POST("api/checkout/bill")
    suspend fun bill(@Body body: BillRequest): Bill

    @POST("api/payment/create")
    suspend fun createPayment(@Body body: OrderPayload): PaymentOrder

    @POST("api/payment/verify")
    suspend fun verifyPayment(@Body body: VerifyRequest): VerifyResponse

    @POST("api/orders/place")
    suspend fun placeOrder(@Body body: OrderPayload): PlacedOrder

    // Orders
    @GET("api/orders")
    suspend fun orders(): List<OrderSummary>

    @GET("api/orders/{id}")
    suspend fun order(@Path("id") id: Long): OrderDetail
}
