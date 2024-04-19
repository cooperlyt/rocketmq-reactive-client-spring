plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "rocketmq-reactive-client-spring"
include("rocketmq-reactive-client-spring-boot")
include("rocketmq-reactive-client-spring-boot-starter")
