import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension

plugins {
    id("aibo.kotlin-module")
    id("org.jetbrains.kotlin.plugin.jpa")
}

extensions.configure<AllOpenExtension> {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
