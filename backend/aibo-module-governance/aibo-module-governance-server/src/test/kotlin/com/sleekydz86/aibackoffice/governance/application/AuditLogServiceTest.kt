package com.sleekydz86.aibackoffice.governance.application

import com.sleekydz86.aibackoffice.common.ActorType
import com.sleekydz86.aibackoffice.common.AuditResult
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Permissions
import com.sleekydz86.aibackoffice.common.Zones
import com.sleekydz86.aibackoffice.framework.audit.core.AuditRecorder
import com.sleekydz86.aibackoffice.framework.file.config.AiboFileProperties
import com.sleekydz86.aibackoffice.framework.file.core.DownloadLink
import com.sleekydz86.aibackoffice.framework.file.core.FileRules
import com.sleekydz86.aibackoffice.framework.file.core.FileStorage
import com.sleekydz86.aibackoffice.framework.file.core.StoredFile
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.framework.security.core.TokenType
import com.sleekydz86.aibackoffice.framework.web.core.list.ListQuery
import com.sleekydz86.aibackoffice.governance.domain.ActionCount
import com.sleekydz86.aibackoffice.governance.domain.AuditLog
import com.sleekydz86.aibackoffice.governance.domain.AuditLogFilter
import com.sleekydz86.aibackoffice.governance.infrastructure.persistence.AuditLogStore
import java.io.InputStream
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

class AuditLogServiceTest {

    private val now = LocalDateTime.of(2026, 12, 4, 10, 0)
    private val clock = Clock.fixed(now.atZone(Zones.SEOUL).toInstant(), Zones.SEOUL)
    private val store = mock<AuditLogStore>()
    private val files = mock<FileStorage>()
    private val audit = mock<AuditRecorder>()
    private val jsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    private fun service(storage: FileStorage? = files): AuditLogService {
        val provider = mock<ObjectProvider<FileStorage>>()
        whenever(provider.ifAvailable).thenReturn(storage)
        return AuditLogService(store, jsonMapper, provider, FileRules(AiboFileProperties()), audit, clock)
    }

    private fun user(perms: Set<String> = setOf(Permissions.AUDIT_READ)) =
        LoginUser("9", "감사 담당", null, emptySet(), perms, emptySet(), 0, null, TokenType.USER, null, ActorType.HUMAN, "token")

    private fun row(reason: String?) = AuditLog(
        1, "trace1", ActorType.HUMAN, "7", null, "operation", "schedule.publish", "schedule_slot", "10",
        null, "{\"status\":\"PUBLISHED\"}", AuditResult.SUCCESS, reason, 55, now,
    )

    @Test
    @DisplayName("받은 감사 기록을 서울 시각과 JSON 값으로 바꿔 저장하고 형식이 틀린 것은 건너뛴다")
    fun ingestsEvents() {
        // given
        val good = mapOf(
            "traceId" to "abc", "actorType" to "AGENT", "actorId" to "7", "service" to "operation", "action" to "schedule.draft.create",
            "result" to "SUCCESS", "afterValue" to mapOf("id" to 11), "approvalId" to 55, "occurredAt" to "2026-12-04T01:00:00Z",
        )
        val bad = mapOf("actorType" to "ROBOT", "actorId" to "7", "service" to "x", "action" to "y", "result" to "SUCCESS")

        // when
        val saved = service().ingest(listOf(good, bad))

        // then
        assertEquals(1, saved)
        val rows = argumentCaptor<List<AuditLog>>()
        verify(store).insertAll(rows.capture())
        val log = rows.firstValue.single()
        assertEquals(ActorType.AGENT, log.actorType)
        assertEquals("{\"id\":11}", log.afterJson)
        assertEquals(55L, log.approvalId)
        assertEquals(LocalDateTime.of(2026, 12, 4, 10, 0), log.createdAt)
    }

    @Test
    @DisplayName("outbox 에서 온 JSON 문자열도 같은 방식으로 저장한다")
    fun ingestsPayloadStrings() {
        // when
        val saved = service().ingestPayloads(
            listOf("{\"actorType\":\"HUMAN\",\"actorId\":\"4\",\"service\":\"governance\",\"action\":\"approval.approve\",\"result\":\"SUCCESS\"}", "깨진 값"),
        )

        // then
        assertEquals(1, saved)
    }

