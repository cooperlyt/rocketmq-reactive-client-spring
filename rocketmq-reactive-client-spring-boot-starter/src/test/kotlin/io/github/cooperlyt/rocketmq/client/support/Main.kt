package io.github.cooperlyt.rocketmq.client.support

import io.github.cooperlyt.rocketmq.client.ConsumerResult
import io.github.cooperlyt.rocketmq.client.TypedConsumer
import io.github.cooperlyt.rocketmq.client.TypedReplayConsumer
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import reactor.core.publisher.Mono
import java.util.function.Consumer
import java.util.function.Function


@SpringBootApplication
@Configuration
class Application {



    @Bean
    fun contractCreateChannel(): (Message<String>) -> Mono<Void> = { str ->
        Mono.just("${str.payload}").then()
    }

//    @Bean
//    fun testChannel(): (Message<String>) -> Mono<Void> = { str ->
//        Mono.just("${str.payload}").doOnNext(::println).then(Mono.error(RuntimeException("test")))
//    }

    @Bean
    fun testChannel(): (Message<String>) -> Mono<ConsumerResult> = { str ->
        Mono.just("${str.payload}").doOnNext(::println).thenReturn(ConsumerResult.SUCCESS)
    }


    @Bean
    fun testConsumer1(): Consumer<Message<String>> = object: TypedConsumer<Message<String>>(Consumer { msg ->
            println("Receive New Create Messages: ${msg.payload}")
        }){}


    @Bean
    fun testFunction1() = object: TypedReplayConsumer<String>(Function { msg ->
            Mono.just("${msg}").thenReturn("asdf")
        }) {}



    //FAIL
    @Bean
    fun testConsumer() = Consumer { msg: Message<Long> ->
            println("Receive New Create Messages: ${msg.payload}")

    }

    //FAIL
    @Bean
    fun testFunction(): Function<Message<Long>,Mono<Void>> = Function { msg ->
        Mono.just("${msg.payload}").then()
    }


}




fun main(args: Array<String>) {
    runApplication<Application>(*args)
}