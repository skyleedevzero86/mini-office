package com.sleekydz86.contentmeta.catalog.infrastructure;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CatalogMapper {

    TitleRow findTitle(@Param("titleId") long titleId);

    List<TitleSummaryRow> searchTitles(
            @Param("query") String query,
            @Param("lang") String lang,
            @Param("titleType") String titleType,
            @Param("limit") int limit);

    List<TitleListRow> pageTitles(TitleSearchCondition condition);

    long countTitles(TitleSearchCondition condition);

    List<GenreRow> findGenreCodes();

    List<LocalizationRow> findLocalizations(@Param("titleId") long titleId);

    List<ExternalIdRow> findExternalIds(@Param("entityType") String entityType, @Param("entityId") long entityId);

    Long findTitleIdByExternalId(@Param("provider") String provider, @Param("externalId") String externalId);

    List<RegionRow> findRegions(@Param("titleId") long titleId);

    List<String> findGenres(@Param("titleId") long titleId);

    List<String> findCountries(@Param("titleId") long titleId);

    List<SeasonRow> findSeasons(@Param("titleId") long titleId);

    List<CreditRow> findCredits(@Param("titleId") long titleId);

    void externalIdCud(ExternalIdCudCall call);

    void localizationCud(LocalizationCudCall call);
}
