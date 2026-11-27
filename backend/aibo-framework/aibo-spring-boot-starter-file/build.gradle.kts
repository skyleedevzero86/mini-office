plugins {
    `java-library`
    id("aibo.kotlin-module")
}

dependencies {
    api(project(":aibo-framework:aibo-common"))
    api("software.amazon.awssdk:s3")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
}
