---
name: analyze-impact
description: Analyse l'impact d'une modification sur un programme, copybook, dataset ou JCL. Détecte automatiquement le type d'entité et retourne tous les éléments affectés avec un niveau de risque (low/medium/high/critical). Utiliser quand l'utilisateur veut évaluer le risque avant de modifier quelque chose ou planifier un changement.
compatibility: Requires smojol-rest-api running. Set COBOL_REST_URL (default http://localhost:8080).
---

# Analyze Modification Impact

## Instructions

1. Determine `COBOL_REST_URL` from environment (default: `http://localhost:8080`)
2. If entity type is unknown, auto-detect by trying in order:
   - `GET {COBOL_REST_URL}/api/programs/{name}` → type = program
   - `GET {COBOL_REST_URL}/api/copybooks/{name}` → type = copybook
   - `GET {COBOL_REST_URL}/api/datasets/{name}` → type = dataset
   - `GET {COBOL_REST_URL}/api/jcls/{name}` → type = jcl
   - If none found → report not found
3. Analyze based on type:

### If copybook:
- `affected_programs` = `usedBy` array
- `risk_level`: `length > 10` → critical, `> 5` → high, `> 2` → medium, else low
- Recommendation: "Recompiler {N} programmes, tests de régression requis"

### If program:
- `affected_programs` = `callers` array  
- `affected_jcls` = `jcls` array
- `affected_plans` = `plans` array (plans bound to this program)
- `risk_level`: `callers + jcls > 5` → high, `> 2` → medium, else low

### If dataset:
- `affected_programs` = `usedByCobol`
- `affected_jcls` = `usedByJcls`
- total = sum of both; `> 15` → critical, `> 8` → high, `> 2` → medium

### If jcl:
- Show programs and datasets it references

## Response Format

> ## Analyse d'impact : `{name}` ({type})
>
> **Niveau de risque** : 🔴 CRITICAL / 🟠 HIGH / 🟡 MEDIUM / 🟢 LOW
>
> **Programmes affectés** ({N}) : ...
> **JCL affectés** ({N}) : ...
> **Plans affectés** ({N}) : ... (if type is program, show bound plans)
>
> **Recommandations** :
> - ...

## Examples

**User**: "Quel est l'impact si je modifie CVACT01Y ?"
**User**: "Que se passe-t-il si je change le dataset CUSTOUT ?"
**User**: "Analyse l'impact de CBIMPORT" (auto-detect → program)
