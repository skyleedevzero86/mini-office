plugins {
    id("aibo.kotlin-jpa")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":aibo-framework:aibo-spring-boot-starter-security"))
    implementation(project(":aibo-framework:aibo-spring-boot-starter-redis"))
    implementation("org.springframework.ai:spring-ai-client-chat")
    implementation("io.modelcontextprotocol.sdk:mcp")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-mysql")
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mysql")
}
