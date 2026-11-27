package com.sleekydz86.contentmeta.catalog.application;

import com.sleekydz86.contentmeta.catalog.infrastructure.TitleListRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/** 목록 한 줄. 쉼표 문자열(제공처·장르)을 배열로 풀어 내보낸다 */
public record TitleListItem(
        long titleId,
        String titleType,
        String displayTitle,
        String originalTitle,
        LocalDate releaseDate,
        String status,
        boolean animation,
        BigDecimal voteAverage,
        String posterPath,
        int seasonCount,
        List<String> providers,
        List<String> genres
) {

    public static TitleListItem from(TitleListRow row) {
        return new TitleListItem(row.getTitleId(), row.getTitleType(), row.getDisplayTitle(), row.getOriginalTitle(),
                row.getReleaseDate(), row.getStatus(), row.isAnimation(), row.getVoteAverage(), row.getPosterPath(),
                row.getSeasonCount(), splitComma(row.getProviders()), splitComma(row.getGenres()));
    }

    private static List<String> splitComma(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(part -> !part.isEmpty()).toList();
    }
}
