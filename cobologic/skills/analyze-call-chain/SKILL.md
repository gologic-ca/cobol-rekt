---
name: analyze-call-chain
description: Analyse récursivement la chaîne d'appels d'un programme COBOL jusqu'à une profondeur donnée et retourne l'arbre complet des appels. Gère les dépendances circulaires et les programmes externes. Utiliser quand l'utilisateur veut tracer l'arbre d'appels, comprendre l'impact transitif d'un programme, ou visualiser la hiérarchie d'appels.
compatibility: Requires smojol-rest-api running. Requires Python 3.10+ with httpx installed. Set COBOL_REST_URL (default http://localhost:8080).
---

# Analyze Call Chain

## Instructions

1. Determine `COBOL_REST_URL` from environment (default: `http://localhost:8080`)
2. Run the bundled script to perform the recursive analysis:

```bash
python scripts/analyze_call_chain.py --program {program_name} --max-depth {max_depth} --api-url {COBOL_REST_URL}
```

Default `max_depth` is **5** unless the user specifies otherwise.

3. Parse the JSON output and present the call tree

## Response Format

Present as an indented tree:

```
📦 MAINPROG (depth 0) — appelle 3 programmes
  └─ 📦 SUBPROG1 (depth 1) — appelle 1 programme
      └─ 📦 SUBPROG2 (depth 2) — aucun appel
  └─ 📦 SUBPROG3 (depth 1) — aucun appel
  └─ 🌐 EXTPROG (externe — non trouvé dans le catalogue)
```

Then summarize:
- **Total programmes dans la chaîne** : N
- **Profondeur max analysée** : N
- **Programmes externes** (non dans le catalogue) : list them

## Examples

**User**: "Trace l'arbre d'appels de CBIMPORT"
**Action**: run script with `--program CBIMPORT --max-depth 5`

**User**: "Qui MAINPROG appelle-t-il sur 3 niveaux ?"
**Action**: run script with `--program MAINPROG --max-depth 3`

## Edge Cases

- **Circular dependency** : détecté automatiquement, marqué `[CIRCULAR]`
- **External program** : programme non trouvé dans le catalogue, marqué `[EXTERNAL]`
- Si la chaîne est très profonde, suggérer de réduire `max_depth`
