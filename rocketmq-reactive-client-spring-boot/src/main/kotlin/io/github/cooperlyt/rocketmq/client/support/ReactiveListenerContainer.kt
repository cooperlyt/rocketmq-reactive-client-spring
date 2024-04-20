package io.github.cooperlyt.rocketmq.client.support

import io.github.cooperlyt.rocketmq.client.ConsumerResult
import io.github.cooperlyt.rocketmq.client.TypedConsumer
import io.github.cooperlyt.rocketmq.client.TypedReplayConsumer
import org.apache.rocketmq.client.AccessChannel
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer
import org.apache.rocketmq.client.consumer.MessageSelector
import org.apache.rocketmq.client.consumer.listener.*
import org.apache.rocketmq.client.consumer.rebalance.AllocateMessageQueueAveragely
import org.apache.rocketmq.client.exception.MQClientException
import org.apache.rocketmq.client.producer.SendCallback
import org.apache.rocketmq.client.producer.SendResult
import org.apache.rocketmq.client.producer.SendStatus
import org.apache.rocketmq.client.utils.MessageUtil
import org.apache.rocketmq.common.message.MessageExt
import org.apache.rocketmq.remoting.RPCHook
import org.apache.rocketmq.remoting.exception.RemotingException
import org.apache.rocketmq.remoting.netty.TlsSystemConfig.tlsEnable
import org.apache.rocketmq.remoting.protocol.heartbeat.MessageModel
import org.apache.rocketmq.spring.annotation.ConsumeMode
import org.apache.rocketmq.spring.annotation.SelectorType
import org.apache.rocketmq.spring.support.RocketMQUtil
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.SmartLifecycle
import org.springframework.messaging.Message
import org.springframework.messaging.MessageHeaders
import org.springframework.messaging.converter.MessageConversionException
import org.springframework.messaging.converter.MessageConverter
import org.springframework.messaging.converter.SmartMessageConverter
import org.springframework.messaging.support.MessageBuilder
import org.springframework.util.MimeTypeUtils
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono
import reactor.util.retry.RetryBackoffSpec
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.nio.charset.Charset
import java.time.Duration
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer


/**
 *       基于org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer 修改而来用于支持reactor
 *
 *       去掉原来重试机制，使用reactor的重试机制
 *
 *
 *
 */
