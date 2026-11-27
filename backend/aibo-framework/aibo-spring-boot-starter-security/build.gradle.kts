plugins {
    `java-library`
    id("aibo.kotlin-module")
}

dependencies {
    api(project(":aibo-framework:aibo-spring-boot-starter-web"))
    api("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
}
