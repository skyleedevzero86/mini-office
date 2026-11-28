package com.sleekydz86.contentmeta.catalog.application;

import com.sleekydz86.contentmeta.catalog.infrastructure.CatalogMapper;
import com.sleekydz86.contentmeta.catalog.infrastructure.GenreRow;
import com.sleekydz86.contentmeta.catalog.infrastructure.TitleRow;
import com.sleekydz86.contentmeta.catalog.infrastructure.TitleSearchCondition;
import com.sleekydz86.contentmeta.catalog.infrastructure.TitleSummaryRow;
import com.sleekydz86.contentmeta.global.error.BusinessException;
import com.sleekydz86.contentmeta.global.error.ErrorCode;
import com.sleekydz86.contentmeta.global.web.PageResponse;
import com.sleekydz86.contentmeta.migration.application.ReadSourceSwitch;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class CatalogQueryService {

    private final CatalogMapper mapper;
    private final ReadSourceSwitch readSourceSwitch;

    public CatalogQueryService(CatalogMapper mapper, ReadSourceSwitch readSourceSwitch) {
        this.mapper = mapper;
        this.readSourceSwitch = readSourceSwitch;
    }

    public List<TitleSummaryRow> search(String query, String lang, String titleType, int limit) {
        readSourceSwitch.requireNew();
        return mapper.searchTitles(trimToNull(query), lang, upperOrNull(titleType), Math.max(1, Math.min(100, limit)));
    }

    public PageResponse<TitleListItem> page(String query, String lang, String titleType, String genre, String status,
                                            Boolean animation, TitleSort sort, int page, int size) {
        readSourceSwitch.requireNew();
        if (page < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "page는 0 이상이어야 합니다: " + page);
        }
        int pageSize = Math.max(1, Math.min(100, size));
        TitleSearchCondition condition = new TitleSearchCondition(trimToNull(query), lang, upperOrNull(titleType),
                upperOrNull(genre), upperOrNull(status), animation, (sort == null ? TitleSort.RELEASE_DESC : sort).name(),
                page * pageSize, pageSize);
        long total = mapper.countTitles(condition);
        List<TitleListItem> items = total == 0 ? List.of() : mapper.pageTitles(condition).stream().map(TitleListItem::from).toList();
        return PageResponse.of(items, page, pageSize, total);
    }

    public List<GenreRow> genres() {
        return mapper.findGenreCodes();
    }

    public TitleDetail detail(long titleId) {
        readSourceSwitch.requireNew();
        TitleRow title = mapper.findTitle(titleId);
        if (title == null) {
            throw new BusinessException(ErrorCode.TITLE_NOT_FOUND, "title_id=" + titleId);
        }
        return new TitleDetail(
                title,
                mapper.findExternalIds("TITLE", titleId),
                mapper.findLocalizations(titleId),
                mapper.findRegions(titleId),
                mapper.findGenres(titleId),
                mapper.findCountries(titleId),
                mapper.findSeasons(titleId),
                mapper.findCredits(titleId));
    }

    public TitleDetail byExternalId(String provider, String externalId) {
        readSourceSwitch.requireNew();
        Long titleId = mapper.findTitleIdByExternalId(provider.toUpperCase(Locale.ROOT), externalId.trim());
        if (titleId == null) {
            throw new BusinessException(ErrorCode.TITLE_NOT_FOUND, provider + " " + externalId);
        }
        return detail(titleId);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String upperOrNull(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
