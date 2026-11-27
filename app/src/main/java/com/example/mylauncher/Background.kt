package com.example.mylauncher

import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Poore launcher ke liye ek hi chhota background pool (max 2 threads, 30s idle pe band).
 * Pehle MainActivity, PaletteEngine aur Settings har ek apna executor/thread banate the.
 * Is pool ko shutdown nahi karte: ye process ke saath rehta hai aur idle hone pe apne aap khali ho jata hai.
 */
object Background {
    val io: ExecutorService = ThreadPoolExecutor(
        2, 2, 30L, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>()
    ).apply { allowCoreThreadTimeOut(true) }
}
