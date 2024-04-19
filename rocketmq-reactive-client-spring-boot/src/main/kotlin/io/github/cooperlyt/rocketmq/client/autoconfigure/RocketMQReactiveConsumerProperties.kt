package io.github.cooperlyt.rocketmq.client.autoconfigure

import org.apache.rocketmq.client.AccessChannel
import org.apache.rocketmq.spring.annotation.ConsumeMode
import org.apache.rocketmq.spring.autoconfigure.RocketMQProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import kotlin.math.min

@ConfigurationProperties(prefix = "rocketmq")
data class RocketMQReactiveConsumerProperties(
    var nameServer: String,
    var accessChannel: AccessChannel = AccessChannel.LOCAL,
    var bindings: MutableMap<String, BindingProperties>? = null,
) {

    class BindingConsumerProperties: RocketMQProperties.PullConsumer(){
        var consumeTimeout: Long = 15L
        var maxReconsumeTimes: Int = -1
        var awaitTerminationMillisWhenShutdown: Long = 1000L
        var consumeMode: ConsumeMode = ConsumeMode.CONCURRENTLY
        var replyTimeout: Int = 3000
        var consumeThreadMax: Int = 64
        var consumeThreadNumber: Int = 20
        var suspendCurrentQueueTimeMillis: Long = 1000
        var delayLevelWhenNextConsume: Int = 0
        var charset: String = "UTF-8"
        var messageQueueCapacity: Int = 1024
        var pollTimeout: Duration = Duration.ofMillis(1000)
        var retry: RetryProperties? = null
        var processTimeout: Long? = null
    }

    data class BindingProperties(
       var consumer: BindingConsumerProperties
    )

    data class RetryProperties(
        var maxAttempts: Long = 10,
        var backoff: BackoffProperties = BackoffProperties()
    ) {


        data class BackoffProperties( var delay: Long = 1000,
                                      var maxDelay: Long = Long.MAX_VALUE,
                                      var multiplier: Double = 0.2)

        fun calculateTotalDelay(): Long {
            val defaultProcessTime = 60 * 1000L

            var totalDelay = 0L
            var delay = backoff.delay
            for (i in 1..maxAttempts) {
                totalDelay += delay + (delay * backoff.multiplier).toLong() + defaultProcessTime
                delay = min(2 * delay, backoff.maxDelay)
            }
            return totalDelay
        }



    }
}