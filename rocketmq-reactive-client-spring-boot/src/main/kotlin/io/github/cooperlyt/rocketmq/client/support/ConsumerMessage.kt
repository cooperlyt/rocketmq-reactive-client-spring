package io.github.cooperlyt.rocketmq.client.support

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ConsumerMessage<T>(private val data: T) {

    private val latch: CountDownLatch = CountDownLatch(1)

    private var hasFailure = false

    private var replay: Any? = null

    @Throws(InterruptedException::class)
    fun waitFinish(timeout: Long): Boolean {
        return latch.await(timeout, TimeUnit.MILLISECONDS)
    }

    fun isSuccess(): Boolean {
        return !this.hasFailure
    }

    fun isHasReplay(): Boolean {
        return this.replay != null
    }

    fun getReplay(): Any? {
        return this.replay
    }

    fun getData(): T {
        return this.data
    }

    fun ack() {
        latch.countDown()
    }

    fun ack(replay: Any){
        this.replay = replay
        ack()
    }

    fun fail() {
        this.hasFailure = true
        val count = latch.count

        var i = 0
        while (i.toLong() < count) {
            latch.countDown()
            ++i
        }
    }

}