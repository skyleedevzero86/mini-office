package com.sleekydz86.aibackoffice.framework.web.core.trace

import com.sleekydz86.aibackoffice.common.Headers
import io.micrometer.tracing.Tracer
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.filter.OncePerRequestFilter

class TraceIdFilter(private val tracer: ObjectProvider<Tracer>) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val traceId = request.getHeader(Headers.TRACE_ID)?.let(TraceIds::sanitize)
            ?: tracer.ifAvailable?.currentSpan()?.context()?.traceId()
            ?: TraceIds.newId()
        MDC.put(TraceIds.MDC_KEY, traceId)
        response.setHeader(Headers.TRACE_ID, traceId)
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove(TraceIds.MDC_KEY)
        }
    }
}
