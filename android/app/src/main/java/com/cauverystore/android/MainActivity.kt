package com.cauverystore.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.cauverystore.android.ui.AppRoot
import com.cauverystore.android.ui.CauveryTheme
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import org.json.JSONObject

/** What came back from Razorpay's checkout screen. */
sealed interface PaymentOutcome {
    data class Success(val paymentId: String, val orderId: String, val signature: String?) : PaymentOutcome
    data class Failure(val code: Int, val description: String?) : PaymentOutcome
}

class MainActivity : AppCompatActivity(), PaymentResultWithDataListener {

    private var onPaymentResult: ((PaymentOutcome) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as CauveryApp
        setContent {
            CauveryTheme { AppRoot(app = app, startPayment = ::startPayment) }
        }
    }

    /**
     * Opens Razorpay's own checkout (UPI, cards, net banking, wallets). The key and Razorpay
     * order come from our backend, so the app never holds any Razorpay secret.
     */
    private fun startPayment(options: JSONObject, key: String, onResult: (PaymentOutcome) -> Unit) {
        onPaymentResult = onResult
        val checkout = Checkout()
        checkout.setKeyID(key)
        checkout.setImage(R.mipmap.ic_launcher)
        try {
            checkout.open(this, options)
        } catch (e: Exception) {
            onPaymentResult = null
            onResult(PaymentOutcome.Failure(-1, e.message))
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, data: PaymentData?) {
        val cb = onPaymentResult ?: return
        onPaymentResult = null
        cb(
            PaymentOutcome.Success(
                paymentId = data?.paymentId ?: razorpayPaymentId.orEmpty(),
                orderId = data?.orderId.orEmpty(),
                signature = data?.signature,
            )
        )
    }

    override fun onPaymentError(code: Int, response: String?, data: PaymentData?) {
        val cb = onPaymentResult ?: return
        onPaymentResult = null
        val description = try {
            response?.let { JSONObject(it).optJSONObject("error")?.optString("description") }
        } catch (_: Exception) { null } ?: response
        cb(PaymentOutcome.Failure(code, description))
    }
}
