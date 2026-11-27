package com.sleekydz86.aibackoffice.operation.presentation

import com.sleekydz86.aibackoffice.framework.security.core.SecurityFrameworkUtils
import com.sleekydz86.aibackoffice.operation.application.AttachmentService
import com.sleekydz86.aibackoffice.operation.application.AttachmentUpload
import com.sleekydz86.aibackoffice.operation.application.AttachmentView
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/operation/issues/{issueId}/attachments")
class AttachmentController(private val attachmentService: AttachmentService) {

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize("@ss.hasPermission('issue:write')")
    fun upload(@PathVariable issueId: Long, @RequestPart("file") file: MultipartFile): AttachmentView =
        attachmentService.upload(
            SecurityFrameworkUtils.loginUser(),
            issueId,
            AttachmentUpload(file.originalFilename, file.contentType, file.size) { file.inputStream },
        )

    @GetMapping
    @PreAuthorize("@ss.hasPermission('issue:read')")
    fun attachments(@PathVariable issueId: Long): List<AttachmentView> =
        attachmentService.attachments(SecurityFrameworkUtils.loginUser(), issueId)
}
