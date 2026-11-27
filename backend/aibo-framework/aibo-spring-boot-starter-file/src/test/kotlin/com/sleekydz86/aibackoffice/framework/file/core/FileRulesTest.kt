package com.sleekydz86.aibackoffice.framework.file.core

import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.framework.file.config.AiboFileProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FileRulesTest {

    private val rules = FileRules(AiboFileProperties(maxSizeBytes = 1000, allowedContentTypes = listOf("image/png", "text/plain")))

    @Test
    @DisplayName("허용한 종류와 크기의 파일은 통과하고 종류는 소문자로 맞춘다")
    fun acceptsAllowedFile() {
        // when
        val checked = rules.check("캡처 화면.png", "IMAGE/PNG; charset=binary", 500)

        // then
        assertEquals("캡처_화면.png", checked.fileName)
        assertEquals("image/png", checked.contentType)
        assertEquals(500L, checked.sizeBytes)
    }

    @Test
    @DisplayName("크기를 넘거나 빈 파일이면 400으로 거절한다")
    fun rejectsSize() {
        // when
        val tooLarge = assertThrows<BusinessException> { rules.check("a.png", "image/png", 1001) }
        val empty = assertThrows<BusinessException> { rules.check("a.png", "image/png", 0) }

        // then
        assertEquals(ErrorCode.VALIDATION_FAILED, tooLarge.code)
        assertEquals(ErrorCode.VALIDATION_FAILED, empty.code)
    }

    @Test
    @DisplayName("허용하지 않은 종류나 종류를 모르는 파일은 거절한다")
    fun rejectsType() {
        // when
        val script = assertThrows<BusinessException> { rules.check("a.html", "text/html", 10) }
        val unknown = assertThrows<BusinessException> { rules.check("a.png", null, 10) }

        // then
        assertEquals(ErrorCode.VALIDATION_FAILED, script.code)
        assertEquals(ErrorCode.VALIDATION_FAILED, unknown.code)
    }

    @Test
    @DisplayName("경로가 섞인 이름은 마지막 이름만 남기고 위험한 글자는 바꾼다")
    fun sanitizesName() {
        // then
        assertEquals("passwd", FileRules.safeName("../../etc/passwd"))
        assertEquals("report.txt", FileRules.safeName("C:\\temp\\report.txt"))
        assertEquals("hidden", FileRules.safeName(".hidden"))
        assertEquals("a_b_c.txt", FileRules.safeName("a<b>c.txt"))
    }

    @Test
    @DisplayName("이름이 비었거나 쓸 글자가 없으면 거절한다")
    fun rejectsEmptyName() {
        // when
        val blank = assertThrows<BusinessException> { FileRules.safeName("  ") }
        val onlyUnsafe = assertThrows<BusinessException> { FileRules.safeName("<>|") }

        // then
        assertEquals(ErrorCode.VALIDATION_FAILED, blank.code)
        assertEquals(ErrorCode.VALIDATION_FAILED, onlyUnsafe.code)
    }

    @Test
    @DisplayName("저장 위치는 앞부분 뒤에 겹치지 않는 값과 파일 이름을 붙인다")
    fun buildsUniqueKey() {
        // when
        val first = rules.key("/issues/3/", "a.png")
        val second = rules.key("issues/3", "a.png")

        // then
        assertTrue(first.startsWith("issues/3/"))
        assertTrue(first.endsWith("-a.png"))
        assertTrue(first != second)
    }
}
