---
name: find-programs-using-dataset
description: Trouve tous les programmes COBOL qui accèdent à un dataset spécifique. Utiliser quand l'utilisateur veut identifier les programmes touchés par un changement de structure ou de format de dataset, ou faire une analyse d'impact sur un fichier de données.
compatibility: Requires smojol-rest-api running. Set COBOL_REST_URL (default http://localhost:8080).
---

# Find Programs Using Dataset

## Instructions

1. Determine `COBOL_REST_URL` from environment (default: `http://localhost:8080`)
2. Make a GET request: `GET {COBOL_REST_URL}/api/datasets/{dataset_name}`
3. Extract `usedByCobol` array (COBOL programs) and `usedByJcls` (JCL references)
4. Classify impact:
   - total > 10 → 🔴 **critical**
   - total > 5 → 🟠 **high**
   - total > 2 → 🟡 **medium**
   - else → 🟢 **low**

## Response Format

> **Dataset** `{name}` est accédé par **{N}** programme(s) COBOL et référencé dans **{M}** JCL

List programs and JCLs separately.

## Examples

**User**: "Quels programmes utilisent CUSTOUT ?"
**Action**: `GET http://localhost:8080/api/datasets/CUSTOUT` → present `usedByCobol`
