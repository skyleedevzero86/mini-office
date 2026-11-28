package com.sleekydz86.contentmeta.catalog.application;

import com.sleekydz86.contentmeta.catalog.infrastructure.CreditRow;
import com.sleekydz86.contentmeta.catalog.infrastructure.ExternalIdRow;
import com.sleekydz86.contentmeta.catalog.infrastructure.LocalizationRow;
import com.sleekydz86.contentmeta.catalog.infrastructure.RegionRow;
import com.sleekydz86.contentmeta.catalog.infrastructure.SeasonRow;
import com.sleekydz86.contentmeta.catalog.infrastructure.TitleRow;
import java.util.List;

public record TitleDetail(
        TitleRow title,
        List<ExternalIdRow> externalIds,
        List<LocalizationRow> localizations,
        List<RegionRow> regions,
        List<String> genres,
        List<String> countries,
        List<SeasonRow> seasons,
        List<CreditRow> credits
) {
}
