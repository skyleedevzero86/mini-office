package com.sleekydz86.aibackoffice.agent

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AgentServerApplication

fun main(args: Array<String>) {
    runApplication<AgentServerApplication>(*args)
}
