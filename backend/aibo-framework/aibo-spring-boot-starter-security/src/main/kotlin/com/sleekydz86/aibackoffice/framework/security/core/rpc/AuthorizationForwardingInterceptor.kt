package com.sleekydz86.aibackoffice.framework.security.core.rpc

import com.sleekydz86.aibackoffice.common.Headers
import com.sleekydz86.aibackoffice.framework.security.core.SecurityFrameworkUtils
import com.sleekydz86.aibackoffice.framework.web.core.trace.TraceIds
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

class AuthorizationForwardingInterceptor : ClientHttpRequestInterceptor {

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
        val headers = request.headers
        if (!headers.containsHeader(HttpHeaders.AUTHORIZATION)) {
            SecurityFrameworkUtils.loginUserOrNull()?.let { headers.setBearerAuth(it.rawToken) }
        }
        if (!headers.containsHeader(Headers.TRACE_ID)) {
            TraceIds.current()?.let { headers.set(Headers.TRACE_ID, it) }
        }
        return execution.execute(request, body)
    }
}
