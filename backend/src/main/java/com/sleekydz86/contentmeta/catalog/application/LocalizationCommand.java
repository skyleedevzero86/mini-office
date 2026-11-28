package com.sleekydz86.contentmeta.catalog.application;

public record LocalizationCommand(String operation, long titleId, String langCode, String localTitle, String overview) {
}
