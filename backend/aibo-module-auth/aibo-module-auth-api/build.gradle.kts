plugins {
    `java-library`
    id("aibo.kotlin-module")
}

dependencies {
    api(project(":aibo-framework:aibo-common"))
    api("org.springframework:spring-web")
}
