plugins {
    `java-library`
    id("aibo.kotlin-module")
}

dependencies {
    api(project(":aibo-framework:aibo-common"))
    api("org.springframework.boot:spring-boot-starter-webmvc")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.boot:spring-boot-starter-restclient")
    api("org.springframework.boot:spring-boot-starter-actuator")
    api("org.springframework.boot:spring-boot-micrometer-tracing-brave")
    api("tools.jackson.module:jackson-module-kotlin")
    compileOnly("org.springframework.data:spring-data-commons")
    compileOnly("org.springframework:spring-tx")
}
