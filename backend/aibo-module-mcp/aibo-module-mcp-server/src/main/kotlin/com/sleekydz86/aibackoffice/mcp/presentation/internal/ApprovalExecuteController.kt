package com.sleekydz86.aibackoffice.mcp.presentation.internal

import com.sleekydz86.aibackoffice.framework.security.core.SecurityFrameworkUtils
import com.sleekydz86.aibackoffice.governance.api.ApprovalExecuteRequest
import com.sleekydz86.aibackoffice.mcp.application.ApprovedExecutionService
import com.sleekydz86.aibackoffice.mcp.application.ToolResult
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/approvals")
class ApprovalExecuteController(private val executions: ApprovedExecutionService) {

    @PostMapping("/{approvalId}/execute")
    fun execute(@PathVariable approvalId: Long, @RequestBody request: ApprovalExecuteRequest): ToolResult =
        executions.execute(SecurityFrameworkUtils.loginUser(), approvalId, request)
}
