package com.sleekydz86.aibackoffice.mcp.infrastructure.tool

import com.sleekydz86.aibackoffice.mcp.application.tool.ToolCall
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolList
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolOutput
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolSchemas
import com.sleekydz86.aibackoffice.mcp.infrastructure.client.DownstreamClients
import com.sleekydz86.aibackoffice.mcp.infrastructure.client.ExternalIdRequest
import com.sleekydz86.aibackoffice.mcp.infrastructure.client.LocalizationRequest
import org.springframework.stereotype.Component

object ContentValues {
    val PROVIDERS = setOf("TMDB_MOVIE", "TMDB_TV", "TMDB_PERSON", "IMDB", "KOBIS")
    val ENTITY_TYPES = setOf("TITLE", "SEASON", "EPISODE", "PERSON")
    val TMDB_TYPES = setOf("MOVIE", "TV")
    val OPERATIONS = setOf("C", "U", "D")
    const val DEFAULT_LANG = "ko"
    const val MAX_TEXT = 4000
}

@Component
class ContentSearchTitlesTool(private val clients: DownstreamClients) : BaseTool(
    "content_search_titles",
    "작품 검색",
    "content-meta 신규 메타에서 작품을 제목으로 찾는다. 읽기 원천이 NEW 일 때만 열린다.",
    ToolSchemas.obj(
        "query" to ToolSchemas.string("찾을 제목", 200),
        "lang" to ToolSchemas.string("제목 언어. 기본 ko", 10),
        "type" to ToolSchemas.string("작품 유형. 예 MOVIE, TV", 20),
        "genre" to ToolSchemas.string("장르 코드", 50),
        "status" to ToolSchemas.string("작품 상태", 30),
        ToolSchemas.limit(),
    ),
) {
    override fun execute(call: ToolCall): ToolOutput {
        val args = call.arguments
        val size = ToolList.limit(args.int("limit"))
        val page = clients.contentMeta().titles(
            args.string("query"),
            args.string("lang") ?: ContentValues.DEFAULT_LANG,
            args.string("type")?.uppercase(),
            args.string("genre"),
            args.string("status")?.uppercase(),
            0,
            size,
        )
        @Suppress("UNCHECKED_CAST")
        val items = page["items"] as? List<Map<String, Any?>> ?: emptyList()
        val total = (page["total"] as? Number)?.toLong() ?: items.size.toLong()
        return ToolResponses.listedUntrusted(ToolList.of(items, total), "작품 ${total}건 중 ${minOf(items.size, ToolList.MAX_ITEMS)}건을 찾았습니다.")
    }
}

@Component
class ContentGetTitleTool(private val clients: DownstreamClients) : BaseTool(
    "content_get_title",
    "작품 상세",
    "작품 번호로 시즌, 에피소드, 출연진, 외부 ID, 현지화까지 읽는다.",
    ToolSchemas.obj("titleId" to ToolSchemas.integer("작품 번호", 1), required = listOf("titleId")),
) {
    override fun execute(call: ToolCall): ToolOutput {
        val titleId = call.arguments.requireLong("titleId")
        val detail = clients.contentMeta().title(titleId)
        return ToolOutput(data = mapOf("titleId" to titleId), untrusted = detail, message = "작품 ${titleId}번을 읽었습니다.")
    }
}

@Component
class ContentFindByExternalIdTool(private val clients: DownstreamClients) : BaseTool(
    "content_find_by_external_id",
    "외부 ID로 작품 찾기",
    "TMDB, IMDb, KOBIS 같은 외부 표준 ID로 연결된 작품을 찾는다.",
    ToolSchemas.obj(
        "provider" to ToolSchemas.choice("제공처", ContentValues.PROVIDERS.sorted()),
        "externalId" to ToolSchemas.string("외부 ID. 예 TMDB_TV 는 1399, IMDB 는 tt0944947", 40),
        required = listOf("provider", "externalId"),
    ),
) {
    override fun execute(call: ToolCall): ToolOutput {
        val provider = call.arguments.requireChoice("provider", ContentValues.PROVIDERS)
        val externalId = call.arguments.requireText("externalId", 40)
        val detail = clients.contentMeta().byExternalId(provider, externalId)
        return ToolOutput(data = mapOf("provider" to provider, "externalId" to externalId, "titleId" to detail["id"]), untrusted = detail, message = "외부 ID로 작품을 찾았습니다.")
    }
}

@Component
class ContentSearchTmdbCandidatesTool(private val clients: DownstreamClients) : BaseTool(
    "content_search_tmdb_candidates",
    "TMDB 후보 찾기",
    "TMDB 에서 제목으로 연결 후보를 찾는다. 결과의 제목과 줄거리는 TMDB 글이다.",
    ToolSchemas.obj(
        "type" to ToolSchemas.choice("TMDB 유형", ContentValues.TMDB_TYPES.sorted()),
        "query" to ToolSchemas.string("찾을 제목", 200),
        "year" to ToolSchemas.integer("개봉 연도", 1900, 2100),
        ToolSchemas.limit(),
        required = listOf("query"),
    ),
) {
    override fun execute(call: ToolCall): ToolOutput {
        val args = call.arguments
        val candidates = clients.contentMeta().tmdbSearch(
            clients.worker(call.user),
            args.choice("type", ContentValues.TMDB_TYPES) ?: "MOVIE",
            args.requireText("query", 200),
            args.int("year"),
        )
        val list = ToolList.of(candidates.take(ToolList.limit(args.int("limit"))), candidates.size.toLong())
        return ToolResponses.listedUntrusted(list, "TMDB 후보 ${candidates.size}건을 찾았습니다.")
    }
}

