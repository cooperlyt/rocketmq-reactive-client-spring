package io.github.cooperlyt.rocketmq.client.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.springframework.messaging.converter.ByteArrayMessageConverter
import org.springframework.messaging.converter.CompositeMessageConverter
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.converter.StringMessageConverter
import org.springframework.util.ClassUtils


class RocketMQMessageConverter(objectMapper: ObjectMapper?): org.apache.rocketmq.spring.support.RocketMQMessageConverter() {

    companion object {

        private var JACKSON_PRESENT: Boolean = false
        private var FASTJSON_PRESENT: Boolean = false

        init {
            val classLoader = RocketMQMessageConverter::class.java.classLoader
            JACKSON_PRESENT = ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", classLoader) &&
                    ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator", classLoader)
            FASTJSON_PRESENT = ClassUtils.isPresent("com.alibaba.fastjson.JSON", classLoader) &&
                    ClassUtils.isPresent("com.alibaba.fastjson.support.config.FastJsonConfig", classLoader)
        }
    }

    private var messageConverter: CompositeMessageConverter? = null

    init {
        val messageConverters: MutableList<org.springframework.messaging.converter.MessageConverter> =
            ArrayList()
        val byteArrayMessageConverter = ByteArrayMessageConverter()
        byteArrayMessageConverter.contentTypeResolver = null
        messageConverters.add(byteArrayMessageConverter)
        messageConverters.add(StringMessageConverter())
        if (JACKSON_PRESENT) {
            val converter = MappingJackson2MessageConverter()
            // 在 Spring context 中查找 ObjectMapper 如果找到则使用，否则创建一个新的
            if (objectMapper != null) {
                converter.objectMapper = objectMapper
            }else {
                val mapper: ObjectMapper = converter.objectMapper
                mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                mapper.registerModule(JavaTimeModule())
                converter.objectMapper = mapper
                messageConverters.add(converter)
            }
        }
        if (FASTJSON_PRESENT) {
            try {
                messageConverters.add(
                    ClassUtils.forName(
                        "com.alibaba.fastjson.support.spring.messaging.MappingFastJsonMessageConverter",
                        ClassUtils.getDefaultClassLoader()
                    ).getDeclaredConstructor().newInstance() as org.springframework.messaging.converter.MessageConverter
                )
            } catch (ignored: ClassNotFoundException) {
                //ignore this exception
            } catch (ignored: IllegalAccessException) {
            } catch (ignored: InstantiationException) {
            }
        }
        messageConverter = CompositeMessageConverter(messageConverters)
    }

    override fun getMessageConverter(): org.springframework.messaging.converter.MessageConverter? {
        return messageConverter
    }
}