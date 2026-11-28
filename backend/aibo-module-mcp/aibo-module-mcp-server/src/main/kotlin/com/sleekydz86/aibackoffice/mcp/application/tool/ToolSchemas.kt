package com.sleekydz86.aibackoffice.mcp.application.tool

object ToolSchemas {

    fun obj(vararg properties: Pair<String, Map<String, Any?>>, required: List<String> = emptyList()): Map<String, Any?> =
        buildMap {
            put("type", "object")
            put("properties", properties.toMap())
            if (required.isNotEmpty()) put("required", required)
            put("additionalProperties", false)
        }

    fun string(description: String, maxLength: Int? = null): Map<String, Any?> = buildMap {
        put("type", "string")
        put("description", description)
        maxLength?.let { put("maxLength", it) }
    }

    fun choice(description: String, values: Collection<String>): Map<String, Any?> =
        mapOf("type" to "string", "description" to description, "enum" to values.toList())

    fun integer(description: String, minimum: Long? = null, maximum: Long? = null): Map<String, Any?> = buildMap {
        put("type", "integer")
        put("description", description)
        minimum?.let { put("minimum", it) }
        maximum?.let { put("maximum", it) }
    }

    fun boolean(description: String): Map<String, Any?> = mapOf("type" to "boolean", "description" to description)

    fun dateTime(description: String): Map<String, Any?> =
        mapOf("type" to "string", "format" to "date-time", "description" to "$description 예 2026-12-01T21:00:00+09:00")

    fun limit(): Pair<String, Map<String, Any?>> =
        "limit" to integer("돌려받을 최대 건수. 20건까지", 1, ToolList.MAX_ITEMS.toLong())

    fun reason(): Pair<String, Map<String, Any?>> =
        "reason" to string("승인자에게 보일 요청 이유", 2000)
}
