package com.sleekydz86.aibackoffice.framework.web.core.list

import com.sleekydz86.aibackoffice.common.Headers
import org.springframework.http.ResponseEntity

data class ListQuery(
    val start: Int = 0,
    val end: Int = DEFAULT_SIZE,
    val sort: String? = null,
    val order: String? = null,
) {
    val offset: Int get() = start.coerceAtLeast(0)
    val limit: Int get() = (end - offset).coerceIn(1, MAX_SIZE)
    val ascending: Boolean get() = order.equals("asc", ignoreCase = true)

    companion object {
        const val DEFAULT_SIZE = 20
        const val MAX_SIZE = 100
    }
}

object ListResponses {
    fun <T> of(items: List<T>, total: Long): ResponseEntity<List<T>> =
        ResponseEntity.ok()
            .header(Headers.TOTAL_COUNT, total.toString())
            .header("Access-Control-Expose-Headers", "${Headers.TOTAL_COUNT}, ${Headers.TRACE_ID}")
            .body(items)
}
