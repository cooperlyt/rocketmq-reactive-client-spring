package io.github.cooperlyt.rocketmq.client

import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus

enum class ConsumerResult {
    SUCCESS,
    FAILURE
}