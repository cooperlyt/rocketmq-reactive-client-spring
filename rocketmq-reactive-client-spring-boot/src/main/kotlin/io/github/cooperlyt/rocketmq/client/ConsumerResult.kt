package io.github.cooperlyt.rocketmq.client

import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus

enum class ConsumerResult(val concurrentlyStatus: ConsumeConcurrentlyStatus,
                          val orderlyStatus: ConsumeOrderlyStatus) {
    SUCCESS(ConsumeConcurrentlyStatus.CONSUME_SUCCESS, ConsumeOrderlyStatus.SUCCESS),
    FAILURE(ConsumeConcurrentlyStatus.RECONSUME_LATER, ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT)
}