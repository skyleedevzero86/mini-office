package com.sleekydz86.aibackoffice.framework.web.config

import com.sleekydz86.aibackoffice.framework.web.core.handler.DataAccessExceptionHandler
import com.sleekydz86.aibackoffice.framework.web.core.handler.GlobalExceptionHandler
import com.sleekydz86.aibackoffice.framework.web.core.list.ListQueryArgumentResolver
import com.sleekydz86.aibackoffice.framework.web.core.trace.TraceIdFilter
import io.micrometer.tracing.Tracer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class AiboWebAutoConfiguration : WebMvcConfigurer {

    @Bean
    fun globalExceptionHandler(): GlobalExceptionHandler = GlobalExceptionHandler()

    @Bean
    fun traceIdFilter(tracer: ObjectProvider<Tracer>): FilterRegistrationBean<TraceIdFilter> =
        FilterRegistrationBean(TraceIdFilter(tracer)).apply { order = Ordered.HIGHEST_PRECEDENCE + 10 }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(ListQueryArgumentResolver())
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.dao.OptimisticLockingFailureException"])
    class DataAccessConfiguration {
        @Bean
        fun dataAccessExceptionHandler(): DataAccessExceptionHandler = DataAccessExceptionHandler()
    }
}
