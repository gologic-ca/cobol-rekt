package com.smojol.api.query.util;

import com.smojol.api.query.model.Copybook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;

/**
 * Détecteur de cycles dans les dépendances de copybooks
 * Utilise DFS (Depth-First Search) pour détecter les cycles
 */
public class CycleDetector {
    private static final Logger logger = LoggerFactory.getLogger(CycleDetector.class);

    /**
     * Détecte s'il y a un cycle à partir d'un copybook donné
     *
     * @param startCopybook nom du copybook de départ
     * @param copybooks Map de tous les copybooks disponibles
     * @return true si un cycle est détecté, false sinon
     */
    public static boolean hasCycle(String startCopybook, Map<String, Copybook> copybooks) {
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        return dfsHasCycle(startCopybook, copybooks, visited, visiting);
    }

    /**
     * Récupère tous les copybooks inclus sans cycles
     *
     * @param startCopybook nom du copybook de départ
     * @param copybooks Map de tous les copybooks disponibles
     * @return List des copybooks inclus (sans cycles)
     */
    public static List<Copybook> getIncludesWithoutCycles(
            String startCopybook, Map<String, Copybook> copybooks) {
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        List<Copybook> result = new ArrayList<>();
        
        dfsCollect(startCopybook, copybooks, visited, visiting, result);
        
        return result;
    }

    /**
     * Récupère le chemin d'un cycle s'il existe
     *
     * @param startCopybook nom du copybook de départ
     * @param copybooks Map de tous les copybooks disponibles
     * @return Optional contenant le chemin du cycle si trouvé
     */
    public static Optional<List<String>> findCyclePath(
            String startCopybook, Map<String, Copybook> copybooks) {
        Set<String> visited = new HashSet<>();
        List<String> path = new ArrayList<>();
        List<String> cyclePath = dfsFindCyclePath(startCopybook, copybooks, visited, path);
        
        if (cyclePath != null && !cyclePath.isEmpty()) {
            logger.warn("Cycle detected: {}", cyclePath);
            return Optional.of(cyclePath);
        }
        
        return Optional.empty();
    }

    /**
     * Valide la structure complète des copybooks (détecte tous les cycles)
     *
     * @param allCopybooks Map de tous les copybooks
     * @return List des cycles trouvés (chaque cycle est une List de copybooks)
     */
    public static List<List<String>> validateCopybookStructure(
            Map<String, Copybook> allCopybooks) {
        List<List<String>> cycles = new ArrayList<>();
        Set<String> globalVisited = new HashSet<>();
        Set<String> inStack = new HashSet<>();
        
        for (String copybookName : allCopybooks.keySet()) {
            if (!globalVisited.contains(copybookName)) {
                List<String> path = new ArrayList<>();
                dfsValidate(copybookName, allCopybooks, globalVisited, inStack, path, cycles);
            }
        }
        
        if (!cycles.isEmpty()) {
            logger.error("Found {} cycles in copybook structure", cycles.size());
        }
        
        return cycles;
    }

    /**
     * DFS pour validation complète
     */
    private static void dfsValidate(
            String current, Map<String, Copybook> allCopybooks,
            Set<String> visited, Set<String> inStack, List<String> path,
            List<List<String>> cycles) {
        
        if (visited.contains(current)) {
            return;
        }
        
        if (inStack.contains(current)) {
            // Trouvé un cycle!
            int idx = path.indexOf(current);
            if (idx != -1) {
                List<String> cycle = new ArrayList<>(path.subList(idx, path.size()));
                cycle.add(current);
                cycles.add(cycle);
                logger.debug("Cycle detected: {}", cycle);
            }
            return;
        }
        
        inStack.add(current);
        path.add(current);
        
        Copybook copybook = allCopybooks.get(current);
        if (copybook != null && copybook.getIncludes() != null) {
            for (String include : copybook.getIncludes()) {
                dfsValidate(include, allCopybooks, visited, inStack, path, cycles);
            }
        }
        
        path.remove(path.size() - 1);
        inStack.remove(current);
        visited.add(current);
    }

