package com.sleekydz86.aibackoffice.operation.infrastructure.config

import com.sleekydz86.aibackoffice.common.Zones
import java.time.Clock
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class OperationConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun clock(): Clock = Zones.SEOUL_CLOCK
}
