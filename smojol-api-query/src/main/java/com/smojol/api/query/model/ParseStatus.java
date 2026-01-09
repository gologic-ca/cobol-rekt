package com.smojol.api.query.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Statut de parsing d'un fichier AST
 */
public enum ParseStatus {
    @JsonProperty("success")
    SUCCESS("File parsed successfully"),

    @JsonProperty("error")
    ERROR("Error parsing file"),

    @JsonProperty("partial")
    PARTIAL("File partially parsed"),

    @JsonProperty("not_found")
    NOT_FOUND("File not found"),

    @JsonProperty("unknown")
    UNKNOWN("Unknown status");

    private final String description;

    ParseStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Vérifie si le statut indique un parsing réussi
     */
    public boolean isSuccessful() {
        return this == SUCCESS || this == PARTIAL;
    }

    /**
     * Vérifie si le statut indique une erreur
     */
    public boolean isFailed() {
        return this == ERROR || this == NOT_FOUND || this == UNKNOWN;
    }
}
