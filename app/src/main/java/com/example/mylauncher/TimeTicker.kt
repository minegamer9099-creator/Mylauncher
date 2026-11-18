package com.example.mylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * Har minute (aur time / date / timezone badalne pe) ek hi BroadcastReceiver sabko khabar deta hai.
 * Pehle har clock / date / analog widget apna alag receiver register karta tha (4-5 receivers, ek hi event).
 * Receiver tabhi registered rehta hai jab kam se kam ek view screen se juda ho. Sirf main thread se use karo.
 */
object TimeTicker {

    private val listeners = LinkedHashSet<Runnable>()
    private var appContext: Context? = null
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Copy pe ghumo: listener callback ke andar unregister kar sakta hai
            for (listener in listeners.toList()) listener.run()
        }
    }

    fun register(context: Context, listener: Runnable) {
        if (!registered) {
            val ctx = context.applicationContext
            appContext = ctx
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
            }
            ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        }
        listeners.add(listener)
    }

    fun unregister(listener: Runnable) {
        listeners.remove(listener)
        if (listeners.isEmpty() && registered) {
            try {
                appContext?.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                // pehle se registered nahi tha
            }
            registered = false
        }
    }
}
