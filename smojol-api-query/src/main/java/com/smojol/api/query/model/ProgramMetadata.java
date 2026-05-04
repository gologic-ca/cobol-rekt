package com.smojol.api.query.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Métadonnées légères d'un programme COBOL.
 * Stocké dans l'index en mémoire (Tier 1) — ne contient PAS l'AST complet.
 * Permet de répondre aux requêtes de listing, dépendances et impact
 * sans charger les fichiers AST volumineux.
 * 
 * Taille typique : ~1-5 KB par programme (vs 1-50 MB pour un AST complet).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramMetadata {
    private String name;
    private String path;
    private String programId;
    private long size;
    private int lines;
    private List<String> copybooks;
    private List<String> datasets;
    private List<String> callees;
    private List<String> callers;
    private List<String> jcls;
    private List<String> plans;
    private ParseStatus parseStatus;

    /**
     * Convertit vers un CBLFile complet (sans astData).
     * Utilisé pour les réponses API qui n'ont pas besoin de l'AST.
     */
    public CBLFile toCBLFile() {
        return CBLFile.builder()
                .name(name)
                .path(path)
                .programId(programId)
                .size(size)
                .lines(lines)
                .copybooks(copybooks)
                .datasets(datasets)
                .callees(callees)
                .callers(callers)
                .jcls(jcls)
                .plans(plans)
                .parseStatus(parseStatus)
                .build();
    }

    /**
     * Crée un ProgramMetadata depuis un CBLFile (extraction des métadonnées).
     */
    public static ProgramMetadata fromCBLFile(CBLFile cbl) {
        return ProgramMetadata.builder()
                .name(cbl.getName())
                .path(cbl.getPath())
                .programId(cbl.getProgramId())
                .size(cbl.getSize())
                .lines(cbl.getLines())
                .copybooks(cbl.getCopybooks())
                .datasets(cbl.getDatasets())
                .callees(cbl.getCallees())
                .callers(cbl.getCallers())
                .jcls(cbl.getJcls())
                .plans(cbl.getPlans())
                .parseStatus(cbl.getParseStatus())
                .build();
    }

    public boolean usesCopybook(String copybookName) {
        return copybooks != null && copybooks.contains(copybookName);
    }

    public boolean usesDataset(String datasetName) {
        return datasets != null && datasets.contains(datasetName);
    }

    public int getCopybookCount() {
        return copybooks != null ? copybooks.size() : 0;
    }

    public int getCalleeCount() {
        return callees != null ? callees.size() : 0;
    }

    public int getCallerCount() {
        return callers != null ? callers.size() : 0;
    }
}
