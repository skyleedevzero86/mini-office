package com.sleekydz86.contentmeta.catalog.application;

public record ExternalIdCommand(String operation, String entityType, long entityId, String provider, String externalId) {
}