open class ReactiveListenerContainer(
    private val name: String,
    private val accessKey: String?,
    private val secretKey: String?,
    private val nameServer: String,
    private val consumerGroup: String,
    private val topic: String,


    private val messageConverter: MessageConverter,
    private val consumeTimeout:Long,
    private val maxReconsumeTimes:Int,
    private val awaitTerminationMillisWhenShutdown : Long,
    private val instanceName: String,
    private val messageModel: MessageModel,
    private val selectorType: SelectorType,
    private val selectorExpression: String,
    private val consumeMode: ConsumeMode,
    private val replyTimeout: Int,

    private val enableMsgTrace: Boolean = false,
    private var customizedTraceTopic: String? = null,
    private val namespace: String? = null,
    private val accessChannel: AccessChannel? = AccessChannel.LOCAL,
    private val consumeThreadMax: Int = 64,
    private val consumeThreadNumber: Int = 20,
    private val suspendCurrentQueueTimeMillis: Long = 1000,
    private val delayLevelWhenNextConsume: Int = 0,
    private val charset: String = "UTF-8",
    private val pollTimeout: Duration = Duration.ofMillis(1000),
    private val retrySpec: RetryBackoffSpec? = null,
    messageQueueCapacity: Int = 1024,
    private val processTimeout: Long = 1000,
    ): InitializingBean, DisposableBean, SmartLifecycle, ApplicationContextAware {

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(ReactiveListenerContainer::class.java)
    }

    private var  running: AtomicBoolean = AtomicBoolean(false)

    private lateinit var applicationContext: ApplicationContext

    private var consumer: DefaultMQPushConsumer? = null

    private var messageBlockingQueue: BlockingQueue<ConsumerMessage<MessageExt>> = LinkedBlockingQueue(messageQueueCapacity)

    private var disposable: Disposable? = null


    @Suppress("UNCHECKED_CAST")
    override fun start() {
        check(!this.isRunning) { "container already running. $name" }

        val consumerBean = applicationContext.getBean(name)

        var genericInterface: ParameterizedType? = null
        if (consumerBean::class.java.genericInterfaces.isNotEmpty() &&
            consumerBean::class.java.genericInterfaces[0] is ParameterizedType){
            genericInterface = consumerBean::class.java.genericInterfaces[0] as ParameterizedType
        }


        val consumerFunction : (Any) -> Mono<Any> = when (consumerBean) {
            is Function1<*,*> -> {
                consumerBean as (Any) -> Mono<Any>
            }

            is java.util.function.Function<*,*> -> {
                if (genericInterface == null && consumerBean is TypedReplayConsumer<*>){
                    genericInterface = consumerBean.type
                }
                object: (Any) -> Mono<Any> {
                    override fun invoke(p1: Any): Mono<Any> {
                        return (consumerBean as java.util.function.Function<Any,*>).apply(p1) as Mono<Any>
                    }
                }
            }

            is Consumer<*> -> {
                if (genericInterface == null && consumerBean is TypedConsumer<*>){
                    genericInterface = consumerBean.type
                }
                object: (Any) -> Mono<Any>{
                    override fun invoke(p1: Any): Mono<Any> {
                        (consumerBean as Consumer<Any>).accept(p1)
                        return Mono.just(ConsumerResult.SUCCESS)
                    }
                }
            }

            else -> {
                throw IllegalArgumentException("$name - consumer bean type error ${consumerBean::class.java.name}")
            }
        }

        if (genericInterface == null){
            throw IllegalArgumentException("$name - consumer bean type error")
        }

        //val genericInterface = consumerBean::class.java.genericInterfaces[0] as ParameterizedType
        val actualTypeArguments = genericInterface.actualTypeArguments
        val firstTypeArgument = actualTypeArguments[0]

        log.debug("{} firstTypeArgument: {}",name, firstTypeArgument)

        val isMessage = firstTypeArgument is ParameterizedType && firstTypeArgument.rawType == Message::class.java

        log.debug("{} consumer is request Spring message : {}",name, isMessage)

        val requestMessageType: Type
        if (isMessage) {
            val messageType = firstTypeArgument as ParameterizedType
            val messageGenericType = messageType.actualTypeArguments[0]
            requestMessageType = messageGenericType
            log.debug("{} messageGenericType: {}",name, messageGenericType)
        }else {
            requestMessageType = firstTypeArgument
        }

        disposable = Flux.create(startMessageEmitter())
            .flatMap { message -> consumerFunction.invoke(doConvertMessage(message.getData(), requestMessageType, isMessage))
                .doOnError { log.warn("$name - Consume fail! ", it) }
                .let { originalFlux ->
                    if (retrySpec != null) {
                        originalFlux.retryWhen(retrySpec
                            .doBeforeRetry { log.warn("{} - Retrying...", name) }
                            .filter {
                                if (isRunning) {
                                    true
                                } else {
                                    throw RuntimeException("Retry stopped")
                                }
                            }
                        )
                    } else {
                        originalFlux
                    }
                }
                .onErrorResume {
                    log.error("$name - Consume fail! ", it)
                    Mono.just(ConsumerResult.FAILURE)
                }
                .defaultIfEmpty(ConsumerResult.SUCCESS)
                .doOnNext { result ->
                    log.debug("{} - Consume result: {}", name, result)
                    if (result is ConsumerResult) {
                        if (result == ConsumerResult.SUCCESS) {
                            message.ack()
                        } else {
                            message.fail()
                        }
                    } else {
                        message.ack(result)
                    }
                }
            }
            .subscribe()


        log.info("running container: {}", name)
    }

    override fun getPhase(): Int {
        // Returning Integer.MAX_VALUE only suggests that
        // we will be the first bean to shutdown and last bean to start
        return Int.MAX_VALUE
    }


    override fun isRunning(): Boolean {
        return running.get()
    }

    override fun isAutoStartup(): Boolean {
        return true
    }

    override fun stop(callback: Runnable) {
        stop()
        callback.run()
    }

    override fun stop() {
        if (!running.get()) {
            return
        }
        running.set(false)

        while (disposable?.isDisposed != true) {
            log.info("{} - Waiting for the consumer to complete", name)
            Thread.sleep(1000)
        }
        log.info("{} container is Stop!", name)
    }

    override fun destroy() {
        stop()

        log.info("container destroyed, {}", name)
    }

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        this.applicationContext = applicationContext
    }

    override fun afterPropertiesSet() {
        initRocketMQPushConsumer()

    }

    inner class DefaultMessageListenerConcurrently : MessageListenerConcurrently {
        override fun consumeMessage(
            msgs: List<MessageExt>,
            context: ConsumeConcurrentlyContext
        ): ConsumeConcurrentlyStatus {
            if (!running.get()){
                context.delayLevelWhenNextConsume = delayLevelWhenNextConsume
                return ConsumeConcurrentlyStatus.RECONSUME_LATER
            }

            for (messageExt in msgs) {
                log.debug("received msg: {}", messageExt)
                try {
                    val now = System.currentTimeMillis()
                    if (!handleMessage(messageExt))
                        throw RuntimeException("handle message failed")
                    val costTime = System.currentTimeMillis() - now
                    log.debug("consume {} cost: {} ms", messageExt.msgId, costTime)
                } catch (e: java.lang.Exception) {
                    log.warn(
                        "consume message failed. messageId:{}, topic:{}, reconsumeTimes:{}",
                        messageExt.msgId,
                        messageExt.topic,
                        messageExt.reconsumeTimes,
                        e
                    )
                    context.delayLevelWhenNextConsume = delayLevelWhenNextConsume
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER
                }
            }

            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS
        }
    }

    inner class DefaultMessageListenerOrderly : MessageListenerOrderly {
        override fun consumeMessage(msgs: List<MessageExt>, context: ConsumeOrderlyContext): ConsumeOrderlyStatus {
            if (!running.get()){
                context.suspendCurrentQueueTimeMillis = suspendCurrentQueueTimeMillis
                return ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT
            }


            for (messageExt in msgs) {
                log.debug("received msg: {}", messageExt)
                try {
                    val now = System.currentTimeMillis()
                    if (!handleMessage(messageExt))
                        throw RuntimeException("handle message failed")
                    val costTime = System.currentTimeMillis() - now
                    log.debug("consume {} cost: {} ms", messageExt.msgId, costTime)
                } catch (e: java.lang.Exception) {
                    log.warn(
                        "consume message failed. messageId:{}, topic:{}, reconsumeTimes:{}",
                        messageExt.msgId,
                        messageExt.topic,
                        messageExt.reconsumeTimes,
                        e
                    )
                    context.suspendCurrentQueueTimeMillis = suspendCurrentQueueTimeMillis
                    return ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT
                }
            }

            return ConsumeOrderlyStatus.SUCCESS
        }
    }


    @Throws(MQClientException::class, RemotingException::class, InterruptedException::class)
    fun handleMessage(messageExt: MessageExt): Boolean {
        val message = ConsumerMessage(messageExt)
        messageBlockingQueue.put(message)

        val isCompleted: Boolean
        try {
            isCompleted = message.waitFinish(processTimeout)
        } catch (e: InterruptedException) {
            log.error("Interrupted when waiting messages to be finished.", e)
            throw RuntimeException(e)
        }
        val isSuccess = message.isSuccess()


        val result = isCompleted && isSuccess

        log.trace("isCompleted: {}, isSuccess: {} , result: {}", isCompleted, isSuccess, result)
        if (result && message.isHasReplay()){
            sendReplay(message.getData(),message.getReplay()!!)
        }
        return result
    }

    private fun startMessageEmitter() : Consumer<FluxSink<ConsumerMessage<MessageExt>>> {
        return Consumer { sink ->
            val thread = Thread {
                check(Objects.nonNull(consumer)) { "consumer has been build. $name" }
                try {
                    consumer!!.start()
                } catch (e: MQClientException) {
                    throw IllegalStateException("Failed to start RocketMQ push consumer", e)
                }
                running.set(true)
                while (running.get()) {
                    try {
                        val messages = getWithoutAck() // 获取message
                        if (messages != null){
                            sink.next(messages)
                        }
                    } catch (e: Exception) {
                        log.error(e.message, e)
                    }
                }

                sink.complete()
                if (Objects.nonNull(consumer)) {
                    consumer!!.shutdown();
                }
                log.debug("{} - The MQ client consume is stop", name)
            }
            thread.setUncaughtExceptionHandler { _, e ->   log.error("$name - parse events has an error", e)}
            thread.start()
        }
    }

    private fun getWithoutAck(): ConsumerMessage<MessageExt>? {
        try {
            val batchMessage = messageBlockingQueue.poll(pollTimeout.toNanos(), TimeUnit.NANOSECONDS)
            if (batchMessage != null) {
                return batchMessage
            }
        } catch (ex: InterruptedException) {
            log.warn("Get message timeout", ex)
            //throw CanalClientException("Failed to fetch the data after: $pollTimeout")
        }
        return null
    }

    private fun sendReplay(messageExt: MessageExt, replyContent: Any){
        val message: Message<Any> = MessageBuilder.withPayload(replyContent).build()

        val replyMessage = MessageUtil.createReplyMessage(messageExt, convertToBytes(message))
        val producer = consumer!!.defaultMQPushConsumerImpl.getmQClientFactory().defaultMQProducer
        producer.sendMsgTimeout = replyTimeout
        producer.send(replyMessage, object : SendCallback {
            override fun onSuccess(sendResult: SendResult) {
                if (sendResult.sendStatus != SendStatus.SEND_OK) {
                    log.error("Consumer replies message failed. SendStatus: {}", sendResult.sendStatus)
                } else {
                    log.debug("Consumer replies message success.")
                }
            }

            override fun onException(e: Throwable) {
                log.error("Consumer replies message failed. error: {}", e.localizedMessage)
            }
        })
    }

    private fun doConvertMessage(messageExt: MessageExt, messageType: Type, toMessage: Boolean): Any {
        if (Objects.equals(messageType, MessageExt::class.java) || Objects.equals(
                messageType,
                org.apache.rocketmq.common.message.Message::class.java
            )
        ) {
            return messageExt
        } else {
            val str = String(messageExt.body, Charset.forName(charset))
            return if (!toMessage && Objects.equals(messageType, String::class.java)) {
                str
            } else {
                // If msgType not string, use objectMapper change it.
                try {
                    val clazz = when (messageType) {
                        is ParameterizedType -> {
                            messageType.rawType as Class<*>
                        }

                        is Class<*> -> {
                            messageType
                        }

                        else -> {
                            throw java.lang.RuntimeException("parameterType:$messageType of onMessage method is not supported")
                        }
                    }

                    val messageBody = messageConverter.fromMessage(MessageBuilder.withPayload(str).build(), clazz)
                        ?: throw java.lang.RuntimeException("cannot convert message to $messageType")

                    return if (toMessage) {
                        log.trace("convert to Spring Message<>. str:{}, msgType:{}", str, messageType)
                        MessageBuilder.withPayload(messageBody).copyHeaders(messageExt.properties).build()
                    } else {
                        log.trace("convert to {}", messageType)
                        messageBody
                    }

                } catch (e: java.lang.Exception) {
                    log.info("convert failed. str:{}, msgType:{}", str, messageType)
                    throw java.lang.RuntimeException("cannot convert message to $messageType", e)
                }
            }
        }
    }



    private fun convertToBytes(message: Message<*>): ByteArray {
        val messageWithSerializedPayload = doConvert(message.payload, message.headers)
        val payloadObj = messageWithSerializedPayload.payload
        val payloads: ByteArray
        try {
            if (payloadObj == null) {
                throw RuntimeException("the message cannot be empty")
            }
            payloads = when (payloadObj) {
                is String -> payloadObj.toByteArray(Charset.forName(charset))
                is ByteArray -> payloadObj
                else -> {
                    val jsonObj = this.messageConverter.fromMessage(messageWithSerializedPayload, payloadObj::class.java) as String?
                    jsonObj?.toByteArray(Charset.forName(charset))
                        ?: throw RuntimeException("empty after conversion [messageConverter:${this.messageConverter::class.java},payloadClass:${payloadObj::class.java},payloadObj:$payloadObj]")
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("convert to bytes failed.", e)
        }
        return payloads
    }

    private fun doConvert(payload: Any, headers: MessageHeaders): Message<*> {
        val message =
            if (this.messageConverter is SmartMessageConverter) this.messageConverter.toMessage(
                payload,
                headers,
                null
            ) else this.messageConverter.toMessage(payload, headers)
        if (message == null) {
            val payloadType = payload.javaClass.name
            val contentType = headers[MessageHeaders.CONTENT_TYPE]
            throw MessageConversionException(
                ("Unable to convert payload with type='" + payloadType +
                        "', contentType='" + contentType + "', converter=[" + this.messageConverter) + "]"
            )
        }
        val builder = MessageBuilder.fromMessage(message)
        builder.setHeaderIfAbsent(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.TEXT_PLAIN)
        return builder.build()
    }

    @Throws(MQClientException::class)
    private fun initRocketMQPushConsumer() {
        // require(!(rocketMQListener == null && rocketMQReplyListener == null)) { "Property 'rocketMQListener' or 'rocketMQReplyListener' is required" }


        val rpcHook: RPCHook? = RocketMQUtil.getRPCHookByAkSk(
            applicationContext.environment,
            accessKey, secretKey
        )
        if (Objects.nonNull(rpcHook)) {
            consumer = DefaultMQPushConsumer(
                consumerGroup, rpcHook, AllocateMessageQueueAveragely(),
                enableMsgTrace, customizedTraceTopic?.let {
                    applicationContext.environment.resolveRequiredPlaceholders(it)
                }
            )
            consumer!!.isVipChannelEnabled = false
        } else {
            log.debug("Access-key or secret-key not configure in {}.", this)
            consumer = DefaultMQPushConsumer(
                consumerGroup, enableMsgTrace,
                customizedTraceTopic?.let {
                    applicationContext.environment.resolveRequiredPlaceholders(it)
                }
            )
        }
        consumer!!.namespaceV2 = namespace

        val customizedNameServer: String = this.applicationContext.environment
            .resolveRequiredPlaceholders(nameServer)
        consumer!!.setNamesrvAddr(customizedNameServer)
        if (accessChannel != null) {
            consumer!!.accessChannel = accessChannel
        }

        consumer!!.consumeThreadMax = consumeThreadMax
        consumer!!.consumeThreadMin = consumeThreadNumber
        consumer!!.consumeTimeout = consumeTimeout
        consumer!!.maxReconsumeTimes = maxReconsumeTimes
        consumer!!.awaitTerminationMillisWhenShutdown = awaitTerminationMillisWhenShutdown
        consumer!!.instanceName = instanceName
        when (messageModel) {
            MessageModel.BROADCASTING -> consumer!!.messageModel = MessageModel.BROADCASTING
            MessageModel.CLUSTERING -> consumer!!.messageModel = MessageModel.CLUSTERING
            else -> throw IllegalArgumentException("Property 'messageModel' was wrong.")
        }
        when (selectorType) {
            SelectorType.TAG -> consumer!!.subscribe(topic, selectorExpression)
            SelectorType.SQL92 -> consumer!!.subscribe(topic, MessageSelector.bySql(selectorExpression))
            else -> throw IllegalArgumentException("Property 'selectorType' was wrong.")
        }
        when (consumeMode) {
            ConsumeMode.ORDERLY -> consumer!!.messageListener = DefaultMessageListenerOrderly()
            ConsumeMode.CONCURRENTLY -> consumer!!.messageListener = DefaultMessageListenerConcurrently()
            else -> throw IllegalArgumentException("Property 'consumeMode' was wrong.")
        }
        //if String is not is equal "true" TLS mode will represent the as default value false
        consumer!!.setUseTLS(tlsEnable)

        log.info("Consumer on {} start", topic)

//        if (rocketMQListener is RocketMQPushConsumerLifecycleListener) {
//            (rocketMQListener as RocketMQPushConsumerLifecycleListener).prepareStart(consumer)
//        } else if (rocketMQReplyListener is RocketMQPushConsumerLifecycleListener) {
//            (rocketMQReplyListener as RocketMQPushConsumerLifecycleListener).prepareStart(consumer)
//        }
    }


}