---
name: search-program
description: Recherche un programme COBOL par son nom et retourne ses détails complets : copybooks utilisés, programmes appelants (callers), programmes appelés (callees) et JCL associés. Utiliser quand l'utilisateur demande des informations sur un programme COBOL spécifique, veut savoir qui appelle un programme, ou quels copybooks il utilise.
compatibility: Requires smojol-rest-api running. Set COBOL_REST_URL (default http://localhost:8080).
---

# Search COBOL Program

## Instructions

1. Determine `COBOL_REST_URL` from environment (default: `http://localhost:8080`)
2. Make a GET request: `GET {COBOL_REST_URL}/api/programs/{name}`
   - The `name` is case-sensitive; try uppercase if not found
3. If HTTP 404: report the program was not found, suggest using the `list-programs` skill to browse
4. If success: format and present the response

## Response Format

Present the result as:

- **Programme** : `name`
- **Fichier** : `path` (`lines` lignes)
- **Copybooks** (`copybooks_count`) : list each on a new line
- **Appelé par** (`callers_count`) : list of callers
- **Appelle** (`callees_count`) : list of callees
- **JCL** : list of JCL files

Compute `complexity_score = copybooks.length * 2 + callees.length * 3 + callers.length` and show it.

## Examples

**User**: "Montre-moi le programme CBIMPORT"
**Action**: `GET http://localhost:8080/api/programs/CBIMPORT`

**User**: "Quels programmes CBEXPORT appelle-t-il ?"
**Action**: `GET http://localhost:8080/api/programs/CBEXPORT` → present `callees`

## Edge Cases

- If name contains extension (e.g. `CBIMPORT.cbl`), strip it and retry with the base name
- If the program exists but has no callers, mention it may be an orphan program
