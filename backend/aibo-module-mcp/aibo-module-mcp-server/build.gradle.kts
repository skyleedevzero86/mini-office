plugins {
    id("aibo.kotlin-module")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":aibo-module-auth:aibo-module-auth-api"))
    implementation(project(":aibo-module-operation:aibo-module-operation-api"))
    implementation(project(":aibo-module-governance:aibo-module-governance-api"))
    implementation(project(":aibo-framework:aibo-spring-boot-starter-audit"))
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-mysql")
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
}
