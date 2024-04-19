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

    implementation("org.apache.rocketmq:rocketmq-spring-boot-starter:2.3.0")
    implementation(project(":rocketmq-reactive-client-spring-boot"))
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


kotlin {
    jvmToolchain(21)
}