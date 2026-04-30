---
name: search-jcl
description: Recherche un fichier JCL par son nom et retourne les programmes qu'il exécute, les datasets qu'il référence et le nom du job. Utiliser quand l'utilisateur demande des informations sur un job JCL, les programmes lancés par un batch, ou les datasets utilisés dans un job.
compatibility: Requires smojol-rest-api running. Set COBOL_REST_URL (default http://localhost:8080).
---

# Search JCL File

## Instructions

1. Determine `COBOL_REST_URL` from environment (default: `http://localhost:8080`)
2. Make a GET request: `GET {COBOL_REST_URL}/api/jcls/{jcl_name}`
3. If HTTP 404: report not found, suggest using `list-jcls` to browse
4. If success: format the response

## Response Format

- **JCL** : `name`
- **Job Name** : `jobName`
- **Programmes exécutés** (`programs.length`) : list each program
- **Datasets référencés** (`datasets.length`) : list each dataset
- **Plans** (`plans.length`) : list each plan (from `plans` array)

## Examples

**User**: "Que fait le JCL DAILYBATCH ?"
**Action**: `GET http://localhost:8080/api/jcls/DAILYBATCH`

**User**: "Quels programmes sont lancés par MONTHJOB ?"
**Action**: fetch JCL → present programs list
