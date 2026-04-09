---
name: search-dataset
description: Recherche un dataset par son nom et retourne les programmes COBOL et JCL qui l'utilisent, ainsi que son organisation. Utiliser quand l'utilisateur veut savoir quels programmes accèdent à un fichier de données, ou l'impact d'un changement de format de dataset.
compatibility: Requires smojol-rest-api running. Set COBOL_REST_URL (default http://localhost:8080).
---

# Search Dataset

## Instructions

1. Determine `COBOL_REST_URL` from environment (default: `http://localhost:8080`)
2. Make a GET request: `GET {COBOL_REST_URL}/api/datasets/{dataset_name}`
3. If HTTP 404: report not found, suggest using `list-datasets` to browse
4. If success: format the response

## Response Format

- **Dataset** : `name`
- **Organisation** : `organization`
- **Programmes COBOL** (`usedByCobol.length`) : list each program
- **JCL** (`usedByJcls.length`) : list each JCL
- **Total références** : `usedByCobol.length + usedByJcls.length`
- **Niveau d'impact** :
  - total > 15 → 🔴 **critical**
  - total > 8 → 🟠 **high**
  - total > 2 → 🟡 **medium**
  - else → 🟢 **low**

## Examples

**User**: "Qui accède au dataset CUSTOUT ?"
**Action**: `GET http://localhost:8080/api/datasets/CUSTOUT`
