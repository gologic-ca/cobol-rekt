---
name: analyze-dependencies
description: Analyse le graphe complet de dépendances d'un programme COBOL : copybooks inclus, programmes appelés (callees), programmes appelants (callers) et JCL associés. Calcule un score de complexité. Utiliser quand l'utilisateur veut une vue complète des dépendances d'un programme ou son score de complexité.
compatibility: Requires smojol-rest-api running. Set COBOL_REST_URL (default http://localhost:8080).
---

# Analyze Program Dependencies

## Instructions

1. Determine `COBOL_REST_URL` from environment (default: `http://localhost:8080`)
2. Make a GET request: `GET {COBOL_REST_URL}/api/programs/{program_name}`
3. If not found: report error and suggest `list-programs`
4. Compute: `complexity_score = copybooks.length * 2 + callees.length * 3 + callers.length * 1`

## Complexity Score Interpretation

| Score  | Niveau   |
|--------|----------|
| > 50   | 🔴 Très élevé — candidat prioritaire à la documentation |
| 20–50  | 🟠 Élevé |
| 10–20  | 🟡 Moyen |
| < 10   | 🟢 Faible |

## Response Format

> **Programme** `{name}` — Score de complexité : **{score}** ({level})
>
> - 📚 **Copybooks** ({N}) : ...
> - ➡️ **Appelle** ({N}) : ...
> - ⬅️ **Appelé par** ({N}) : ...
> - 📋 **JCL** ({N}) : ...

## Examples

**User**: "Analyse les dépendances de CBIMPORT"
**Action**: `GET http://localhost:8080/api/programs/CBIMPORT` → compute score and format

**User**: "Quelle est la complexité de MAINPROG ?"
**Action**: fetch program → compute and explain complexity score
