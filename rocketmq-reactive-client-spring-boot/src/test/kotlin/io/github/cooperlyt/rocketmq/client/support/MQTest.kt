package io.github.cooperlyt.rocketmq.client.support

import org.apache.rocketmq.spring.core.RocketMQTemplate
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.support.MessageBuilder
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicInteger


@SpringBootTest(classes = [Application::class])
class MQTest {

    @Autowired
    private val rocketMQTemplate: RocketMQTemplate? = null

    private val THREADS: Int = Runtime.getRuntime().availableProcessors() shl 1

    @Test
    fun test(){


        val control = AtomicInteger(-1)
        val uidSet: Set<Long> = ConcurrentSkipListSet()


        // Initialize threads
        val threadList: MutableList<Thread> = ArrayList(THREADS)
        for (i in 0 until 1) {
            val thread = Thread {
                rocketMQTemplate!!.send("test-topic",
                    MessageBuilder.withPayload("Hello, World! I'm from spring message").build())
                println("Send message to rocketmq success!")
                Thread.sleep(10000)
            }
            thread.name = "mq-generator-$i"

            threadList.add(thread)
            thread.start()
        }


        // Wait for worker done
        for (thread in threadList) {
            thread.join()
        }
    }
}