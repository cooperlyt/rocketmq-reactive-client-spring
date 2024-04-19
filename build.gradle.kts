plugins {
    id("org.springframework.boot") version "3.1.10"
    id("io.spring.dependency-management") version "1.1.4"
    id("com.palantir.docker") version "0.35.0"
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.spring") version "1.9.23"
}

group = "io.github.cooperlyt"
version = "1.0.1"

repositories {
    mavenCentral()
}

subprojects {
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")

    ext["rocketmqVersion"] = "2.3.0"

    repositories {
        mavenCentral()
    }

    dependencies {
        implementation("org.apache.rocketmq:rocketmq-spring-boot:2.3.0"){
            exclude(group = "com.alibaba", module = "fastjson") // fast json BigDecimal bug
        }

        implementation("com.alibaba:fastjson:2.0.49")

//    implementation("org.apache.rocketmq:rocketmq-acl:${rocketmqVersion}")


        implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

        implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.2.2")

        implementation("org.springframework:spring-core")
        implementation("org.springframework:spring-messaging")
        implementation("org.springframework.boot:spring-boot-starter")
        implementation("org.jetbrains.kotlin:kotlin-reflect")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}