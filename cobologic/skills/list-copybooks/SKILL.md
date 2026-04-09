---
name: list-copybooks
description: Liste tous les copybooks disponibles dans le catalogue avec leur nombre d'utilisateurs et leur niveau d'impact. Utiliser quand l'utilisateur veut explorer les copybooks disponibles, filtrer par nom partiel, ou obtenir une vue d'ensemble des copybooks les plus utilisés.
compatibility: Requires smojol-rest-api running. Set COBOL_REST_URL (default http://localhost:8080).
---

# List COBOL Copybooks

## Instructions

1. Determine `COBOL_REST_URL` from environment (default: `http://localhost:8080`)
2. Make a GET request: `GET {COBOL_REST_URL}/api/copybooks`
3. Apply optional name filter (case-insensitive partial match)
4. Sort by `usedBy.length` descending to show most-used first
5. Apply limit (default 50)

## Response Format

| Copybook | Utilisé par | Impact  |
|----------|-------------|---------|
| NAME     | N prog.     | 🟢/🟡/🔴 |

- `usedBy > 5` → 🔴 high impact
- `usedBy > 2` → 🟡 medium
- else → 🟢 low

## Examples

**User**: "Liste les copybooks"
**Action**: `GET http://localhost:8080/api/copybooks` → show all sorted by usage

**User**: "Quels copybooks commencent par CV ?"
**Action**: fetch all → filter where name starts with "CV"
