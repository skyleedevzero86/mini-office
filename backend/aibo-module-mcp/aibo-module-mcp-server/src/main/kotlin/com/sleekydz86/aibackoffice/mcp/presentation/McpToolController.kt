package com.sleekydz86.aibackoffice.mcp.presentation

import com.sleekydz86.aibackoffice.framework.security.core.SecurityFrameworkUtils
import com.sleekydz86.aibackoffice.mcp.application.ToolVisibilityService
import com.sleekydz86.aibackoffice.mcp.application.VisibleTool
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/mcp")
class McpToolController(private val visibility: ToolVisibilityService) {

    @GetMapping("/tools/visible")
    fun visibleTools(): List<VisibleTool> = visibility.visible(SecurityFrameworkUtils.loginUser())
}
