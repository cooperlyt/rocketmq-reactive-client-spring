package io.github.cooperlyt.rocketmq.client.autoconfigure

import io.github.cooperlyt.rocketmq.client.support.ReactiveListenerContainer
import org.apache.rocketmq.client.MQAdmin
import org.apache.rocketmq.remoting.protocol.heartbeat.MessageModel
import org.apache.rocketmq.spring.annotation.SelectorType
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.SmartLifecycle
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.support.GenericApplicationContext
import reactor.util.retry.Retry
import java.time.Duration
import java.util.function.Supplier


@Configuration
@ConditionalOnClass(MQAdmin::class)
@ConditionalOnMissingBean(ReactiveListenerContainer::class)
@EnableConfigurationProperties(RocketMQReactiveConsumerProperties::class)
@Import(MessageConverterConfiguration::class)
@ConditionalOnProperty(prefix = "rocketmq", name = ["name-server"], matchIfMissing = true)
@AutoConfigureAfter(MessageConverterConfiguration::class)
class RocketMQConsumerBindingAutoConfiguration(
    private val rocketMQMessageConverter: org.apache.rocketmq.spring.support.RocketMQMessageConverter,
    private val properties: RocketMQReactiveConsumerProperties): ApplicationContextAware, SmartLifecycle {

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(RocketMQConsumerBindingAutoConfiguration::class.java)
    }

    private lateinit var applicationContext: ApplicationContext

    private val containers: ArrayList<ReactiveListenerContainer> = ArrayList()

    private var running = false

    private fun registerContainer() {
        properties.bindings?.forEach { (name, containerProperties) ->
            createContainer(name, containerProperties.consumer)
        }
    }

    private fun createContainer(name: String, containerProperties: RocketMQReactiveConsumerProperties.BindingConsumerProperties) {
        val functionName = name.split("-").first()

        val genericApplicationContext = applicationContext as GenericApplicationContext

        val retryStrategy = containerProperties.retry?.let {
            Retry.backoff(it.maxAttempts, Duration.ofMillis(it.backoff.delay))
                .maxBackoff(Duration.ofMillis(it.backoff.maxDelay))
                .jitter(it.backoff.multiplier)
                .transientErrors(true)
        }

        genericApplicationContext.registerBean(name,
            ReactiveListenerContainer::class.java,
            Supplier{ ReactiveListenerContainer(
                name = functionName,
                accessKey = containerProperties.accessKey,
                secretKey = containerProperties.secretKey,
                nameServer = properties.nameServer,
                consumerGroup = containerProperties.group,
                topic = containerProperties.topic,
                messageConverter = rocketMQMessageConverter.messageConverter,
                consumeTimeout = containerProperties.consumeTimeout,
                maxReconsumeTimes = containerProperties.maxReconsumeTimes,
                awaitTerminationMillisWhenShutdown = containerProperties.awaitTerminationMillisWhenShutdown,
                instanceName = containerProperties.instanceName,
                messageModel = MessageModel.valueOf(containerProperties.messageModel),
                selectorType = SelectorType.valueOf(containerProperties.selectorType),
                selectorExpression = containerProperties.selectorExpression,
                consumeMode = containerProperties.consumeMode,
                replyTimeout = containerProperties.replyTimeout,
                enableMsgTrace = containerProperties.isEnableMsgTrace,
                customizedTraceTopic = containerProperties.customizedTraceTopic,
                namespace = containerProperties.namespace,
                accessChannel = properties.accessChannel,
                consumeThreadMax = containerProperties.consumeThreadMax,
                consumeThreadNumber = containerProperties.consumeThreadNumber,
                suspendCurrentQueueTimeMillis = containerProperties.suspendCurrentQueueTimeMillis,
                delayLevelWhenNextConsume = containerProperties.delayLevelWhenNextConsume,
                charset = containerProperties.charset,
                pollTimeout = containerProperties.pollTimeout,
                messageQueueCapacity = containerProperties.messageQueueCapacity,
                processTimeout = containerProperties.processTimeout?: containerProperties.retry?.calculateTotalDelay() ?: 1000L,
                retrySpec = retryStrategy)
            })


        val container: ReactiveListenerContainer = genericApplicationContext.getBean(
            name,
            ReactiveListenerContainer::class.java
        )
        containers.add(container)

        if (!container.isRunning)
            container.start()

        log.info("RocketMQ Reactive Container [{}] started", name)
    }

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        this.applicationContext = applicationContext
    }

    override fun start() {
        if (!isRunning) {
            running = true
            registerContainer()
        }
    }

    override fun stop() {
        // do nothing
    }

    override fun isRunning(): Boolean {
        return running
    }
}