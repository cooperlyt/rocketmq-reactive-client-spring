package io.github.cooperlyt.rocketmq.client.support

import io.github.cooperlyt.rocketmq.client.TypedConsumer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmptyDeferred
import java.lang.reflect.ParameterizedType
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer
import kotlin.reflect.jvm.internal.impl.load.kotlin.JvmType


@SpringBootTest(classes = [Application::class])
class FunctionTest{

    @Autowired
    lateinit var applicationContext: ApplicationContext

    data class Result(val result: String, val replay: Any? = null)


    @Test
    fun test(){


        val consumerFunction = applicationContext.getBean("testConsumer1")

        //println(consumerFunction::class.java.genericInterfaces[0]) //.typeParameters[0].name

        if (consumerFunction is TypedConsumer<*>){
            println(consumerFunction.type)
        }

        println(consumerFunction::class.java.genericInterfaces[0].typeName)

        val genericInterface = consumerFunction::class.java.genericInterfaces[0] as ParameterizedType
        val actualTypeArguments = genericInterface.actualTypeArguments
        val firstTypeArgument = actualTypeArguments[0]
        println(firstTypeArgument)


        val isMessage = firstTypeArgument is ParameterizedType && firstTypeArgument.rawType == Message::class.java
        println(isMessage)

        if (isMessage) {
            val messageType = firstTypeArgument as ParameterizedType
            val messageGenericType = messageType.actualTypeArguments[0]
            println(messageGenericType)
        }

//        val targetFunction = consumerFunction as (Any) -> Mono<Void>
//
//        Flux.create(messageEmitter())
//            .flatMap { (consumerFunction.invoke(MessageBuilder.withPayload(it).build()) as Mono<Any>)
//                .defaultIfEmpty("success")
//                .doOnNext { if (it is String) println("return string $it" ) else println("return replay $it")  }
//
//            }
//
//
//            .then()
//            .subscribe()


    }




    private var c: AtomicLong = AtomicLong(0)

    private fun messageEmitter() : Consumer<FluxSink<Long>> {
        return Consumer { sink ->
            val thread = Thread {
                while (c.get() < 2)

                                sink.next(c.incrementAndGet())

                sink.complete()


            }

            //thread.setUncaughtExceptionHandler { _, e ->   logger.error("$targetName - parse events has an error", e)}
            thread.start()
        }
    }
}