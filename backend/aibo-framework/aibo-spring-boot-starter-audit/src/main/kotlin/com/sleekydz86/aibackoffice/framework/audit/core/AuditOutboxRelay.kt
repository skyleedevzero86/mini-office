package com.sleekydz86.aibackoffice.framework.audit.core

import com.sleekydz86.aibackoffice.framework.audit.config.AiboAuditProperties
import java.time.Duration
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

open class AuditOutboxRelay(
    private val store: AuditOutboxStore,
    private val sink: AuditSink,
    private val properties: AiboAuditProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${aibo.audit.relay-delay:3s}")
    open fun relay() {
        if (!properties.relayEnabled) return
        val rows = store.claim(properties.batchSize, properties.lease)
        if (rows.isEmpty()) return
        try {
            sink.send(rows.map { it.payload })
            store.markSent(rows.map { it.id })
        } catch (exception: Exception) {
            log.warn("감사 기록 {}건을 보내지 못했습니다. 나중에 다시 보냅니다. 이유={}", rows.size, exception.message)
            val now = LocalDateTime.now()
            rows.forEach { row ->
                val giveUp = row.retryCount + 1 >= properties.maxRetries
                if (giveUp) log.error("감사 기록을 {}번 보내지 못해 멈췄습니다. 번호={}", properties.maxRetries, row.id)
                store.markRetry(row, now.plus(backoff(row.retryCount + 1)), giveUp)
            }
        }
    }

    companion object {
        private val MAX_BACKOFF: Duration = Duration.ofMinutes(10)

        fun backoff(attempt: Int): Duration {
            val seconds = 1L shl attempt.coerceIn(0, 20)
            return Duration.ofSeconds(seconds).coerceAtMost(MAX_BACKOFF)
        }
    }
}