    @Test
    @DisplayName("audit:read 권한이 없으면 검색할 수 없다")
    fun searchNeedsAuditRead() {
        // when
        val error = assertThrows<BusinessException> {
            service().search(user(setOf(Permissions.ISSUE_READ)), AuditLogFilter(), ListQuery())
        }

        // then
        assertEquals(ErrorCode.PERMISSION_DENIED, error.code)
    }

    @Test
    @DisplayName("CSV 칸은 수식으로 실행되지 않게 막고 쉼표와 따옴표는 감싼다")
    fun csvCellsAreSafe() {
        // then
        assertEquals("'=HYPERLINK(\"x\")".let { "\"" + it.replace("\"", "\"\"") + "\"" }, AuditCsv.cell("=HYPERLINK(\"x\")"))
        assertEquals("'+1", AuditCsv.cell("+1"))
        assertEquals("'@cmd", AuditCsv.cell("@cmd"))
        assertEquals("\"a,b\"", AuditCsv.cell("a,b"))
        assertEquals("plain", AuditCsv.cell("plain"))
        assertEquals("", AuditCsv.cell(null))
    }

    @Test
    @DisplayName("CSV 는 엑셀에서 한글이 깨지지 않게 앞에 BOM 을 붙이고 머리줄과 기록을 담는다")
    fun csvHasBomAndRows() {
        // when
        val bytes = AuditCsv.write(listOf(row("권리 문제, 확인")))

        // then
        assertEquals(listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.take(3))
        val lines = String(bytes.drop(3).toByteArray(), Charsets.UTF_8).trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("id,created_at,trace_id"))
        assertTrue(lines[1].contains("\"권리 문제, 확인\""))
    }

    @Test
    @DisplayName("내보내기는 CSV 를 파일 저장소에 올리고 서명된 주소를 돌려주며 감사 기록을 남긴다")
    fun exportUploadsCsv() {
        // given
        whenever(store.count(any())).thenReturn(1)
        whenever(store.search(any(), eq(0), eq(10_000), eq(false))).thenReturn(listOf(row(null)))
        whenever(files.put(any(), eq("text/csv"), any(), any<InputStream>())).thenAnswer { StoredFile(it.getArgument(0), "text/csv", it.getArgument(2)) }
        whenever(files.downloadLink(any(), any(), anyOrNull()))
            .thenReturn(DownloadLink(URI.create("http://localhost:9000/e.csv?sig=1").toURL(), Instant.parse("2026-12-04T01:10:00Z")))

        // when
        val view = service().export(user(), AuditLogFilter())

        // then
        assertEquals(1, view.rows)
        assertEquals("audit-logs-20261204-100000.csv", view.fileName)
        assertEquals("http://localhost:9000/e.csv?sig=1", view.downloadUrl)
        val key = argumentCaptor<String>()
        verify(files).put(key.capture(), eq("text/csv"), any(), any<InputStream>())
        assertTrue(key.firstValue.startsWith("exports/audit/"))
    }

    @Test
    @DisplayName("파일 저장소 설정이 없으면 내보내기는 503 으로 알려 준다")
    fun exportNeedsStorage() {
        // when
        val error = assertThrows<BusinessException> { service(storage = null).export(user(), AuditLogFilter()) }

        // then
        assertEquals(ErrorCode.SERVICE_NOT_CONFIGURED, error.code)
        verify(store, never()).search(any(), any(), any(), any())
    }

    @Test
    @DisplayName("자동화 후보는 최근 기간 사람이 한 작업을 횟수와 사람 수로 알려 준다")
    fun automationCandidates() {
        // given
        whenever(store.humanActions(now.minusDays(7), 10)).thenReturn(listOf(ActionCount("issue.assign", 42, 3, now.minusHours(1))))

        // when
        val candidates = service().automationCandidates(user(), 7, 10)

        // then
        assertEquals("issue.assign", candidates.single().action)
        assertEquals(42L, candidates.single().count)
        assertTrue(candidates.single().suggestion.contains("3명이 42번"))
    }

    @Test
    @DisplayName("자동화 후보 기간과 개수는 정해진 범위 안이어야 한다")
    fun automationCandidateRange() {
        // when
        val days = assertThrows<BusinessException> { service().automationCandidates(user(), 0, 10) }
        val limit = assertThrows<BusinessException> { service().automationCandidates(user(), 7, 51) }

        // then
        assertEquals(ErrorCode.VALIDATION_FAILED, days.code)
        assertEquals(ErrorCode.VALIDATION_FAILED, limit.code)
    }
}
