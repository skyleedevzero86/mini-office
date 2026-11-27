import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.spring")
}

group = "com.sleekydz86.aibackoffice"
version = "0.1.0-SNAPSHOT"

extensions.configure<KotlinJvmProjectExtension> {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
        javaParameters.set(true)
    }
}

dependencies {
    "implementation"(platform(project(":aibo-dependencies")))
    "testImplementation"(platform(project(":aibo-dependencies")))
    "implementation"("org.jetbrains.kotlin:kotlin-reflect")
    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    "testImplementation"("org.mockito.kotlin:mockito-kotlin:6.1.0")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform { if (name == "test") excludeTags("integration") }
    systemProperty("file.encoding", "UTF-8")
    testLogging { events("passed", "skipped", "failed") }
}

val sourceSets = extensions.getByType<SourceSetContainer>()
tasks.register<Test>("integrationTest") {
    description = "Integration tests that need Testcontainers MySQL"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
}
