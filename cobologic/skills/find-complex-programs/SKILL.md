---
name: find-complex-programs
description: Trouve les N programmes COBOL les plus complexes selon différentes métriques de complexité (score global, copybooks, appels). Retourne un classement avec scores. Utiliser pour prioriser la documentation, identifier les cibles de refactoring, ou évaluer le risque d'une migration.
compatibility: Requires smojol-rest-api running. Set COBOL_REST_URL (default http://localhost:8080).
---

# Find Most Complex Programs

## Instructions

1. Determine `COBOL_REST_URL` from environment (default: `http://localhost:8080`)
2. Make a GET request: `GET {COBOL_REST_URL}/api/programs`
3. Determine the metric from user request:
   - `all` (default): `score = copybooks * 2 + callees * 3 + callers * 1`
   - `copybooks`: `score = copybooks.length`
   - `calls`: `score = callees.length + callers.length`
   - `dependencies`: `score = copybooks.length + callees.length`
4. Sort by score descending, return top N (default: 10)

## Response Format

> ## 🏆 Top {N} — Programmes les plus complexes
> Métrique : **{metric}**
>
> | # | Programme | Score | Copybooks | Appelle | Appelé par |
> |---|-----------|-------|-----------|---------|------------|
> | 1 | PROG1     | 87    | 12        | 8       | 3          |
>
> 💡 **Recommandation** : Ces programmes sont prioritaires pour la documentation approfondie et le refactoring.

## Examples

**User**: "Quels sont les 10 programmes les plus complexes ?"
**Action**: metric = "all", top_n = 10

**User**: "Quels programmes ont le plus de copybooks ?"
**Action**: metric = "copybooks", top_n = 10

**User**: "Top 5 des programmes avec le plus d'appels"
**Action**: metric = "calls", top_n = 5
