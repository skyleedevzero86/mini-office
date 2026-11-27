package com.sleekydz86.contentmeta.tmdb.application;

import java.util.List;

/** TMDB 화면용 응답 */
public final class TmdbViews {

    private TmdbViews() {
    }

    public record Status(boolean configured, String language) {
    }

    public record Person(long tmdbPersonId, String name, String character, String profileUrl) {
    }

    /**
     * 작품 상세 화면의 TMDB 패널.
     * configured=false면 키가 없는 것, linked=false면 작품에 TMDB ID가 없는 것 (둘 다 200으로 돌려주고 화면이 안내한다)
     */
    public record Title(
            boolean configured,
            boolean linked,
            String provider,
            Long tmdbId,
            String title,
            String originalTitle,
            String overview,
            String tagline,
            String posterPath,
            String posterUrl,
            String backdropUrl,
            String releaseDate,
            Integer runtime,
            Double voteAverage,
            Integer voteCount,
            List<String> genres,
            List<String> directors,
            List<Person> cast,
            String imdbId,
            String homepage,
            String tmdbUrl,
            String status,
            Integer seasons,
            Integer episodes,
            List<String> networks
    ) {

        public static Title notConfigured() {
            return new Title(false, false, null, null, null, null, null, null, null, null, null, null, null, null, null,
                    List.of(), List.of(), List.of(), null, null, null, null, null, null, List.of());
        }

        public static Title notLinked() {
            return new Title(true, false, null, null, null, null, null, null, null, null, null, null, null, null, null,
                    List.of(), List.of(), List.of(), null, null, null, null, null, null, List.of());
        }
    }

    /** TMDB 검색 결과 한 건. 연결 후보로 보여 준다 */
    public record Candidate(
            String provider,
            long tmdbId,
            String title,
            String originalTitle,
            String date,
            String posterUrl,
            String overview,
            Double voteAverage,
            Long linkedTitleId
    ) {
    }

    /** 반영 결과 (프로시저가 남긴 내역) */
    public record ApplyResult(long titleId, String note, String posterPath, String imdbId) {
    }
}
