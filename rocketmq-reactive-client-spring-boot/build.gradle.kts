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

    testImplementation("org.apache.rocketmq:rocketmq-spring-boot-starter:2.3.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
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

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}