package com.sleekydz86.aibackoffice.framework.file.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("aibo.file")
data class AiboFileProperties(
    val bucket: String = "",
    val region: String = "ap-northeast-2",
    val endpoint: String = "",
    val pathStyle: Boolean = false,
    val accessKey: String = "",
    val secretKey: String = "",
    val downloadTtl: Duration = Duration.ofMinutes(10),
    val maxSizeBytes: Long = 10L * 1024 * 1024,
    val allowedContentTypes: List<String> = listOf(
        "image/png",
        "image/jpeg",
        "image/gif",
        "application/pdf",
        "text/plain",
        "text/csv",
        "application/json",
        "application/zip",
    ),
)
