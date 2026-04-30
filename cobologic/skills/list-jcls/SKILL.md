---
name: list-jcls
description: Liste tous les fichiers JCL disponibles dans le catalogue avec le nombre de programmes et datasets associés. Utiliser quand l'utilisateur veut voir les jobs batch disponibles, filtrer par nom, ou avoir une vue d'ensemble des traitements batch.
compatibility: Requires smojol-rest-api running. Set COBOL_REST_URL (default http://localhost:8080).
---

# List JCL Files

## Instructions

1. Determine `COBOL_REST_URL` from environment (default: `http://localhost:8080`)
2. Make a GET request: `GET {COBOL_REST_URL}/api/jcls`
3. Apply optional name filter (case-insensitive partial match)
4. Apply limit (default 50)

## Response Format

| JCL    | Job Name | Programmes | Datasets | Plans |
|--------|----------|------------|----------|-------|
| NAME   | JOBNAME  | N          | N        | list  |

## Examples

**User**: "Liste tous les JCL"
**Action**: `GET http://localhost:8080/api/jcls` → present table

**User**: "Quels JCL contiennent DAILY dans leur nom ?"
**Action**: fetch all → filter where name contains "DAILY"
