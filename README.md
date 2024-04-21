#RocketMQ-Reactive-Consumer-Spring

This project aims to help developers quickly integrate [RocketMQ-Spring](https://github.com/apache/rocketmq-spring) with Reactive.

##Features 

- [x] Consuming messages from RocketMQ with Reactive
- [x] Using Spring content ObjectMapper deserialization


## Usage
使用和配置方式类似于 Spring Cloud Stream RocketMQ Binder，但解决一个Stream不能处理流中出现异常无法报告消息失败的[问题](https://github.com/spring-cloud/spring-cloud-stream/issues/2892) 。

application.yml
```yml
rocketmq:
  name-server: 192.168.1.21:9876
  producer:
    group: group-test-producer
  bindings:
    testFunction1-in-0:
      consumer:
        topic: test-topic
        group: group-test-consumer
        retry:
          maxAttempts: 3
          backoff:
            delay: 10000
            multiplier: 0.4
```

consumer
```kotlin
    @Bean
    fun contractCreateChannel(): (Message<String>) -> Mono<Void> = { str ->
        Mono.just("${str.payload}").then()
    }


    @Bean
    fun testChannel(): (Message<String>) -> Mono<ConsumerResult> = { str ->
        Mono.just("${str.payload}").doOnNext(::println).thenReturn(ConsumerResult.SUCCESS)
    }

    @Bean
    fun testReplayChannel(): (Message<String>) -> Mono<ReplayData> = { str ->
        Mono.just("${str.payload}").doOnNext(::println).thenReturn(ReplayData())
    }


    @Bean
    fun testConsumer1(): Consumer<Message<String>> = object: TypedConsumer<Message<String>>(Consumer { msg ->
            println("Receive New Create Messages: ${msg.payload}")
        }){}


    @Bean
    fun testFunction1() = object: TypedReplayConsumer<String>(Function { msg ->
            Mono.just("${msg}").thenReturn("replay")
        }) {}

```

## [Samples](https://github.com/cooperlyt/rocketmq-reactive-client-spring/tree/master/rocketmq-reactive-client-spring-boot-starter/src/test/kotlin/io/github/cooperlyt/rocketmq/client/support)

