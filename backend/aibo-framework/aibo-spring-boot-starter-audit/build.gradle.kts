plugins {
    `java-library`
    id("aibo.kotlin-module")
}

dependencies {
    api(project(":aibo-framework:aibo-spring-boot-starter-security"))
    api("org.springframework.boot:spring-boot-starter-jdbc")
}
