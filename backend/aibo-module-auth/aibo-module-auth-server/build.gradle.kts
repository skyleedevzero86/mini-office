plugins {
    id("aibo.kotlin-jpa")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":aibo-module-auth:aibo-module-auth-api"))
    implementation(project(":aibo-framework:aibo-spring-boot-starter-audit"))
    implementation(project(":aibo-framework:aibo-spring-boot-starter-redis"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-mysql")
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mysql")
}
