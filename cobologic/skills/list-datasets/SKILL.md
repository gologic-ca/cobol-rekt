---
name: list-datasets
description: Liste tous les datasets disponibles dans le catalogue avec leur organisation et le nombre de références (programmes et JCL). Utiliser quand l'utilisateur veut explorer les fichiers de données du système, filtrer par nom, ou identifier les datasets les plus utilisés.
compatibility: Requires smojol-rest-api running. Set COBOL_REST_URL (default http://localhost:8080).
---

# List Datasets

## Instructions

1. Determine `COBOL_REST_URL` from environment (default: `http://localhost:8080`)
2. Make a GET request: `GET {COBOL_REST_URL}/api/datasets`
3. Apply optional name filter (case-insensitive partial match)
4. Sort by total references (`usedByCobol.length + usedByJcls.length`) descending
5. Apply limit (default 50)

## Response Format

| Dataset | Organisation | Programmes | JCL |
|---------|--------------|------------|-----|
| NAME    | ORG          | N          | N   |

## Examples

**User**: "Liste les datasets"
**Action**: `GET http://localhost:8080/api/datasets` → present table

**User**: "Quels datasets commencent par CUST ?"
**Action**: fetch all → filter where name starts with "CUST"
