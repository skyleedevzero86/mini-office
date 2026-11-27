package com.sleekydz86.aibackoffice.framework.file.core

import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.framework.file.config.AiboFileProperties
import java.io.InputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest

class S3FileStorage(
    private val s3: S3Client,
    private val presigner: S3Presigner,
    private val properties: AiboFileProperties,
) : FileStorage {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun put(key: String, contentType: String, sizeBytes: Long, content: InputStream): StoredFile {
        val request = PutObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key)
            .contentType(contentType)
            .contentLength(sizeBytes)
            .build()
        try {
            s3.putObject(request, RequestBody.fromInputStream(content, sizeBytes))
        } catch (exception: SdkException) {
            log.warn("파일 저장소에 올리지 못했습니다. 키={} 이유={}", key, exception.message)
            throw BusinessException(ErrorCode.UPSTREAM_FAILED, "파일 저장소에 올리지 못했습니다.")
        }
        return StoredFile(key, contentType, sizeBytes)
    }

    override fun downloadLink(key: String, fileName: String, ttl: Duration?): DownloadLink {
        val duration = ttl ?: properties.downloadTtl
        val encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20")
        val get = GetObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key)
            .responseContentDisposition("attachment; filename*=UTF-8''$encoded")
            .build()
        val presigned = presigner.presignGetObject(
            GetObjectPresignRequest.builder().signatureDuration(duration).getObjectRequest(get).build(),
        )
        return DownloadLink(presigned.url(), presigned.expiration() ?: Instant.now().plus(duration))
    }

    override fun delete(key: String) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket).key(key).build())
        } catch (exception: SdkException) {
            log.warn("파일 저장소에서 지우지 못했습니다. 키={} 이유={}", key, exception.message)
            throw BusinessException(ErrorCode.UPSTREAM_FAILED, "파일 저장소에서 지우지 못했습니다.")
        }
    }
}
