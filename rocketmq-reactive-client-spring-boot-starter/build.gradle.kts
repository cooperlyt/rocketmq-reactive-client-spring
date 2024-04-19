plugins {
    kotlin("jvm")
}

group = "io.github.cooperlyt"
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {

    implementation("org.apache.rocketmq:rocketmq-spring-boot-starter:2.3.0")
    implementation(project(":rocketmq-reactive-client-spring-boot"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}