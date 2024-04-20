package io.github.cooperlyt.rocketmq.client

import java.lang.reflect.ParameterizedType
import java.util.function.Consumer

abstract class TypedConsumer<T>(private val consumer: Consumer<T>): Consumer<T> {

    val type: ParameterizedType

    init {
        val superClass = this::class.java.genericSuperclass
        type = if (superClass is ParameterizedType) {
            superClass
        } else {
            throw RuntimeException("Missing type parameter.")
        }
    }


    override fun accept(t: T) {
        consumer.accept(t)
    }
}