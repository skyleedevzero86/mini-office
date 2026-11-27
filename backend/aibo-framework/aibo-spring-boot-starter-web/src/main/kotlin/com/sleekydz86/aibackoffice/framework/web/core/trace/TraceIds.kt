package com.sleekydz86.aibackoffice.framework.web.core.trace

import java.util.UUID
import org.slf4j.MDC

object TraceIds {

    const val MDC_KEY = "traceId"
    private val SAFE = Regex("[A-Za-z0-9-]{8,64}")

    fun current(): String? = MDC.get(MDC_KEY)

    fun sanitize(given: String?): String = given?.takeIf { SAFE.matches(it) } ?: newId()

    fun newId(): String = UUID.randomUUID().toString().replace("-", "")

    fun <T> runWith(traceId: String?, action: () -> T): T {
        val previous = MDC.get(MDC_KEY)
        MDC.put(MDC_KEY, sanitize(traceId))
        try {
            return action()
        } finally {
            if (previous == null) MDC.remove(MDC_KEY) else MDC.put(MDC_KEY, previous)
        }
    }
}
