package com.sleekydz86.aibackoffice.framework.web.core.list

import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

class ListQueryArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == ListQuery::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): ListQuery = ListQuery(
        start = webRequest.getParameter("_start")?.toIntOrNull() ?: 0,
        end = webRequest.getParameter("_end")?.toIntOrNull() ?: ListQuery.DEFAULT_SIZE,
        sort = webRequest.getParameter("_sort")?.takeIf { it.isNotBlank() },
        order = webRequest.getParameter("_order")?.takeIf { it.isNotBlank() },
    )
}