@Component
class ContentLinkExternalIdTool(private val clients: DownstreamClients) : BaseTool(
    "content_link_external_id",
    "외부 ID 연결",
    "작품, 시즌, 에피소드, 인물에 외부 표준 ID를 새로 연결한다. 승인을 거친 뒤에만 실행된다. 이미 연결된 ID는 바꾸지 않는다.",
    ToolSchemas.obj(
        "entityType" to ToolSchemas.choice("대상 유형", ContentValues.ENTITY_TYPES.sorted()),
        "entityId" to ToolSchemas.integer("대상 번호", 1),
        "provider" to ToolSchemas.choice("제공처", ContentValues.PROVIDERS.sorted()),
        "externalId" to ToolSchemas.string("연결할 외부 ID", 40),
        ToolSchemas.reason(),
        required = listOf("entityType", "entityId", "provider", "externalId"),
    ),
) {
    override fun precheck(call: ToolCall) {
        request(call)
    }

    override fun execute(call: ToolCall): ToolOutput {
        val request = request(call)
        clients.contentMeta().externalId(clients.worker(call.user), request)
        return ToolOutput(
            data = ToolResponses.pick(mapOf("entityType" to request.entityType, "entityId" to request.entityId, "provider" to request.provider, "externalId" to request.externalId), "entityType", "entityId", "provider", "externalId"),
            message = "${request.entityType} ${request.entityId}번에 ${request.provider} ${request.externalId} 를 연결했습니다.",
        )
    }

    private fun request(call: ToolCall): ExternalIdRequest {
        val args = call.arguments
        return ExternalIdRequest(
            operation = "C",
            entityType = args.requireChoice("entityType", ContentValues.ENTITY_TYPES),
            entityId = args.requireLong("entityId"),
            provider = args.requireChoice("provider", ContentValues.PROVIDERS),
            externalId = args.requireText("externalId", 40),
        )
    }
}

@Component
class ContentUpdateLocalizationTool(private val clients: DownstreamClients) : BaseTool(
    "content_update_localization",
    "현지화 고치기",
    "작품의 언어별 제목과 줄거리를 등록 C, 수정 U, 삭제 D 한다. 사람이 직접 넣은 MANUAL 값은 content-meta 가 지킨다.",
    ToolSchemas.obj(
        "operation" to ToolSchemas.choice("작업", ContentValues.OPERATIONS.sorted()),
        "titleId" to ToolSchemas.integer("작품 번호", 1),
        "langCode" to ToolSchemas.string("언어 코드. 예 ko, en", 10),
        "localTitle" to ToolSchemas.string("그 언어의 제목", 500),
        "overview" to ToolSchemas.string("그 언어의 줄거리", ContentValues.MAX_TEXT),
        required = listOf("operation", "titleId", "langCode"),
    ),
) {
    override fun execute(call: ToolCall): ToolOutput {
        val args = call.arguments
        val request = LocalizationRequest(
            operation = args.requireChoice("operation", ContentValues.OPERATIONS),
            titleId = args.requireLong("titleId"),
            langCode = args.requireText("langCode", 10).lowercase(),
            localTitle = args.string("localTitle"),
            overview = args.string("overview"),
        )
        clients.contentMeta().localization(clients.worker(call.user), request)
        return ToolOutput(
            data = mapOf("operation" to request.operation, "titleId" to request.titleId, "langCode" to request.langCode),
            message = "작품 ${request.titleId}번 ${request.langCode} 현지화를 반영했습니다.",
        )
    }
}

@Component
class ContentApplyTmdbTool(private val clients: DownstreamClients) : BaseTool(
    "content_apply_tmdb",
    "TMDB 반영",
    "연결된 TMDB 정보로 포스터, IMDb ID, 영어 현지화만 채운다. 원제, 개봉일, 상영 시간, 상태는 바꾸지 않는다.",
    ToolSchemas.obj("titleId" to ToolSchemas.integer("작품 번호", 1), required = listOf("titleId")),
) {
    override fun execute(call: ToolCall): ToolOutput {
        val titleId = call.arguments.requireLong("titleId")
        val result = clients.contentMeta().applyTmdb(clients.worker(call.user), titleId)
        return ToolOutput(data = result, message = "작품 ${titleId}번에 TMDB 정보를 반영했습니다.")
    }
}

@Component
class ContentGetQualityReportTool(private val clients: DownstreamClients) : BaseTool(
    "content_get_quality_report",
    "품질 점검 결과",
    "content-meta 의 가장 최근 품질 점검 결과를 읽는다.",
    ToolSchemas.obj(),
) {
    override fun execute(call: ToolCall): ToolOutput =
        ToolOutput(data = clients.contentMeta().qualityLatest(clients.worker(call.user)), message = "최근 품질 점검 결과를 읽었습니다.")
}

@Component
class ContentGetMigrationStatusTool(private val clients: DownstreamClients) : BaseTool(
    "content_get_migration_status",
    "이관 상태",
    "읽기 원천, 가장 최근 대조 결과, 최근 이관 실행 다섯 건을 읽는다.",
    ToolSchemas.obj(),
) {
    override fun execute(call: ToolCall): ToolOutput {
        val api = clients.contentMeta()
        val worker = clients.worker(call.user)
        return ToolOutput(
            data = mapOf(
                "readSource" to api.readSource(worker),
                "latestVerify" to api.verifyLatest(worker),
                "recentRuns" to api.migrationRuns(worker, RECENT_RUNS),
            ),
            message = "이관 상태를 읽었습니다.",
        )
    }

    companion object {
        private const val RECENT_RUNS = 5
    }
}
