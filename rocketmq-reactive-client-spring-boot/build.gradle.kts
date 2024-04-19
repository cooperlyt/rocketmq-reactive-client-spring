plugins {
    kotlin("jvm")
}

group = "io.github.cooperlyt"
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {

    testImplementation("org.apache.rocketmq:rocketmq-spring-boot-starter:2.3.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}