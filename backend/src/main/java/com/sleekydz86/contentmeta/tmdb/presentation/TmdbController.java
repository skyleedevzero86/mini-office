package com.sleekydz86.contentmeta.tmdb.presentation;

import com.sleekydz86.contentmeta.tmdb.application.TmdbService;
import com.sleekydz86.contentmeta.tmdb.application.TmdbViews;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TmdbController {

    private final TmdbService tmdbService;

    public TmdbController(TmdbService tmdbService) {
        this.tmdbService = tmdbService;
    }

    @GetMapping("/api/catalog/tmdb/status")
    public TmdbViews.Status status() {
        return tmdbService.status();
    }

    @GetMapping("/api/catalog/titles/{titleId}/tmdb")
    public TmdbViews.Title title(@PathVariable long titleId) {
        return tmdbService.titleView(titleId);
    }

    @GetMapping("/api/admin/tmdb/search")
    public List<TmdbViews.Candidate> search(
            @RequestParam(defaultValue = "MOVIE") String type,
            @RequestParam String query,
            @RequestParam(required = false) Integer year) {
        return tmdbService.search(type, query, year);
    }

    @PostMapping("/api/admin/tmdb/titles/{titleId}/apply")
    public TmdbViews.ApplyResult apply(@PathVariable long titleId) {
        return tmdbService.apply(titleId);
    }
}
