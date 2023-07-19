package com.example.feedmeassignment.model

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "CookingBot"
const val ORDER_PROCESS_DURATION: Long = 3_000

class CookingBot(private val coroutineScope: CoroutineScope) {
    private val orderChannel = Channel<Order>()
    private var isActive = true
    private var job: Job? = null

    suspend fun enqueueOrder(order: Order): Boolean {
        val result = CompletableDeferred<Boolean>()
        coroutineScope.launch {
            result.complete(orderChannel.trySend(order).isSuccess)
        }
        return result.await()
    }

    fun start() {
        isActive = true
        job = coroutineScope.launch {
            for (order in orderChannel) {
                if (!isActive)
                    break
            }
        }
    }

    fun stop() {
        isActive = false
        job?.cancel()
        job = null
    }
}