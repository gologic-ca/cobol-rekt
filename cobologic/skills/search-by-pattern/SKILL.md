---
name: search-by-pattern
description: Recherche par pattern avec wildcard (*) dans tous les types d'entités COBOL (programmes, copybooks, JCL, datasets) ou dans un type spécifique. Supporte la recherche insensible à la casse. Utiliser quand l'utilisateur cherche des entités dont il connaît une partie du nom, ou veut lister toutes les entités correspondant à un pattern.
compatibility: Requires smojol-rest-api running. Set COBOL_REST_URL (default http://localhost:8080).
---

# Search by Pattern (Wildcard)

## Instructions

1. Determine `COBOL_REST_URL` from environment (default: `http://localhost:8080`)
2. Convert the wildcard pattern to a regex: replace `*` with `.*`
3. Determine which entity types to search (default: all)
4. For each type to search:
   - Fetch all: `GET {COBOL_REST_URL}/api/{programs|copybooks|jcls|datasets}`
   - Filter names matching the regex (case-insensitive by default)
5. Aggregate and present results

## Pattern Examples

| Pattern     | Matches                          |
|-------------|----------------------------------|
| `CB*`       | All names starting with CB       |
| `*VALID*`   | All names containing VALID       |
| `*IMPORT`   | All names ending with IMPORT     |
| `CB*EXPORT` | Names starting with CB and ending with EXPORT |

## Response Format

> ## 🔍 Résultats pour le pattern `{pattern}`
>
> **{total}** résultat(s) trouvé(s)
>
> **Programmes** ({N}) : PROG1, PROG2, ...
> **Copybooks** ({N}) : CPY1, CPY2, ...
> **JCL** ({N}) : JCL1, ...
> **Datasets** ({N}) : DS1, ...

## Examples

**User**: "Trouve tout ce qui commence par CB"
**Action**: pattern = "CB*", entity_type = "all"

**User**: "Quels programmes contiennent IMPORT ?"
**Action**: pattern = "*IMPORT*", entity_type = "program"

**User**: "Cherche les datasets avec CUST"
**Action**: pattern = "*CUST*", entity_type = "dataset"
