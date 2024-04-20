package io.github.cooperlyt.rocketmq.client

import reactor.core.publisher.Mono
import java.lang.reflect.ParameterizedType
import java.util.function.Function

abstract class TypedReplayConsumer<T>(private val function: Function<T, Mono<*>>): Function<T,Mono<*>> {


    val type: ParameterizedType

    init {
        val superClass = this::class.java.genericSuperclass
        type = if (superClass is ParameterizedType) {
            superClass
        } else {
            throw RuntimeException("Missing type parameter.")
        }
    }


     override fun apply(t: T): Mono<*> {
        return function.apply(t)
     }
 }