    // ==================== Méthodes Privées DFS ====================

    /**
     * DFS pour détecter les cycles
     */
    private static boolean dfsHasCycle(
            String current, Map<String, Copybook> copybooks,
            Set<String> visited, Set<String> visiting) {
        
        if (visiting.contains(current)) {
            logger.debug("Cycle detected involving: {}", current);
            return true;  // Cycle trouvé!
        }
        
        if (visited.contains(current)) {
            return false;  // Déjà visité
        }
        
        visiting.add(current);
        
        Copybook copybook = copybooks.get(current);
        if (copybook != null && copybook.getIncludes() != null) {
            for (String include : copybook.getIncludes()) {
                if (dfsHasCycle(include, copybooks, visited, visiting)) {
                    return true;
                }
            }
        }
        
        visiting.remove(current);
        visited.add(current);
        
        return false;
    }

    /**
     * DFS pour collecter les dépendances sans cycles
     */
    private static void dfsCollect(
            String current, Map<String, Copybook> copybooks,
            Set<String> visited, Set<String> visiting,
            List<Copybook> result) {
        
        if (visited.contains(current)) {
            return;  // Déjà traité
        }
        
        if (visiting.contains(current)) {
            logger.warn("Cycle detected at: {}, stopping recursion", current);
            return;  // Cycle détecté, arrêter
        }
        
        visiting.add(current);
        
        Copybook copybook = copybooks.get(current);
        if (copybook != null) {
            // Récurser sur les includes d'abord
            if (copybook.getIncludes() != null) {
                for (String include : copybook.getIncludes()) {
                    dfsCollect(include, copybooks, visited, visiting, result);
                }
            }
            // Ajouter le copybook courant
            result.add(copybook);
        }
        
        visiting.remove(current);
        visited.add(current);
    }

    /**
     * DFS pour trouver le chemin d'un cycle
     */
    private static List<String> dfsFindCyclePath(
            String current, Map<String, Copybook> copybooks,
            Set<String> visited, List<String> path) {
        
        // Si le nœud est déjà dans le chemin actuel, on a trouvé un cycle
        int idx = path.indexOf(current);
        if (idx != -1) {
            logger.debug("Cycle path found: {}", path.subList(idx, path.size()));
            List<String> cyclePath = new ArrayList<>(path.subList(idx, path.size()));
            cyclePath.add(current);  // Fermer le cycle
            return cyclePath;
        }
        
        if (visited.contains(current)) {
            return null;  // Déjà complètement traité
        }
        
        path.add(current);
        
        Copybook copybook = copybooks.get(current);
        if (copybook != null && copybook.getIncludes() != null) {
            for (String include : copybook.getIncludes()) {
                List<String> cyclePath = dfsFindCyclePath(include, copybooks, visited, 
                    new ArrayList<>(path));
                if (cyclePath != null && !cyclePath.isEmpty()) {
                    return cyclePath;
                }
            }
        }
        
        visited.add(current);
        return null;
    }

    /**
     * Utilitaire pour afficher un cycle de manière lisible
     */
    public static String formatCyclePath(List<String> cyclePath) {
        if (cyclePath == null || cyclePath.isEmpty()) {
            return "No cycle";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cyclePath.size(); i++) {
            sb.append(cyclePath.get(i));
            if (i < cyclePath.size() - 1) {
                sb.append(" → ");
            }
        }
        sb.append(" → ").append(cyclePath.get(0));  // Fermer le cycle
        return sb.toString();
    }

    /**
     * Classe pour capturer les informations du cycle
     */
    public static class CycleInfo {
        public final String startNode;
        public final List<String> path;
        public final int depth;

        public CycleInfo(String startNode, List<String> path) {
            this.startNode = startNode;
            this.path = new ArrayList<>(path);
            this.depth = path.size();
        }

        @Override
        public String toString() {
            return String.format("Cycle[start=%s, depth=%d, path=%s]",
                    startNode, depth, formatCyclePath(path));
        }
    }
}
