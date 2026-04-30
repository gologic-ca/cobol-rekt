---
name: list-programs
description: Liste tous les programmes COBOL disponibles dans le catalogue, avec filtrage optionnel par nom partiel et une limite de résultats. Utiliser quand l'utilisateur veut explorer la liste des programmes, chercher des programmes dont le nom contient une chaîne donnée, ou obtenir un aperçu général.
compatibility: Requires smojol-rest-api running. Set COBOL_REST_URL (default http://localhost:8080).
---

# List COBOL Programs

## Instructions

1. Determine `COBOL_REST_URL` from environment (default: `http://localhost:8080`)
2. Make a GET request: `GET {COBOL_REST_URL}/api/programs`
3. If a filter was requested, keep only entries where `name.toUpperCase().includes(filter.toUpperCase())`
4. Apply a limit (default 50) and sort alphabetically by name
5. Present the results

## Response Format

Show as a table or list:

| Programme | Copybooks | Appelé par | Appelle | JCL  | Plans |
|-----------|-----------|------------|---------|------|-------|
| NAME      | N         | ✓/✗        | ✓/✗     | list | list  |

Include `total_found` count at the top. If a program has `plans`, show them inline.

## Examples

**User**: "Liste tous les programmes"
**Action**: `GET http://localhost:8080/api/programs` → show all (up to 50)

**User**: "Quels programmes commencent par CB ?"
**Action**: fetch all → filter where name starts with "CB"

**User**: "Y a-t-il des programmes avec VALID dans le nom ?"
**Action**: fetch all → filter where name contains "VALID"
