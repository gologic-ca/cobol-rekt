package com.smojol.api.query.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Résultat d'une recherche full-text dans les ASTs COBOL.
 * Chaque SearchResult correspond à un programme où le terme a été trouvé.
 */
@Getter
@Setter
@Builder
public class SearchResult {
    private String programName;
    private int matchCount;
    private List<MatchSnippet> matches;

    /**
     * Un snippet de texte autour d'une occurrence trouvée.
     */
    @Getter
    @Setter
    @Builder
    public static class MatchSnippet {
        private int lineNumber;
        private String line;
        private String context;
    }
}
