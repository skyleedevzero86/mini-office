package com.sleekydz86.contentmeta.tmdb.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** TMDB v3 응답 중 화면에 쓰는 필드만 받는다 (나머지는 무시) */
public final class TmdbModels {

    private TmdbModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Genre(int id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cast(long id, String name, String character, @JsonProperty("profile_path") String profilePath, int order) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Crew(long id, String name, String job, String department) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Credits(List<Cast> cast, List<Crew> crew) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExternalIds(@JsonProperty("imdb_id") String imdbId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Named(long id, String name) {
    }

    /** GET /movie/{id}?append_to_response=credits */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Movie(
            long id,
            String title,
            @JsonProperty("original_title") String originalTitle,
            String overview,
            String tagline,
            @JsonProperty("poster_path") String posterPath,
            @JsonProperty("backdrop_path") String backdropPath,
            @JsonProperty("release_date") String releaseDate,
            Integer runtime,
            @JsonProperty("vote_average") Double voteAverage,
            @JsonProperty("vote_count") Integer voteCount,
            List<Genre> genres,
            @JsonProperty("imdb_id") String imdbId,
            String homepage,
            String status,
            Credits credits
    ) {
    }

    /** GET /tv/{id}?append_to_response=credits,external_ids */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tv(
            long id,
            String name,
            @JsonProperty("original_name") String originalName,
            String overview,
            String tagline,
            @JsonProperty("poster_path") String posterPath,
            @JsonProperty("backdrop_path") String backdropPath,
            @JsonProperty("first_air_date") String firstAirDate,
            @JsonProperty("episode_run_time") List<Integer> episodeRunTime,
            @JsonProperty("number_of_seasons") Integer numberOfSeasons,
            @JsonProperty("number_of_episodes") Integer numberOfEpisodes,
            @JsonProperty("vote_average") Double voteAverage,
            @JsonProperty("vote_count") Integer voteCount,
            List<Genre> genres,
            String homepage,
            String status,
            @JsonProperty("created_by") List<Named> createdBy,
            List<Named> networks,
            @JsonProperty("external_ids") ExternalIds externalIds,
            Credits credits
    ) {
    }

    /** GET /search/movie, /search/tv 결과 한 건 (영화는 title·release_date, TV는 name·first_air_date) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchItem(
            long id,
            String title,
            String name,
            @JsonProperty("original_title") String originalTitle,
            @JsonProperty("original_name") String originalName,
            @JsonProperty("release_date") String releaseDate,
            @JsonProperty("first_air_date") String firstAirDate,
            @JsonProperty("poster_path") String posterPath,
            String overview,
            @JsonProperty("vote_average") Double voteAverage
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchPage(int page, List<SearchItem> results, @JsonProperty("total_results") int totalResults) {
    }
}
