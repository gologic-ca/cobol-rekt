---
name: search-text
description: "Recherche full-text dans le code source de tous les programmes COBOL. Scanne les ASTs sur disque sans les charger en mémoire (streaming). Permet de trouver des variables, paragraphes, instructions EXEC SQL, CALL, PERFORM, ou n'importe quel texte dans le code source. Utiliser quand l'utilisateur cherche une chaîne de texte dans le code COBOL, veut trouver où une variable est utilisée, ou identifier les programmes contenant un pattern spécifique."
compatibility: "Requires smojol-rest-api running. Set COBOL_REST_URL (default http://localhost:8080)."
---

# Search Text in COBOL ASTs

## Instructions

1. Determine `COBOL_REST_URL` from environment (default: `http://localhost:8080`)
2. Make a GET request: `GET {COBOL_REST_URL}/api/search?q={query}&caseSensitive={true|false}&limit={N}`
   - `q` (required): the text to search for (URL-encoded)
   - `caseSensitive` (optional, default: false): whether the search is case-sensitive
   - `limit` (optional, default: 50, max: 200): maximum number of programs to return
3. If no results: report nothing was found, suggest trying a different query or checking the spelling
4. If success: format and present the response

## Response Format

Present the result as:

> ## 🔍 Résultats pour « {query} »
>
> **{totalPrograms}** programme(s) trouvé(s) — **{totalMatches}** occurrence(s) au total
>
> ### {programName} ({matchCount} occurrences)
> ```cobol
> {context snippet with line numbers}
> ```
>
> _(repeat for each program, max 10 shown)_

## Query Parameters

| Parameter     | Default | Description                                      |
|---------------|---------|--------------------------------------------------|
| `q`           | —       | Texte à rechercher (obligatoire)                 |
| `caseSensitive` | false | true = sensible à la casse                       |
| `limit`       | 50      | Nombre max de programmes à retourner (max: 200)  |

## Examples

**User**: "Trouve tous les programmes qui utilisent EXEC SQL"
**Action**: `GET http://localhost:8080/api/search?q=EXEC%20SQL`

**User**: "Quels programmes référencent CUST-RECORD ?"
**Action**: `GET http://localhost:8080/api/search?q=CUST-RECORD`

**User**: "Cherche PERFORM VARYING dans le code"
**Action**: `GET http://localhost:8080/api/search?q=PERFORM%20VARYING`

**User**: "Trouve les programmes avec CALL 'CBIMPORT'"
**Action**: `GET http://localhost:8080/api/search?q=CALL%20'CBIMPORT'`

## Edge Cases

- If the query is very short (1-2 chars), warn the user that results may be numerous
- The search scans files in parallel — response time depends on the number of AST files
- Each result includes up to 10 snippets with 2 lines of context before/after
- Results are sorted by number of matches (most matches first)
