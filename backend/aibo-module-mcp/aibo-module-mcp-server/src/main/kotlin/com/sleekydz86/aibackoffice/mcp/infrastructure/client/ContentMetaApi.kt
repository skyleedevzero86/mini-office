package com.sleekydz86.aibackoffice.mcp.infrastructure.client

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange("/api")
interface ContentMetaApi {

    @GetExchange("/catalog/titles/page")
    fun titles(
        @RequestParam(required = false) query: String?,
        @RequestParam lang: String,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) genre: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam page: Int,
        @RequestParam size: Int,
    ): Map<String, Any?>

    @GetExchange("/catalog/titles/{titleId}")
    fun title(@PathVariable titleId: Long): Map<String, Any?>

    @GetExchange("/catalog/by-external/{provider}/{externalId}")
    fun byExternalId(@PathVariable provider: String, @PathVariable externalId: String): Map<String, Any?>

    @GetExchange("/admin/tmdb/search")
    fun tmdbSearch(
        @RequestHeader(WORKER) worker: String,
        @RequestParam type: String,
        @RequestParam query: String,
        @RequestParam(required = false) year: Int?,
    ): List<Map<String, Any?>>

    @PostExchange("/admin/catalog/external-ids")
    fun externalId(@RequestHeader(WORKER) worker: String, @RequestBody command: ExternalIdRequest): ResponseEntity<Void>

    @PostExchange("/admin/catalog/localizations")
    fun localization(@RequestHeader(WORKER) worker: String, @RequestBody command: LocalizationRequest): ResponseEntity<Void>

    @PostExchange("/admin/tmdb/titles/{titleId}/apply")
    fun applyTmdb(@RequestHeader(WORKER) worker: String, @PathVariable titleId: Long): Map<String, Any?>

    @GetExchange("/admin/dashboard/quality/latest")
    fun qualityLatest(@RequestHeader(WORKER) worker: String): Any?

    @GetExchange("/admin/dashboard/verify/latest")
    fun verifyLatest(@RequestHeader(WORKER) worker: String): Any?

    @GetExchange("/admin/migration/read-source")
    fun readSource(@RequestHeader(WORKER) worker: String): Map<String, Any?>

    @GetExchange("/admin/migration/runs")
    fun migrationRuns(@RequestHeader(WORKER) worker: String, @RequestParam limit: Int): List<Map<String, Any?>>

    companion object {
        const val WORKER = "X-Worker"
        const val ADMIN_TOKEN = "X-Admin-Token"
    }
}

data class ExternalIdRequest(
    val operation: String,
    val entityType: String,
    val entityId: Long,
    val provider: String,
    val externalId: String,
)

data class LocalizationRequest(
    val operation: String,
    val titleId: Long,
    val langCode: String,
    val localTitle: String?,
    val overview: String?,
)
