package com.sleekydz86.aibackoffice.framework.file.core

import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.framework.file.config.AiboFileProperties
import java.io.ByteArrayInputStream
import java.net.URI
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest

class S3FileStorageTest {

    private val properties = AiboFileProperties(bucket = "aibo-files", downloadTtl = Duration.ofMinutes(5))
    private val s3 = mock<S3Client>()
    private val presigner = mock<S3Presigner>()
    private val storage = S3FileStorage(s3, presigner, properties)

    @Test
    @DisplayName("설정한 버킷과 위치, 종류, 크기로 파일을 올린다")
    fun putsIntoBucket() {
        // when
        storage.put("issues/3/k-a.png", "image/png", 3, ByteArrayInputStream(byteArrayOf(1, 2, 3)))

        // then
        val request = argumentCaptor<PutObjectRequest>()
        verify(s3).putObject(request.capture(), any<RequestBody>())
        assertEquals("aibo-files", request.firstValue.bucket())
        assertEquals("issues/3/k-a.png", request.firstValue.key())
        assertEquals("image/png", request.firstValue.contentType())
        assertEquals(3L, request.firstValue.contentLength())
    }

    @Test
    @DisplayName("저장소 호출이 실패하면 502 연동 실패로 바꿔 알린다")
    fun wrapsStorageFailure() {
        // given
        whenever(s3.putObject(any<PutObjectRequest>(), any<RequestBody>())).thenThrow(SdkClientException.create("연결 거부"))

        // when
        val error = assertThrows<BusinessException> {
            storage.put("k", "image/png", 1, ByteArrayInputStream(byteArrayOf(1)))
        }

        // then
        assertEquals(ErrorCode.UPSTREAM_FAILED, error.code)
    }

    @Test
    @DisplayName("내려받기 주소는 정한 유효 시간으로 서명하고 한글 파일 이름을 담는다")
    fun presignsDownload() {
        // given
        val expires = Instant.parse("2026-12-04T01:05:00Z")
        val presigned = mock<PresignedGetObjectRequest> {
            on { url() } doReturn URI.create("http://localhost:9000/aibo-files/k?sig=1").toURL()
            on { expiration() } doReturn expires
        }
        whenever(presigner.presignGetObject(any<GetObjectPresignRequest>())).thenReturn(presigned)

        // when
        val link = storage.downloadLink("k", "캡처 화면.png")

        // then
        val request = argumentCaptor<GetObjectPresignRequest>()
        verify(presigner).presignGetObject(request.capture())
        assertEquals(Duration.ofMinutes(5), request.firstValue.signatureDuration())
        assertEquals("aibo-files", request.firstValue.getObjectRequest().bucket())
        assertTrue(request.firstValue.getObjectRequest().responseContentDisposition().contains("%EC%BA%A1%EC%B2%98%20"))
        assertEquals(expires, link.expiresAt)
    }
}
