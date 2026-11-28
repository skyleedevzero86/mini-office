package com.sleekydz86.contentmeta.catalog.presentation;

import com.sleekydz86.contentmeta.catalog.application.CatalogEditService;
import com.sleekydz86.contentmeta.catalog.application.ExternalIdCommand;
import com.sleekydz86.contentmeta.catalog.application.LocalizationCommand;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/catalog")
public class CatalogAdminController {

    private final CatalogEditService catalogEditService;

    public CatalogAdminController(CatalogEditService catalogEditService) {
        this.catalogEditService = catalogEditService;
    }

    @PostMapping("/external-ids")
    public ResponseEntity<Void> externalId(@RequestBody ExternalIdCommand command) {
        catalogEditService.externalId(command);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/localizations")
    public ResponseEntity<Void> localization(@RequestBody LocalizationCommand command) {
        catalogEditService.localization(command);
        return ResponseEntity.noContent().build();
    }
}
