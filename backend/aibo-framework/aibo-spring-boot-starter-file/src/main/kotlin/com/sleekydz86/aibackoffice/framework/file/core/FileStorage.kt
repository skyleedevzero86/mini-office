package com.sleekydz86.aibackoffice.framework.file.core

import java.io.InputStream
import java.net.URL
import java.time.Duration
import java.time.Instant

data class StoredFile(val key: String, val contentType: String, val sizeBytes: Long)

data class DownloadLink(val url: URL, val expiresAt: Instant)

interface FileStorage {

    fun put(key: String, contentType: String, sizeBytes: Long, content: InputStream): StoredFile

    fun downloadLink(key: String, fileName: String, ttl: Duration? = null): DownloadLink

    fun delete(key: String)
}
