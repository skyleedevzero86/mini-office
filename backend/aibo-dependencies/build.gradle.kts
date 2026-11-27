plugins { `java-platform` }

javaPlatform { allowDependencies() }

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
    api(platform("org.springframework.cloud:spring-cloud-dependencies:2025.1.5"))
    api(platform("org.springframework.ai:spring-ai-bom:2.0.1"))
    constraints {
        api("io.modelcontextprotocol.sdk:mcp:2.0.0")
    }
}
