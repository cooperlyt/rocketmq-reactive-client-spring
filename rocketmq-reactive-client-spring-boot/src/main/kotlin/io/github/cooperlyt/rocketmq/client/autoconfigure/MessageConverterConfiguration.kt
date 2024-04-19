package io.github.cooperlyt.rocketmq.client.autoconfigure

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order


@Configuration
@ConditionalOnMissingBean(org.apache.rocketmq.spring.support.RocketMQMessageConverter::class)
@Order(Int.MAX_VALUE / 2)
class MessageConverterConfiguration {

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(MessageConverterConfiguration::class.java)
    }

    @Bean
    @ConditionalOnMissingBean(ObjectMapper::class)
    fun rocketMQMessageConverterDefault(): org.apache.rocketmq.spring.support.RocketMQMessageConverter {
        log.info("No custom ObjectMapper found, using default RocketMQMessageConverter")
        return io.github.cooperlyt.rocketmq.client.support.RocketMQMessageConverter(null)
    }

    @Bean
    @ConditionalOnBean(ObjectMapper::class)
    fun rocketMQMessageConverterRefer(objectMapper: ObjectMapper): org.apache.rocketmq.spring.support.RocketMQMessageConverter {
        log.info("Custom ObjectMapper found, using custom RocketMQMessageConverter")
        return io.github.cooperlyt.rocketmq.client.support.RocketMQMessageConverter(objectMapper)
    }
}