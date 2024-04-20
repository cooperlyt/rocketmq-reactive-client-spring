import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication


plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "io.github.cooperlyt"
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {

    api("org.apache.rocketmq:rocketmq-spring-boot-starter:2.3.0"){
        exclude(group = "com.alibaba", module = "fastjson") // fast json BigDecimal bug
    }
    api(project(":rocketmq-reactive-client-spring-boot"))
    api("com.alibaba:fastjson:2.0.49")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test {
    useJUnitPlatform()
}


configure<PublishingExtension> {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        mavenLocal() // Publish to local Maven repository
    }
}


tasks.getByName<Jar>("jar") {
    enabled = true
    // Remove `plain` postfix from jar file name
    archiveClassifier.set("")
}

kotlin {
    jvmToolchain(21)
}