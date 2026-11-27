package com.sleekydz86.contentmeta.catalog.application;

import com.sleekydz86.contentmeta.catalog.infrastructure.CreditRow;
import com.sleekydz86.contentmeta.catalog.infrastructure.ExternalIdRow;
import com.sleekydz86.contentmeta.catalog.infrastructure.LocalizationRow;
import com.sleekydz86.contentmeta.catalog.infrastructure.RegionRow;
import com.sleekydz86.contentmeta.catalog.infrastructure.SeasonRow;
import com.sleekydz86.contentmeta.catalog.infrastructure.TitleRow;
import java.util.List;

/** 작품 상세: 작품 + 외부 표준 ID + 언어별 제목 + 지역별 공개 + 시즌 + 출연 */
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
