package com.sleekydz86.aibackoffice.common

import java.time.Clock
import java.time.ZoneId

object Zones {
    val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    val SEOUL_CLOCK: Clock = Clock.system(SEOUL)
}
