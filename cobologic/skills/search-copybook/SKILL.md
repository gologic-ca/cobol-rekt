---
name: search-copybook
description: Recherche un copybook COBOL par son nom et retourne tous les programmes qui l'utilisent, son niveau d'impact (low/medium/high) et son nombre de lignes. Utiliser quand l'utilisateur veut savoir ce qu'est un copybook, qui l'utilise, ou son niveau d'impact sur la base de code.
compatibility: Requires smojol-rest-api running. Set COBOL_REST_URL (default http://localhost:8080).
---

# Search COBOL Copybook

## Instructions

1. Determine `COBOL_REST_URL` from environment (default: `http://localhost:8080`)
2. Make a GET request: `GET {COBOL_REST_URL}/api/copybooks/{copybook_name}`
3. If HTTP 404: report not found, suggest using `list-copybooks` to browse
4. If success: extract `usedBy` array and format

## Response Format

- **Copybook** : `name`
- **Lignes** : `lines`
- **Utilisé par** (`usedBy.length` programmes) : list each program
- **Niveau d'impact** :
  - `usedBy.length > 5` → 🔴 **critical** — toute modification impacte de nombreux programmes
  - `usedBy.length > 2` → 🟡 **medium**
  - else → 🟢 **low**

## Examples

**User**: "Qui utilise le copybook CVACT01Y ?"
**Action**: `GET http://localhost:8080/api/copybooks/CVACT01Y`

**User**: "Quel est l'impact de modifier CUSTDATA ?"
**Action**: fetch copybook data → show impact level and list of affected programs
