package com.sleekydz86.contentmeta.catalog.presentation;

import com.sleekydz86.contentmeta.catalog.application.CatalogQueryService;
import com.sleekydz86.contentmeta.catalog.application.TitleDetail;
import com.sleekydz86.contentmeta.catalog.application.TitleListItem;
import com.sleekydz86.contentmeta.catalog.application.TitleSort;
import com.sleekydz86.contentmeta.catalog.infrastructure.GenreRow;
import com.sleekydz86.contentmeta.catalog.infrastructure.TitleSummaryRow;
import com.sleekydz86.contentmeta.global.web.PageResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 신규 콘텐츠 메타 조회 API (읽기 원천이 NEW일 때만) */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogQueryService catalogQueryService;

    public CatalogController(CatalogQueryService catalogQueryService) {
        this.catalogQueryService = catalogQueryService;
    }

    @GetMapping("/titles")
    public List<TitleSummaryRow> search(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "ko") String lang,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "20") int limit) {
        return catalogQueryService.search(query, lang, type, limit);
    }

    /** 목록 화면용 페이지 조회 (page는 0부터) */
    @GetMapping("/titles/page")
    public PageResponse<TitleListItem> page(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "ko") String lang,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean animation,
            @RequestParam(defaultValue = "RELEASE_DESC") TitleSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return catalogQueryService.page(query, lang, type, genre, status, animation, sort, page, size);
    }

    @GetMapping("/genres")
    public List<GenreRow> genres() {
        return catalogQueryService.genres();
    }

    @GetMapping("/titles/{titleId}")
    public TitleDetail detail(@PathVariable long titleId) {
        return catalogQueryService.detail(titleId);
    }

    @GetMapping("/by-external/{provider}/{externalId}")
    public TitleDetail byExternalId(@PathVariable String provider, @PathVariable String externalId) {
        return catalogQueryService.byExternalId(provider, externalId);
    }
}
