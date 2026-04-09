---
name: compare-programs
description: Compare deux programmes COBOL selon leurs dépendances (copybooks et callees partagés), leur complexité respective et calcule un score de similarité. Utiliser quand l'utilisateur veut comparer deux programmes, trouver ce qu'ils ont en commun, identifier des opportunités de mutualisation, ou évaluer si deux programmes font des choses similaires.
compatibility: Requires smojol-rest-api running. Set COBOL_REST_URL (default http://localhost:8080).
---

# Compare Two COBOL Programs

## Instructions

1. Determine `COBOL_REST_URL` from environment (default: `http://localhost:8080`)
2. Fetch both programs in parallel:
   - `GET {COBOL_REST_URL}/api/programs/{program1}`
   - `GET {COBOL_REST_URL}/api/programs/{program2}`
3. If either is not found, report the error
4. Compute:
   - `common_copybooks` = intersection of both `copybooks` arrays
   - `common_callees` = intersection of both `callees` arrays
   - `total_unique` = union size of copybooks + union size of callees
   - `similarity_score` = `(common_copybooks + common_callees) / total_unique * 100` (0 if total_unique = 0)
   - `complexity1` = `copybooks1.length * 2 + callees1.length * 3 + callers1.length`
   - `complexity2` = same formula for program2

## Response Format

> ## Comparaison : `{program1}` vs `{program2}`
>
> **Score de similarité** : **{score}%**
>
> | Métrique           | {program1} | {program2} |
> |--------------------|------------|------------|
> | Copybooks          | N          | N          |
> | Appels sortants    | N          | N          |
> | Appelé par         | N          | N          |
> | Score complexité   | N          | N          |
>
> **En commun** :
> - Copybooks partagés ({N}) : ...
> - Callees partagés ({N}) : ...
>
> **Uniquement dans {program1}** : ...
> **Uniquement dans {program2}** : ...

## Examples

**User**: "Compare CBIMPORT et CBEXPORT"
**User**: "PROG1 et PROG2 font-ils des choses similaires ?"
