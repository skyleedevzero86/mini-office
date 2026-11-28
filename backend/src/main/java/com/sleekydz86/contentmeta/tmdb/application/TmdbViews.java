package com.sleekydz86.contentmeta.tmdb.application;

import java.util.List;

public final class TmdbViews {

    private TmdbViews() {
    }

    public record Status(boolean configured, String language) {
    }

    public record Person(long tmdbPersonId, String name, String character, String profileUrl) {
    }

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

    public record ApplyResult(long titleId, String note, String posterPath, String imdbId) {
    }
}
