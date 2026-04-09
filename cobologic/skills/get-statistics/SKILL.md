---
name: get-statistics
description: Retourne les statistiques globales du système COBOL analysé : totaux (programmes, copybooks, JCL, datasets), top 10 des programmes les plus complexes, top 10 des copybooks les plus utilisés, et répartition par niveau de complexité. Utiliser pour avoir une vue d'ensemble de la base de code ou en début de session.
compatibility: Requires smojol-rest-api running. Requires Python 3.10+ with httpx installed. Set COBOL_REST_URL (default http://localhost:8080).
---

# Get Global Statistics

## Instructions

1. Determine `COBOL_REST_URL` from environment (default: `http://localhost:8080`)
2. Run the bundled script for efficient aggregation:

```bash
python scripts/get_statistics.py --api-url {COBOL_REST_URL}
```

3. Parse JSON output and present the statistics

## Response Format

> ## 📊 Statistiques Cobologic
>
> ### Totaux
> | Type        | Nombre |
> |-------------|--------|
> | Programmes  | N      |
> | Copybooks   | N      |
> | JCL         | N      |
> | Datasets    | N      |
>
> ### 🏆 Top 10 — Programmes les plus complexes
> (table with name, score, copybooks, calls)
>
> ### 📚 Top 10 — Copybooks les plus utilisés
> (table with name, used_by_count)
>
> ### Répartition par complexité
> - 🔴 Haute (score > 30) : N programmes
> - 🟡 Moyenne (score 15–30) : N programmes  
> - 🟢 Faible (score ≤ 15) : N programmes

## Examples

**User**: "Donne-moi les stats du projet"
**User**: "Combien de programmes y a-t-il ?"
**User**: "Quels sont les programmes les plus complexes ?"
