package com.cauverystore.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.cauverystore.android.data.Network
import com.cauverystore.android.data.Session
import com.razorpay.Checkout
import kotlinx.coroutines.flow.MutableStateFlow

class CauveryApp : Application(), SingletonImageLoader.Factory {
    lateinit var session: Session
        private set
    lateinit var network: Network
        private set

    /** Number of items in the cart, shown on the Cart tab. */
    val cartCount = MutableStateFlow(0)

    override fun onCreate() {
        super.onCreate()
        session = Session(this)
        network = Network(session)
        Checkout.preload(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { network.okHttp })) }
            .crossfade(true)
            .build()
}
