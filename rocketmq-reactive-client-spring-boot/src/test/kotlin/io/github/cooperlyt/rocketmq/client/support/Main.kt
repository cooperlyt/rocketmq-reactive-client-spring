package io.github.cooperlyt.rocketmq.client.support

import io.github.cooperlyt.rocketmq.client.ConsumerResult
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


@SpringBootApplication
@Configuration
class Application {



    @Bean
    fun contractCreateChannel(): (Message<Long>) -> Mono<Void> = { str ->
        Mono.just("${str.payload}").then()
    }

//    @Bean
//    fun testChannel(): (Message<String>) -> Mono<Void> = { str ->
//        Mono.just("${str.payload}").doOnNext(::println).then(Mono.error(RuntimeException("test")))
//    }

    @Bean
    fun testChannel(): (Message<String>) -> Mono<Void> = { str ->
        Mono.just("${str.payload}").doOnNext(::println).then()
    }

}




fun main(args: Array<String>) {
    runApplication<Application>(*args)
}