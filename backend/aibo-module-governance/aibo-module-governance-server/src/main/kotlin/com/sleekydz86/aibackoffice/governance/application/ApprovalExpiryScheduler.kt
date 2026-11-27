package com.sleekydz86.aibackoffice.governance.application

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ApprovalExpiryScheduler(private val approvals: ApprovalService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${aibo.governance.expire-delay:60s}")
    fun expire() {
        try {
            approvals.expireDue()
        } catch (exception: RuntimeException) {
            log.warn("승인 요청 만료 처리에 실패했습니다. 다음 주기에 다시 합니다. 이유={}", exception.message)
        }
    }
}
